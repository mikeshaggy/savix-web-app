package com.mikeshaggy.backend.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.dto.TransactionCreateRequest;
import com.mikeshaggy.backend.transaction.dto.TransactionFilterParams;
import com.mikeshaggy.backend.transaction.dto.TransactionPageResponse;
import com.mikeshaggy.backend.transaction.dto.TransactionResponse;
import com.mikeshaggy.backend.transaction.dto.TransactionUpdateRequest;
import com.mikeshaggy.backend.transaction.repo.TransactionRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletBalanceService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private WalletBalanceService walletBalanceService;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private TransactionService transactionService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);

    private User user;
    private Wallet wallet;
    private Category expenseCategory;
    private Category incomeCategory;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).email("test@test.com").username("test").build();
        wallet =
                Wallet.builder().id(1).name("Main").balance(new BigDecimal("1000.00")).user(user).build();
        expenseCategory =
                Category.builder().id(1).name("Food").type(CategoryType.EXPENSE).user(user).build();
        incomeCategory =
                Category.builder().id(2).name("Salary").type(CategoryType.INCOME).user(user).build();
    }

    @Nested
    class CreateTransaction {

        @Test
        void createsExpenseTransaction_savesAndAppliesBalance() {
            // given
            TransactionCreateRequest request =
                    new TransactionCreateRequest(
                            1, 1, "Groceries", new BigDecimal("50.00"), DATE, null, Importance.ESSENTIAL, null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(1, USER_ID)).thenReturn(expenseCategory);
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(
                            inv -> {
                                Transaction t = inv.getArgument(0);
                                t.setId(100L);
                                return t;
                            });

            // when
            TransactionResponse response = transactionService.createTransaction(request, USER_ID);

            // then
            assertThat(response.title()).isEqualTo("Groceries");
            assertThat(response.amount()).isEqualByComparingTo("50.00");
            assertThat(response.categoryType()).isEqualTo(CategoryType.EXPENSE);

            verify(walletBalanceService)
                    .applyTransaction(
                            eq(1),
                            eq(new BigDecimal("50.00")),
                            eq(CategoryType.EXPENSE),
                            eq(USER_ID),
                            eq(100L),
                            eq(DATE));
        }

        @Test
        void createsIncomeTransaction_savesAndAppliesBalance() {
            // given
            TransactionCreateRequest request =
                    new TransactionCreateRequest(
                            1, 2, "March Salary", new BigDecimal("5000.00"), DATE, null, null, null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(2, USER_ID)).thenReturn(incomeCategory);
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(
                            inv -> {
                                Transaction t = inv.getArgument(0);
                                t.setId(101L);
                                return t;
                            });

            // when
            TransactionResponse response = transactionService.createTransaction(request, USER_ID);

            // then
            assertThat(response.categoryType()).isEqualTo(CategoryType.INCOME);
            verify(walletBalanceService)
                    .applyTransaction(
                            eq(1),
                            eq(new BigDecimal("5000.00")),
                            eq(CategoryType.INCOME),
                            eq(USER_ID),
                            eq(101L),
                            eq(DATE));
        }

        @Test
        void incomeWithImportance_throws() {
            // given
            TransactionCreateRequest request =
                    new TransactionCreateRequest(
                            1, 2, "Salary", new BigDecimal("5000"), DATE, null, Importance.ESSENTIAL, null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(2, USER_ID)).thenReturn(incomeCategory);

            // when
            // then
            assertThatThrownBy(() -> transactionService.createTransaction(request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Importance must be null for INCOME");
        }

        @Test
        void expenseWithoutImportance_throws() {
            // given
            TransactionCreateRequest request =
                    new TransactionCreateRequest(
                            1, 1, "Groceries", new BigDecimal("50"), DATE, null, null, null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(1, USER_ID)).thenReturn(expenseCategory);

            // when
            // then
            assertThatThrownBy(() -> transactionService.createTransaction(request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Importance is required for EXPENSE");
        }

        @Test
        void withOccurrenceId_doesNotHandleOccurrence_delegatesToOrchestrator() {
            // given
            TransactionCreateRequest request =
                    new TransactionCreateRequest(
                            1, 1, "Rent", new BigDecimal("1500"), DATE, null, Importance.ESSENTIAL, 10L);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(1, USER_ID)).thenReturn(expenseCategory);
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(
                            inv -> {
                                Transaction t = inv.getArgument(0);
                                t.setId(200L);
                                return t;
                            });

            // when
            TransactionResponse response = transactionService.createTransaction(request, USER_ID);

            // then
            assertThat(response.id()).isEqualTo(200L);
        }

        @Test
        void withoutOccurrenceId_createsNormally() {
            // given
            TransactionCreateRequest request =
                    new TransactionCreateRequest(
                            1, 1, "Groceries", new BigDecimal("50.00"), DATE, null, Importance.ESSENTIAL, null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(1, USER_ID)).thenReturn(expenseCategory);
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(
                            inv -> {
                                Transaction t = inv.getArgument(0);
                                t.setId(300L);
                                return t;
                            });

            // when
            TransactionResponse response = transactionService.createTransaction(request, USER_ID);

            // then
            assertThat(response.id()).isEqualTo(300L);
        }

        @Test
        void persistsEntityWithCorrectFields() {
            // given
            TransactionCreateRequest request =
                    new TransactionCreateRequest(
                            1,
                            1,
                            "Lunch",
                            new BigDecimal("25.50"),
                            DATE,
                            "business lunch",
                            Importance.NICE_TO_HAVE,
                            null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(1, USER_ID)).thenReturn(expenseCategory);
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(
                            inv -> {
                                Transaction t = inv.getArgument(0);
                                t.setId(301L);
                                return t;
                            });

            // when
            transactionService.createTransaction(request, USER_ID);

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            // then
            verify(transactionRepository).save(captor.capture());
            Transaction saved = captor.getValue();

            assertThat(saved.getWallet()).isSameAs(wallet);
            assertThat(saved.getCategory()).isSameAs(expenseCategory);
            assertThat(saved.getTitle()).isEqualTo("Lunch");
            assertThat(saved.getAmount()).isEqualByComparingTo("25.50");
            assertThat(saved.getTransactionDate()).isEqualTo(DATE);
            assertThat(saved.getNotes()).isEqualTo("business lunch");
            assertThat(saved.getImportance()).isEqualTo(Importance.NICE_TO_HAVE);
        }
    }

    @Nested
    class UpdateTransaction {

        @Test
        void updateSameWalletAndCategory() {
            // given
            Transaction existing =
                    Transaction.builder()
                            .id(100L)
                            .title("Old")
                            .amount(new BigDecimal("50.00"))
                            .wallet(wallet)
                            .category(expenseCategory)
                            .transactionDate(DATE)
                            .importance(Importance.ESSENTIAL)
                            .build();

            TransactionUpdateRequest request =
                    new TransactionUpdateRequest(
                            1, 1, "Updated", new BigDecimal("75.00"), DATE, "notes", Importance.HAVE_TO_HAVE);

            when(transactionRepository.findByIdAndWalletUserId(100L, USER_ID))
                    .thenReturn(Optional.of(existing));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            TransactionResponse response = transactionService.updateTransaction(100L, request, USER_ID);

            // then
            assertThat(response.title()).isEqualTo("Updated");
            assertThat(response.amount()).isEqualByComparingTo("75.00");

            verify(walletBalanceService)
                    .adjustForTransactionEdit(
                            eq(wallet),
                            eq(new BigDecimal("50.00")),
                            eq(CategoryType.EXPENSE),
                            eq(wallet),
                            eq(new BigDecimal("75.00")),
                            eq(CategoryType.EXPENSE),
                            eq(100L),
                            eq(DATE));
        }

        @Test
        void updateWithDifferentWallet() {
            // given
            Wallet newWallet =
                    Wallet.builder().id(2).name("Savings").balance(new BigDecimal("5000")).user(user).build();

            Transaction existing =
                    Transaction.builder()
                            .id(100L)
                            .title("Old")
                            .amount(new BigDecimal("50.00"))
                            .wallet(wallet)
                            .category(expenseCategory)
                            .transactionDate(DATE)
                            .importance(Importance.ESSENTIAL)
                            .build();

            TransactionUpdateRequest request =
                    new TransactionUpdateRequest(
                            2, 1, "Moved", new BigDecimal("50.00"), DATE, null, Importance.ESSENTIAL);

            when(transactionRepository.findByIdAndWalletUserId(100L, USER_ID))
                    .thenReturn(Optional.of(existing));
            when(walletService.getWalletEntityByIdForUser(2, USER_ID)).thenReturn(newWallet);
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            transactionService.updateTransaction(100L, request, USER_ID);

            // then
            verify(walletBalanceService)
                    .adjustForTransactionEdit(
                            eq(wallet),
                            eq(new BigDecimal("50.00")),
                            eq(CategoryType.EXPENSE),
                            eq(newWallet),
                            eq(new BigDecimal("50.00")),
                            eq(CategoryType.EXPENSE),
                            eq(100L),
                            eq(DATE));
        }

        @Test
        void updateWithDifferentCategory() {
            // given
            Transaction existing =
                    Transaction.builder()
                            .id(100L)
                            .title("Old")
                            .amount(new BigDecimal("200.00"))
                            .wallet(wallet)
                            .category(expenseCategory)
                            .transactionDate(DATE)
                            .importance(Importance.ESSENTIAL)
                            .build();

            TransactionUpdateRequest request =
                    new TransactionUpdateRequest(
                            1, 2, "Salary Correction", new BigDecimal("200.00"), DATE, null, null);

            when(transactionRepository.findByIdAndWalletUserId(100L, USER_ID))
                    .thenReturn(Optional.of(existing));
            when(categoryService.getCategoryEntityByIdForUser(2, USER_ID)).thenReturn(incomeCategory);
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            transactionService.updateTransaction(100L, request, USER_ID);

            // then
            verify(walletBalanceService)
                    .adjustForTransactionEdit(
                            eq(wallet),
                            eq(new BigDecimal("200.00")),
                            eq(CategoryType.EXPENSE),
                            eq(wallet),
                            eq(new BigDecimal("200.00")),
                            eq(CategoryType.INCOME),
                            eq(100L),
                            eq(DATE));
        }

        @Test
        void updateWithWalletAndCategoryAndAmountChange() {
            // given
            Wallet newWallet =
                    Wallet.builder().id(2).name("Savings").balance(new BigDecimal("5000")).user(user).build();

            Transaction existing =
                    Transaction.builder()
                            .id(100L)
                            .title("Old")
                            .amount(new BigDecimal("200.00"))
                            .wallet(wallet)
                            .category(expenseCategory)
                            .transactionDate(DATE)
                            .importance(Importance.ESSENTIAL)
                            .build();

            TransactionUpdateRequest request =
                    new TransactionUpdateRequest(
                            2, 2, "Salary Correction", new BigDecimal("3000.00"), DATE, null, null);

            when(transactionRepository.findByIdAndWalletUserId(100L, USER_ID))
                    .thenReturn(Optional.of(existing));
            when(walletService.getWalletEntityByIdForUser(2, USER_ID)).thenReturn(newWallet);
            when(categoryService.getCategoryEntityByIdForUser(2, USER_ID)).thenReturn(incomeCategory);
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            TransactionResponse response = transactionService.updateTransaction(100L, request, USER_ID);

            // then
            assertThat(response.title()).isEqualTo("Salary Correction");
            assertThat(response.amount()).isEqualByComparingTo("3000.00");
            assertThat(response.categoryType()).isEqualTo(CategoryType.INCOME);
            assertThat(response.walletId()).isEqualTo(2);

            verify(walletBalanceService)
                    .adjustForTransactionEdit(
                            eq(wallet),
                            eq(new BigDecimal("200.00")),
                            eq(CategoryType.EXPENSE),
                            eq(newWallet),
                            eq(new BigDecimal("3000.00")),
                            eq(CategoryType.INCOME),
                            eq(100L),
                            eq(DATE));
        }

        @Test
        void transactionNotFound_throws() {
            // given
            TransactionUpdateRequest request =
                    new TransactionUpdateRequest(
                            1, 1, "X", new BigDecimal("10"), DATE, null, Importance.ESSENTIAL);

            when(transactionRepository.findByIdAndWalletUserId(999L, USER_ID))
                    .thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(() -> transactionService.updateTransaction(999L, request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class DeleteTransaction {

        @Test
        void deletesAndReversesBalance() {
            // given
            Transaction existing =
                    Transaction.builder()
                            .id(100L)
                            .title("Groceries")
                            .amount(new BigDecimal("50.00"))
                            .wallet(wallet)
                            .category(expenseCategory)
                            .transactionDate(DATE)
                            .importance(Importance.ESSENTIAL)
                            .build();

            when(transactionRepository.findByIdAndWalletUserId(100L, USER_ID))
                    .thenReturn(Optional.of(existing));

            // when
            transactionService.deleteTransaction(100L, USER_ID);

            // then
            verify(walletBalanceService)
                    .reverseTransaction(
                            eq(1),
                            eq(new BigDecimal("50.00")),
                            eq(CategoryType.EXPENSE),
                            eq(USER_ID),
                            eq(100L),
                            eq(DATE));
            verify(transactionRepository).delete(existing);
        }

        @Test
        void deletesIncomeTransaction_reversesBalance() {
            // given
            Transaction existing =
                    Transaction.builder()
                            .id(101L)
                            .title("Salary")
                            .amount(new BigDecimal("5000.00"))
                            .wallet(wallet)
                            .category(incomeCategory)
                            .transactionDate(DATE)
                            .build();

            when(transactionRepository.findByIdAndWalletUserId(101L, USER_ID))
                    .thenReturn(Optional.of(existing));

            // when
            transactionService.deleteTransaction(101L, USER_ID);

            // then
            verify(walletBalanceService)
                    .reverseTransaction(
                            eq(1),
                            eq(new BigDecimal("5000.00")),
                            eq(CategoryType.INCOME),
                            eq(USER_ID),
                            eq(101L),
                            eq(DATE));
            verify(transactionRepository).delete(existing);
        }

        @Test
        void transactionNotFound_throws() {
            // given
            when(transactionRepository.findByIdAndWalletUserId(999L, USER_ID))
                    .thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(() -> transactionService.deleteTransaction(999L, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class ReadTransaction {

        @Test
        void getById_returnsResponse() {
            // given
            Transaction existing =
                    Transaction.builder()
                            .id(100L)
                            .title("Test")
                            .amount(new BigDecimal("50.00"))
                            .wallet(wallet)
                            .category(expenseCategory)
                            .transactionDate(DATE)
                            .importance(Importance.ESSENTIAL)
                            .build();

            when(transactionRepository.findByIdAndWalletUserId(100L, USER_ID))
                    .thenReturn(Optional.of(existing));

            // when
            TransactionResponse response = transactionService.getTransactionByIdForUser(100L, USER_ID);

            // then
            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.title()).isEqualTo("Test");
            assertThat(response.walletId()).isEqualTo(1);
            assertThat(response.categoryName()).isEqualTo("Food");
        }

        @Test
        void getById_notFound_throws() {
            // given
            when(transactionRepository.findByIdAndWalletUserId(999L, USER_ID))
                    .thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(() -> transactionService.getTransactionByIdForUser(999L, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class GetTransactionsForUser {

        private Transaction txOnDate(Long id, LocalDate date) {
            return Transaction.builder()
                    .id(id)
                    .title("T" + id)
                    .amount(new BigDecimal("10.00"))
                    .wallet(wallet)
                    .category(expenseCategory)
                    .transactionDate(date)
                    .importance(Importance.ESSENTIAL)
                    .build();
        }

        @SuppressWarnings("unchecked")
        private void stubListResult(List<Transaction> content) {
            when(transactionRepository.findAll(any(Specification.class), any(Sort.class)))
                    .thenReturn(content);
        }

        @Test
        void defaultSort_whenSortIsNull() {
            // given
            stubListResult(List.of(txOnDate(1L, DATE)));

            TransactionFilterParams filter =
                    new TransactionFilterParams(
                            USER_ID, null, 0, 10, null, null, null, null, null, null, null);

            // when
            transactionService.getTransactionsForUser(filter);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Sort> captor = ArgumentCaptor.forClass(Sort.class);
            // then
            verify(transactionRepository).findAll(any(Specification.class), captor.capture());

            assertThat(captor.getValue().getOrderFor("transactionDate")).isNotNull();
            assertThat(captor.getValue().getOrderFor("transactionDate").getDirection())
                    .isEqualTo(Sort.Direction.DESC);
        }

        @Test
        void validSort_withAscDirection() {
            // given
            stubListResult(List.of());

            TransactionFilterParams filter =
                    new TransactionFilterParams(
                            USER_ID, null, 0, 10, null, null, null, null, null, null, "amount,asc");

            // when
            transactionService.getTransactionsForUser(filter);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Sort> captor = ArgumentCaptor.forClass(Sort.class);
            // then
            verify(transactionRepository).findAll(any(Specification.class), captor.capture());

            assertThat(captor.getValue().getOrderFor("amount").getDirection())
                    .isEqualTo(Sort.Direction.ASC);
        }

        @Test
        void invalidSortField_fallsBackToDefault() {
            // given
            stubListResult(List.of());

            TransactionFilterParams filter =
                    new TransactionFilterParams(
                            USER_ID, null, 0, 10, null, null, null, null, null, null, "INVALID_FIELD,asc");

            // when
            transactionService.getTransactionsForUser(filter);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Sort> captor = ArgumentCaptor.forClass(Sort.class);
            // then
            verify(transactionRepository).findAll(any(Specification.class), captor.capture());

            assertThat(captor.getValue().getOrderFor("transactionDate")).isNotNull();
            assertThat(captor.getValue().getOrderFor("transactionDate").getDirection())
                    .isEqualTo(Sort.Direction.DESC);
        }

        @Test
        void disallowedPageSize_fallsBackToDefault() {
            // given
            stubListResult(List.of());

            TransactionFilterParams filter =
                    new TransactionFilterParams(
                            USER_ID, null, 0, 15, null, null, null, null, null, null, null);

            // when
            TransactionPageResponse response = transactionService.getTransactionsForUser(filter);

            // then
            assertThat(response.size()).isEqualTo(20);
        }

        @Test
        void allowedPageSize_isRespected() {
            // given
            stubListResult(List.of());

            TransactionFilterParams filter =
                    new TransactionFilterParams(
                            USER_ID, null, 0, 50, null, null, null, null, null, null, null);

            // when
            TransactionPageResponse response = transactionService.getTransactionsForUser(filter);

            // then
            assertThat(response.size()).isEqualTo(50);
        }

        @Test
        void negativePage_clampedToZero() {
            // given
            stubListResult(List.of());

            TransactionFilterParams filter =
                    new TransactionFilterParams(
                            USER_ID, null, -5, 10, null, null, null, null, null, null, null);

            // when
            TransactionPageResponse response = transactionService.getTransactionsForUser(filter);

            // then
            assertThat(response.page()).isZero();
        }

        @Test
        void returnsPageResponseWithCorrectMetadata() {
            // 3 on DATE, 4 on DATE+1 — both fit within size=10 on a single page
            // given
            List<Transaction> content =
                    List.of(
                            txOnDate(1L, DATE),
                            txOnDate(2L, DATE),
                            txOnDate(3L, DATE),
                            txOnDate(4L, DATE.plusDays(1)),
                            txOnDate(5L, DATE.plusDays(1)),
                            txOnDate(6L, DATE.plusDays(1)),
                            txOnDate(7L, DATE.plusDays(1)));
            stubListResult(content);

            TransactionFilterParams filter =
                    new TransactionFilterParams(
                            USER_ID, null, 0, 10, null, null, null, null, null, null, null);

            // when
            TransactionPageResponse response = transactionService.getTransactionsForUser(filter);

            // then
            assertThat(response.groups()).hasSize(2);
            assertThat(response.page()).isZero();
            assertThat(response.size()).isEqualTo(10);
            assertThat(response.totalElements()).isEqualTo(7);
            assertThat(response.totalPages()).isEqualTo(1);
            assertThat(response.hasNext()).isFalse();
            assertThat(response.hasPrevious()).isFalse();
        }

        @Test
        void greedyBucket_neverSplitsDateGroup_acrossPages() {
            // Mar 5: 3 rows; Mar 4: 4 rows; Mar 3: 6 rows — size=10
            // Page 0: Mar 5 (3) + Mar 4 (4) = 7 rows; adding Mar 3 would give 13 > 10
            // Page 1: Mar 3 (6) rows alone
            // given
            LocalDate mar5 = LocalDate.of(2026, 3, 5);
            LocalDate mar4 = LocalDate.of(2026, 3, 4);
            LocalDate mar3 = LocalDate.of(2026, 3, 3);
            List<Transaction> content = new ArrayList<>();
            for (int i = 1; i <= 3; i++) content.add(txOnDate((long) i, mar5));
            for (int i = 4; i <= 7; i++) content.add(txOnDate((long) i, mar4));
            for (int i = 8; i <= 13; i++) content.add(txOnDate((long) i, mar3));

            stubListResult(content);
            // when
            TransactionPageResponse page0 =
                    transactionService.getTransactionsForUser(
                            new TransactionFilterParams(
                                    USER_ID, null, 0, 10, null, null, null, null, null, null, null));

            // then
            assertThat(page0.groups()).hasSize(2);
            assertThat(page0.groups().get(0).date()).isEqualTo(mar5);
            assertThat(page0.groups().get(0).transactions()).hasSize(3);
            assertThat(page0.groups().get(1).date()).isEqualTo(mar4);
            assertThat(page0.groups().get(1).transactions()).hasSize(4);
            assertThat(page0.totalElements()).isEqualTo(13);
            assertThat(page0.totalPages()).isEqualTo(2);
            assertThat(page0.hasNext()).isTrue();
            assertThat(page0.hasPrevious()).isFalse();

            stubListResult(content);
            TransactionPageResponse page1 =
                    transactionService.getTransactionsForUser(
                            new TransactionFilterParams(
                                    USER_ID, null, 1, 10, null, null, null, null, null, null, null));

            assertThat(page1.groups()).hasSize(1);
            assertThat(page1.groups().get(0).date()).isEqualTo(mar3);
            assertThat(page1.groups().get(0).transactions()).hasSize(6);
            assertThat(page1.totalElements()).isEqualTo(13);
            assertThat(page1.totalPages()).isEqualTo(2);
            assertThat(page1.hasNext()).isFalse();
            assertThat(page1.hasPrevious()).isTrue();
        }
    }

    @Nested
    class GetTransactionsByWalletId {

        @Test
        void returnsMappedResponses() {
            // given
            Transaction t =
                    Transaction.builder()
                            .id(1L)
                            .title("Groceries")
                            .amount(new BigDecimal("30.00"))
                            .wallet(wallet)
                            .category(expenseCategory)
                            .transactionDate(DATE)
                            .importance(Importance.ESSENTIAL)
                            .build();

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(transactionRepository.findByWalletIdAndWalletUserId(1, USER_ID)).thenReturn(List.of(t));

            // when
            List<TransactionResponse> result =
                    transactionService.getTransactionsByWalletIdForUser(1, USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).title()).isEqualTo("Groceries");
        }
    }

    @Nested
    class GetTransactionsForWalletAndPeriod {

        @Test
        void delegatesToRepositoryWithCorrectDates() {
            // given
            LocalDate start = LocalDate.of(2026, 2, 15);
            LocalDate end = LocalDate.of(2026, 3, 14);
            PeriodDto period = new PeriodDto(start, end, end, PeriodType.PAY_CYCLE);

            Transaction t =
                    Transaction.builder()
                            .id(1L)
                            .title("Test")
                            .amount(new BigDecimal("100"))
                            .wallet(wallet)
                            .category(expenseCategory)
                            .transactionDate(DATE)
                            .build();
            when(transactionRepository.findByWalletIdAndTransactionDateBetween(1, start, end))
                    .thenReturn(List.of(t));

            // when
            List<Transaction> result = transactionService.getTransactionsForWalletAndPeriod(1, period);

            // then
            assertThat(result).hasSize(1);
            verify(transactionRepository).findByWalletIdAndTransactionDateBetween(1, start, end);
        }
    }
}
