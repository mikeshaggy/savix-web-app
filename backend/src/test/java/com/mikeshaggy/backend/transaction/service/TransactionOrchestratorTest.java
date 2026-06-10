package com.mikeshaggy.backend.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentOccurrenceService;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.dto.TransactionCreateRequest;
import com.mikeshaggy.backend.transaction.dto.TransactionResponse;
import com.mikeshaggy.backend.transaction.dto.TransactionUpdateRequest;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionOrchestratorTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private FixedPaymentOccurrenceService fixedPaymentOccurrenceService;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private TransactionOrchestrator orchestrator;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);

    private Wallet wallet;
    private Category expenseCategory;
    private User user;
    private Transaction savedTransaction;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).email("test@test.com").username("test").build();
        wallet =
                Wallet.builder().id(1).name("Main").balance(new BigDecimal("1000.00")).user(user).build();
        expenseCategory =
                Category.builder().id(1).name("Food").type(CategoryType.EXPENSE).user(user).build();

        savedTransaction =
                Transaction.builder()
                        .id(100L)
                        .wallet(wallet)
                        .category(expenseCategory)
                        .title("Test")
                        .amount(new BigDecimal("50.00"))
                        .transactionDate(DATE)
                        .importance(Importance.ESSENTIAL)
                        .build();
    }

    private FixedPaymentOccurrence linkedOccurrence(Transaction transaction) {
        FixedPayment fixedPayment = FixedPayment.builder().id(7).wallet(wallet).category(expenseCategory).build();
        FixedPaymentOccurrence occurrence =
                FixedPaymentOccurrence.builder()
                        .id(10L)
                        .fixedPayment(fixedPayment)
                        .dueDate(DATE)
                        .expectedAmount(new BigDecimal("1500.00"))
                        .status(OccurrenceStatus.PAID)
                        .transaction(transaction)
                        .build();
        transaction.setFixedPaymentOccurrence(occurrence);
        return occurrence;
    }

    private TransactionUpdateRequest updateRequest(int walletId, int categoryId, BigDecimal amount) {
        return new TransactionUpdateRequest(
                walletId, categoryId, "Rent", amount, DATE, null, Importance.ESSENTIAL);
    }

    @Test
    void withOccurrenceId_delegatesToBothServices() {
        // given
        TransactionCreateRequest request =
                new TransactionCreateRequest(
                        1, 1, "Rent", new BigDecimal("1500"), DATE, null, Importance.ESSENTIAL, 10L);

        when(transactionService.createTransactionEntity(request, USER_ID)).thenReturn(savedTransaction);

        // when
        TransactionResponse response = orchestrator.createTransaction(request, USER_ID);

        // then
        assertThat(response.id()).isEqualTo(100L);
        verify(transactionService).createTransactionEntity(request, USER_ID);
        verify(fixedPaymentOccurrenceService).markOccurrenceAsPaid(10L, savedTransaction, USER_ID);
    }

    @Test
    void withoutOccurrenceId_doesNotCallFixedPaymentOccurrenceService() {
        // given
        TransactionCreateRequest request =
                new TransactionCreateRequest(
                        1, 1, "Groceries", new BigDecimal("50.00"), DATE, null, Importance.ESSENTIAL, null);

        when(transactionService.createTransactionEntity(request, USER_ID)).thenReturn(savedTransaction);

        // when
        TransactionResponse response = orchestrator.createTransaction(request, USER_ID);

        // then
        assertThat(response.id()).isEqualTo(100L);
        verify(transactionService).createTransactionEntity(request, USER_ID);
        verifyNoInteractions(fixedPaymentOccurrenceService);
    }

    @Test
    void withCrossUserOccurrenceId_propagatesNotFoundSoTransactionCanRollback() {
        // given
        TransactionCreateRequest request =
                new TransactionCreateRequest(
                        1, 1, "Rent", new BigDecimal("1500"), DATE, null, Importance.ESSENTIAL, 10L);

        when(transactionService.createTransactionEntity(request, USER_ID)).thenReturn(savedTransaction);
        doThrow(new EntityNotFoundException("Occurrence not found with id: 10"))
                .when(fixedPaymentOccurrenceService)
                .markOccurrenceAsPaid(eq(10L), any(), eq(USER_ID));

        // when
        // then
        assertThatThrownBy(() -> orchestrator.createTransaction(request, USER_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Occurrence not found");
        verify(transactionService).createTransactionEntity(request, USER_ID);
        verify(fixedPaymentOccurrenceService).markOccurrenceAsPaid(10L, savedTransaction, USER_ID);
    }

    @Test
    void deleteLinkedTransaction_unlinksOccurrenceThenDeletes() {
        // given
        FixedPaymentOccurrence occurrence = linkedOccurrence(savedTransaction);
        when(transactionService.getEntityForUser(100L, USER_ID)).thenReturn(savedTransaction);

        // when
        orchestrator.deleteTransaction(100L, USER_ID);

        // then
        var inOrder = inOrder(fixedPaymentOccurrenceService, transactionService);
        inOrder.verify(fixedPaymentOccurrenceService).unlinkAndReset(occurrence);
        inOrder.verify(transactionService).deleteTransaction(100L, USER_ID);
    }

    @Test
    void deleteUnlinkedTransaction_deletesWithoutTouchingOccurrences() {
        // given
        when(transactionService.getEntityForUser(100L, USER_ID)).thenReturn(savedTransaction);

        // when
        orchestrator.deleteTransaction(100L, USER_ID);

        // then
        verify(transactionService).deleteTransaction(100L, USER_ID);
        verify(fixedPaymentOccurrenceService, never()).unlinkAndReset(any());
    }

    @Test
    void updateLinkedTransaction_syncsOccurrencePaidAmount() {
        // given
        FixedPaymentOccurrence occurrence = linkedOccurrence(savedTransaction);
        TransactionUpdateRequest request = updateRequest(1, 1, new BigDecimal("1600.00"));
        when(transactionService.getEntityForUser(100L, USER_ID)).thenReturn(savedTransaction);
        when(categoryService.getCategoryEntityByIdForUser(1, USER_ID)).thenReturn(expenseCategory);

        // when
        orchestrator.updateTransaction(100L, request, USER_ID);

        // then
        verify(transactionService).updateTransaction(100L, request, USER_ID);
        verify(fixedPaymentOccurrenceService).syncPaidAmount(occurrence, new BigDecimal("1600.00"));
    }

    @Test
    void updateUnlinkedTransaction_doesNotTouchOccurrences() {
        // given
        TransactionUpdateRequest request = updateRequest(1, 1, new BigDecimal("75.00"));
        when(transactionService.getEntityForUser(100L, USER_ID)).thenReturn(savedTransaction);

        // when
        orchestrator.updateTransaction(100L, request, USER_ID);

        // then
        verify(transactionService).updateTransaction(100L, request, USER_ID);
        verifyNoInteractions(fixedPaymentOccurrenceService);
    }

    @Test
    void updateLinkedTransactionIntoIncome_isBlockedAndDoesNotUpdate() {
        // given
        linkedOccurrence(savedTransaction);
        Category incomeCategory =
                Category.builder().id(2).name("Salary").type(CategoryType.INCOME).user(user).build();
        TransactionUpdateRequest request = updateRequest(1, 2, new BigDecimal("1600.00"));
        when(transactionService.getEntityForUser(100L, USER_ID)).thenReturn(savedTransaction);
        when(categoryService.getCategoryEntityByIdForUser(2, USER_ID)).thenReturn(incomeCategory);

        // when / then
        assertThatThrownBy(() -> orchestrator.updateTransaction(100L, request, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("income");
        verify(transactionService, never()).updateTransaction(any(), any(), any());
        verify(fixedPaymentOccurrenceService, never()).syncPaidAmount(any(), any());
    }

    @Test
    void updateLinkedTransactionToDifferentWallet_isBlockedAndDoesNotUpdate() {
        // given
        linkedOccurrence(savedTransaction);
        TransactionUpdateRequest request = updateRequest(2, 1, new BigDecimal("1600.00"));
        when(transactionService.getEntityForUser(100L, USER_ID)).thenReturn(savedTransaction);
        when(categoryService.getCategoryEntityByIdForUser(1, USER_ID)).thenReturn(expenseCategory);

        // when / then
        assertThatThrownBy(() -> orchestrator.updateTransaction(100L, request, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wallet");
        verify(transactionService, never()).updateTransaction(any(), any(), any());
        verify(fixedPaymentOccurrenceService, never()).syncPaidAmount(any(), any());
    }
}
