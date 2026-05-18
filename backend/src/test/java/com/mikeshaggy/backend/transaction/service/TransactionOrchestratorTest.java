package com.mikeshaggy.backend.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentOccurrenceService;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.dto.TransactionCreateRequest;
import com.mikeshaggy.backend.transaction.dto.TransactionResponse;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
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

    @InjectMocks
    private TransactionOrchestrator orchestrator;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);

    private Transaction savedTransaction;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(USER_ID).email("test@test.com").username("test").build();
        Wallet wallet =
                Wallet.builder().id(1).name("Main").balance(new BigDecimal("1000.00")).user(user).build();
        Category category =
                Category.builder().id(1).name("Food").type(CategoryType.EXPENSE).user(user).build();

        savedTransaction =
                Transaction.builder()
                        .id(100L)
                        .wallet(wallet)
                        .category(category)
                        .title("Test")
                        .amount(new BigDecimal("50.00"))
                        .transactionDate(DATE)
                        .importance(Importance.ESSENTIAL)
                        .build();
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
    void withOccurrenceId_wrongUser_propagatesException() {
        // given
        TransactionCreateRequest request =
                new TransactionCreateRequest(
                        1, 1, "Rent", new BigDecimal("1500"), DATE, null, Importance.ESSENTIAL, 10L);

        when(transactionService.createTransactionEntity(request, USER_ID)).thenReturn(savedTransaction);
        doThrow(new IllegalArgumentException("Occurrence does not belong to current user"))
                .when(fixedPaymentOccurrenceService)
                .markOccurrenceAsPaid(eq(10L), any(), eq(USER_ID));

        // when
        // then
        assertThatThrownBy(() -> orchestrator.createTransaction(request, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to current user");
    }
}
