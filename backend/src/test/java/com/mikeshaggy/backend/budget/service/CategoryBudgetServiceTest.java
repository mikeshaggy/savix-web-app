package com.mikeshaggy.backend.budget.service;

import com.mikeshaggy.backend.budget.domain.CategoryBudget;
import com.mikeshaggy.backend.budget.dto.CategoryBudgetCreateRequest;
import com.mikeshaggy.backend.budget.dto.CategoryBudgetResponse;
import com.mikeshaggy.backend.budget.dto.CategoryBudgetUpdateRequest;
import com.mikeshaggy.backend.budget.repository.CategoryBudgetRepository;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import com.mikeshaggy.backend.common.exception.ConflictException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryBudgetServiceTest {

    @Mock
    private CategoryBudgetRepository categoryBudgetRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PeriodService periodService;

    @Mock
    private Clock clock;

    @InjectMocks
    private CategoryBudgetService categoryBudgetService;

    private static final UUID USER_ID = UUID.randomUUID();
    private User user;
    private Wallet wallet;
    private Category expenseCategory;
    private Category incomeCategory;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).email("test@test.com").username("test").build();
        wallet = Wallet.builder().id(1).name("Main").user(user).balance(BigDecimal.ZERO).build();
        expenseCategory = Category.builder().id(10).name("Groceries").emoji("🛒")
                .type(CategoryType.EXPENSE).user(user).build();
        incomeCategory = Category.builder().id(11).name("Salary").emoji("💰")
                .type(CategoryType.INCOME).user(user).build();
    }

    private CategoryBudget budget(Integer id, Category category, BigDecimal amount, int threshold) {
        return CategoryBudget.builder()
                .id(id)
                .wallet(wallet)
                .category(category)
                .amount(amount)
                .warningThresholdPercent(threshold)
                .active(true)
                .build();
    }

    @Nested
    class GetAll {

        @Test
        void defaultBehavior_activeTrue_returnsOnlyActiveBudgets() {
            CategoryBudget b = budget(1, expenseCategory, new BigDecimal("1000.00"), 80);
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryBudgetRepository.findActiveByWalletIdAndUserId(1, USER_ID)).thenReturn(List.of(b));

            List<CategoryBudgetResponse> result = categoryBudgetService.getAll(1, USER_ID, true);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).categoryName()).isEqualTo("Groceries");
            verify(categoryBudgetRepository).findActiveByWalletIdAndUserId(1, USER_ID);
            verify(categoryBudgetRepository, never()).findArchivedByWalletIdAndUserId(any(), any());
        }

        @Test
        void activeFalse_returnsOnlyArchivedBudgets() {
            CategoryBudget archived = budget(2, expenseCategory, new BigDecimal("500.00"), 80);
            archived.setActive(false);
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryBudgetRepository.findArchivedByWalletIdAndUserId(1, USER_ID)).thenReturn(List.of(archived));

            List<CategoryBudgetResponse> result = categoryBudgetService.getAll(1, USER_ID, false);

            assertThat(result).hasSize(1);
            verify(categoryBudgetRepository).findArchivedByWalletIdAndUserId(1, USER_ID);
            verify(categoryBudgetRepository, never()).findActiveByWalletIdAndUserId(any(), any());
        }

        @Test
        void walletNotOwnedByUser_throws() {
            when(walletService.getWalletEntityByIdForUser(999, USER_ID))
                    .thenThrow(new EntityNotFoundException("Wallet not found"));

            assertThatThrownBy(() -> categoryBudgetService.getAll(999, USER_ID, true))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class Create {

        @Test
        void happyPath_savesAndReturnsBudget() {
            CategoryBudgetCreateRequest request =
                    new CategoryBudgetCreateRequest(1, 10, new BigDecimal("1500.00"), 80);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(10, USER_ID)).thenReturn(expenseCategory);
            when(categoryBudgetRepository.existsActiveByWalletIdAndCategoryId(1, 10)).thenReturn(false);
            when(categoryBudgetRepository.save(any(CategoryBudget.class))).thenAnswer(inv -> {
                CategoryBudget b = inv.getArgument(0);
                b.setId(42);
                return b;
            });

            CategoryBudgetResponse result = categoryBudgetService.create(request, USER_ID);

            assertThat(result.id()).isEqualTo(42);
            assertThat(result.amount()).isEqualByComparingTo("1500.00");
            assertThat(result.warningThresholdPercent()).isEqualTo(80);
            assertThat(result.active()).isTrue();

            ArgumentCaptor<CategoryBudget> captor = ArgumentCaptor.forClass(CategoryBudget.class);
            verify(categoryBudgetRepository).save(captor.capture());
            assertThat(captor.getValue().getWallet()).isSameAs(wallet);
            assertThat(captor.getValue().getCategory()).isSameAs(expenseCategory);
        }

        @Test
        void defaultThreshold_is80WhenNotProvided() {
            CategoryBudgetCreateRequest request =
                    new CategoryBudgetCreateRequest(1, 10, new BigDecimal("1500.00"), null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(10, USER_ID)).thenReturn(expenseCategory);
            when(categoryBudgetRepository.existsActiveByWalletIdAndCategoryId(1, 10)).thenReturn(false);
            when(categoryBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            categoryBudgetService.create(request, USER_ID);

            ArgumentCaptor<CategoryBudget> captor = ArgumentCaptor.forClass(CategoryBudget.class);
            verify(categoryBudgetRepository).save(captor.capture());
            assertThat(captor.getValue().getWarningThresholdPercent()).isEqualTo(80);
        }

        @Test
        void incomeCategoryBudget_throws() {
            CategoryBudgetCreateRequest request =
                    new CategoryBudgetCreateRequest(1, 11, new BigDecimal("5000.00"), null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(11, USER_ID)).thenReturn(incomeCategory);

            assertThatThrownBy(() -> categoryBudgetService.create(request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expense");

            verify(categoryBudgetRepository, never()).save(any());
        }

        @Test
        void duplicateActiveBudget_throws() {
            CategoryBudgetCreateRequest request =
                    new CategoryBudgetCreateRequest(1, 10, new BigDecimal("1000.00"), null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(10, USER_ID)).thenReturn(expenseCategory);
            when(categoryBudgetRepository.existsActiveByWalletIdAndCategoryId(1, 10)).thenReturn(true);

            assertThatThrownBy(() -> categoryBudgetService.create(request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(categoryBudgetRepository, never()).save(any());
        }

        @Test
        void wrongWalletOwnership_throws() {
            CategoryBudgetCreateRequest request =
                    new CategoryBudgetCreateRequest(999, 10, new BigDecimal("1000.00"), null);

            when(walletService.getWalletEntityByIdForUser(999, USER_ID))
                    .thenThrow(new EntityNotFoundException("Wallet not found"));

            assertThatThrownBy(() -> categoryBudgetService.create(request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class Update {

        @Test
        void updatesAmountAndThreshold() {
            CategoryBudget existing = budget(5, expenseCategory, new BigDecimal("1000.00"), 80);
            CategoryBudgetUpdateRequest request = new CategoryBudgetUpdateRequest(new BigDecimal("1800.00"), 70);

            when(categoryBudgetRepository.findByIdAndWalletUserId(5, USER_ID)).thenReturn(Optional.of(existing));
            when(categoryBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CategoryBudgetResponse result = categoryBudgetService.update(5, request, USER_ID);

            assertThat(result.amount()).isEqualByComparingTo("1800.00");
            assertThat(result.warningThresholdPercent()).isEqualTo(70);
        }

        @Test
        void budgetNotOwned_throws() {
            CategoryBudgetUpdateRequest request = new CategoryBudgetUpdateRequest(new BigDecimal("1000.00"), null);
            when(categoryBudgetRepository.findByIdAndWalletUserId(999, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryBudgetService.update(999, request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        void nullThreshold_doesNotChangeExisting() {
            CategoryBudget existing = budget(5, expenseCategory, new BigDecimal("1000.00"), 75);
            CategoryBudgetUpdateRequest request = new CategoryBudgetUpdateRequest(new BigDecimal("1200.00"), null);

            when(categoryBudgetRepository.findByIdAndWalletUserId(5, USER_ID)).thenReturn(Optional.of(existing));
            when(categoryBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CategoryBudgetResponse result = categoryBudgetService.update(5, request, USER_ID);

            assertThat(result.warningThresholdPercent()).isEqualTo(75);
        }
    }

    @Nested
    class Deactivate {

        @Test
        void setsActiveFalse() {
            CategoryBudget existing = budget(7, expenseCategory, new BigDecimal("500.00"), 80);

            when(categoryBudgetRepository.findByIdAndWalletUserId(7, USER_ID)).thenReturn(Optional.of(existing));
            when(categoryBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            categoryBudgetService.deactivate(7, USER_ID);

            ArgumentCaptor<CategoryBudget> captor = ArgumentCaptor.forClass(CategoryBudget.class);
            verify(categoryBudgetRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        void budgetNotOwned_throws() {
            when(categoryBudgetRepository.findByIdAndWalletUserId(999, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryBudgetService.deactivate(999, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(categoryBudgetRepository, never()).save(any());
        }
    }

    @Nested
    class Reactivate {

        @Test
        void setsActiveTrue_whenNoOtherActiveBudgetExists() {
            CategoryBudget archived = budget(8, expenseCategory, new BigDecimal("600.00"), 80);
            archived.setActive(false);

            when(categoryBudgetRepository.findByIdAndWalletUserId(8, USER_ID)).thenReturn(Optional.of(archived));
            when(categoryBudgetRepository.existsActiveByWalletIdAndCategoryIdExcluding(
                    wallet.getId(), expenseCategory.getId(), 8)).thenReturn(false);
            when(categoryBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CategoryBudgetResponse result = categoryBudgetService.reactivate(8, USER_ID);

            assertThat(result.active()).isTrue();
            ArgumentCaptor<CategoryBudget> captor = ArgumentCaptor.forClass(CategoryBudget.class);
            verify(categoryBudgetRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isTrue();
        }

        @Test
        void throws409_whenAnotherActiveBudgetExistsForSameCategory() {
            CategoryBudget archived = budget(8, expenseCategory, new BigDecimal("600.00"), 80);
            archived.setActive(false);

            when(categoryBudgetRepository.findByIdAndWalletUserId(8, USER_ID)).thenReturn(Optional.of(archived));
            when(categoryBudgetRepository.existsActiveByWalletIdAndCategoryIdExcluding(
                    wallet.getId(), expenseCategory.getId(), 8)).thenReturn(true);

            assertThatThrownBy(() -> categoryBudgetService.reactivate(8, USER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already exists");

            verify(categoryBudgetRepository, never()).save(any());
        }

        @Test
        void budgetNotOwned_throws() {
            when(categoryBudgetRepository.findByIdAndWalletUserId(999, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryBudgetService.reactivate(999, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class Delete {

        @Test
        void permanentlyDeletesBudget() {
            CategoryBudget existing = budget(9, expenseCategory, new BigDecimal("700.00"), 80);
            when(categoryBudgetRepository.findByIdAndWalletUserId(9, USER_ID)).thenReturn(Optional.of(existing));

            categoryBudgetService.delete(9, USER_ID);

            verify(categoryBudgetRepository).delete(existing);
        }

        @Test
        void budgetNotOwned_throws() {
            when(categoryBudgetRepository.findByIdAndWalletUserId(999, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryBudgetService.delete(999, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(categoryBudgetRepository, never()).delete(any());
        }
    }

    @Nested
    class GetUsage {

        @Test
        void resolvesPeriodAndDelegatesToCalculator() {
            PeriodDto period = new PeriodDto(
                    LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 5, 31),
                    LocalDate.of(2026, 6, 1),
                    PeriodType.PAY_CYCLE);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(periodService.resolve(PeriodType.PAY_CYCLE, 1, USER_ID, null, null)).thenReturn(period);
            when(categoryBudgetRepository.findActiveByWalletIdAndUserId(1, USER_ID)).thenReturn(List.of());
            when(transactionRepository.findCategorySpendByWalletUserAndDateRange(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(clock.instant()).thenReturn(Instant.parse("2026-05-20T12:00:00Z"));
            when(clock.getZone()).thenReturn(ZoneOffset.UTC);

            var response = categoryBudgetService.getUsage(1, USER_ID, PeriodType.PAY_CYCLE, null, null);

            assertThat(response.walletId()).isEqualTo(1);
            // PAY_CYCLE: periodEnd = billingEndDate(Jun 1) - 1 day = May 31
            assertThat(response.periodStart()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(response.periodEnd()).isEqualTo(LocalDate.of(2026, 5, 31));
            assertThat(response.budgets()).isEmpty();
        }
    }
}
