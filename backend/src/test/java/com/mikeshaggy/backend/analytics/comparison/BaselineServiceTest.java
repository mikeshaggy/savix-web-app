package com.mikeshaggy.backend.analytics.comparison;

import com.mikeshaggy.backend.analytics.comparison.BaselineComparisonDto;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.dto.ResolvedPeriods;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.transaction.repository.CategoryBreakdownProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaselineServiceTest {

    private static final Integer WALLET_ID = 1;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // Primary period: March 2026
    private static final LocalDate PRIMARY_START = LocalDate.of(2026, 3, 1);
    private static final LocalDate PRIMARY_END   = LocalDate.of(2026, 3, 31);
    // Compare period: February 2026
    private static final LocalDate COMPARE_START = LocalDate.of(2026, 2, 1);
    private static final LocalDate COMPARE_END   = LocalDate.of(2026, 2, 28);

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletService walletService;
    @Mock private PeriodService periodService;

    private BaselineService service;

    @BeforeEach
    void setUp() {
        service = new BaselineService(transactionRepository, walletService, periodService);
        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder().id(WALLET_ID).build());
        when(periodService.resolvePeriods(PeriodType.CUSTOM, WALLET_ID, USER_ID, PRIMARY_START, PRIMARY_END))
                .thenReturn(new ResolvedPeriods(
                        new PeriodDto(PRIMARY_START, PRIMARY_END, PRIMARY_END, PeriodType.CUSTOM),
                        new PeriodDto(COMPARE_START, COMPARE_END, COMPARE_END, PeriodType.CUSTOM)));
        // Default: no categories
        when(transactionRepository.findCategoryBreakdownByWalletDateRangeAndType(
                WALLET_ID, PRIMARY_START, PRIMARY_END, CategoryType.EXPENSE)).thenReturn(List.of());
        when(transactionRepository.findCategoryBreakdownByWalletDateRangeAndType(
                WALLET_ID, COMPARE_START, COMPARE_END, CategoryType.EXPENSE)).thenReturn(List.of());
    }

    private BaselineComparisonDto run(BigDecimal primaryIncome, BigDecimal primaryExpenses,
                                      BigDecimal compareIncome, BigDecimal compareExpenses) {
        when(transactionRepository.sumByWalletUserDateRangeAndType(
                WALLET_ID, USER_ID, PRIMARY_START, PRIMARY_END, CategoryType.INCOME))
                .thenReturn(primaryIncome);
        when(transactionRepository.sumByWalletUserDateRangeAndType(
                WALLET_ID, USER_ID, PRIMARY_START, PRIMARY_END, CategoryType.EXPENSE))
                .thenReturn(primaryExpenses);
        when(transactionRepository.sumByWalletUserDateRangeAndType(
                WALLET_ID, USER_ID, COMPARE_START, COMPARE_END, CategoryType.INCOME))
                .thenReturn(compareIncome);
        when(transactionRepository.sumByWalletUserDateRangeAndType(
                WALLET_ID, USER_ID, COMPARE_START, COMPARE_END, CategoryType.EXPENSE))
                .thenReturn(compareExpenses);
        return service.getBaseline(WALLET_ID, USER_ID, PeriodType.CUSTOM, PRIMARY_START, PRIMARY_END);
    }

    // ─── tests ────────────────────────────────────────────────────────────────────

    @Test
    void periodDatesArePropagatedToDto() {
        BaselineComparisonDto result = run(
                new BigDecimal("5000.00"), new BigDecimal("3200.00"),
                new BigDecimal("4800.00"), new BigDecimal("2900.00"));

        assertThat(result.periodStart()).isEqualTo(PRIMARY_START);
        assertThat(result.periodEnd()).isEqualTo(PRIMARY_END);
        assertThat(result.compareStart()).isEqualTo(COMPARE_START);
        assertThat(result.compareEnd()).isEqualTo(COMPARE_END);
    }

    @Test
    void currentAndCompareMetricsAreCorrect() {
        BaselineComparisonDto result = run(
                new BigDecimal("5000.00"), new BigDecimal("3200.00"),
                new BigDecimal("4800.00"), new BigDecimal("2900.00"));

        assertThat(result.currentIncome()).isEqualByComparingTo("5000.00");
        assertThat(result.currentExpenses()).isEqualByComparingTo("3200.00");
        assertThat(result.currentSavingsRate()).isEqualByComparingTo("36.00");
        assertThat(result.baselineIncome()).isEqualByComparingTo("4800.00");
        assertThat(result.baselineExpenses()).isEqualByComparingTo("2900.00");
        // savingsRate = (4800-2900)/4800 * 100 = 39.5833...% → 39.58
        assertThat(result.baselineSavingsRate()).isEqualByComparingTo("39.58");
    }

    @Test
    void deltasReflectChangeFromCompareToCurrent() {
        BaselineComparisonDto result = run(
                new BigDecimal("5000.00"), new BigDecimal("3200.00"),
                new BigDecimal("4800.00"), new BigDecimal("2900.00"));

        // income delta: (5000-4800)/4800 * 100 = +4.17%
        assertThat(result.incomeDeltaPercent()).isEqualByComparingTo("4.17");
        assertThat(result.incomeDeltaDisplay()).isEqualTo("+4.17%");
        assertThat(result.incomeDeltaAvailable()).isTrue();
        // expenses delta: (3200-2900)/2900 * 100 = +10.34%
        assertThat(result.expensesDeltaPercent()).isEqualByComparingTo("10.34");
        assertThat(result.expensesDeltaDisplay()).isEqualTo("+10.34%");
        assertThat(result.expensesDeltaAvailable()).isTrue();
        // savingsRate delta: (36% - 39.58%) / |39.58%| * 100 ≈ -9.05%
        assertThat(result.savingsRateDeltaPercent()).isEqualByComparingTo("-9.05");
        assertThat(result.savingsRateDeltaDisplay()).isEqualTo("-9.05%");
        assertThat(result.savingsRateDeltaAvailable()).isTrue();
    }

    @Test
    void zeroCompareBaselineProducesNaDeltaWhenCurrentIsNonZero() {
        BaselineComparisonDto result = run(
                new BigDecimal("5000.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"));

        // income: current>0, compare=0 → N/A
        assertThat(result.incomeDeltaAvailable()).isFalse();
        assertThat(result.incomeDeltaDisplay()).isEqualTo("N/A");
        assertThat(result.incomeDeltaPercent()).isNull();
        // expenses: both 0 → delta = 0.00%, available
        assertThat(result.expensesDeltaAvailable()).isTrue();
        assertThat(result.expensesDeltaDisplay()).isEqualTo("0.00%");
        assertThat(result.expensesDeltaPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void zeroIncomeDoesNotCauseDivideByZeroInSavingsRate() {
        BaselineComparisonDto result = run(
                new BigDecimal("0.00"), new BigDecimal("250.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"));

        assertThat(result.currentSavingsRate()).isEqualByComparingTo("0.00");
        assertThat(result.baselineSavingsRate()).isEqualByComparingTo("0.00");
    }

    @Test
    void negativeSavingsRateInComparePeriodUsesStandardDeltaFormula() {
        BaselineComparisonDto result = run(
                new BigDecimal("1000.00"), new BigDecimal("250.00"),
                new BigDecimal("1000.00"), new BigDecimal("1500.00"));

        // baseline savings rate: (1000-1500)/1000 * 100 = -50%
        assertThat(result.baselineSavingsRate()).isEqualByComparingTo("-50.00");
        // current savings rate: (1000-250)/1000 * 100 = 75%
        assertThat(result.currentSavingsRate()).isEqualByComparingTo("75.00");
        // savingsRate delta: (75 - (-50)) / |-50| * 100 = 125/50*100 = -250% (current < baseline in signum change)
        // delta = (currentForDelta - baselineForDelta) / |baselineForDelta| * 100
        // = (75 - (-50)) / (-50) * 100 = 125 / (-50) * 100 = -250
        assertThat(result.savingsRateDeltaPercent()).isEqualByComparingTo("-250.00");
        assertThat(result.savingsRateDeltaDisplay()).isEqualTo("-250.00%");
        assertThat(result.savingsRateDeltaAvailable()).isTrue();
    }

    @Test
    void categoryComparisonsUseCurrentVsComparePeriod() {
        when(transactionRepository.findCategoryBreakdownByWalletDateRangeAndType(
                WALLET_ID, PRIMARY_START, PRIMARY_END, CategoryType.EXPENSE))
                .thenReturn(List.of(categoryRow(12, "Groceries", "G", "1000.00")));
        when(transactionRepository.findCategoryBreakdownByWalletDateRangeAndType(
                WALLET_ID, COMPARE_START, COMPARE_END, CategoryType.EXPENSE))
                .thenReturn(List.of(categoryRow(12, "Groceries", "G", "800.00")));

        BaselineComparisonDto result = run(
                new BigDecimal("5000.00"), new BigDecimal("3200.00"),
                new BigDecimal("4800.00"), new BigDecimal("2900.00"));

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().getFirst().categoryId()).isEqualTo(12);
        assertThat(result.categories().getFirst().baselineExpenses()).isEqualByComparingTo("800.00");
        assertThat(result.categories().getFirst().currentExpenses()).isEqualByComparingTo("1000.00");
        assertThat(result.categories().getFirst().expensesDeltaPercent()).isEqualByComparingTo("25.00");
        assertThat(result.categories().getFirst().expensesDeltaDisplay()).isEqualTo("+25.00%");
        assertThat(result.categories().getFirst().expensesDeltaAvailable()).isTrue();
    }

    @Test
    void categoryPresentOnlyInCompareAppearesWithZeroCurrentExpenses() {
        when(transactionRepository.findCategoryBreakdownByWalletDateRangeAndType(
                WALLET_ID, PRIMARY_START, PRIMARY_END, CategoryType.EXPENSE))
                .thenReturn(List.of());
        when(transactionRepository.findCategoryBreakdownByWalletDateRangeAndType(
                WALLET_ID, COMPARE_START, COMPARE_END, CategoryType.EXPENSE))
                .thenReturn(List.of(categoryRow(5, "Dining", "D", "300.00")));

        BaselineComparisonDto result = run(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300.00"));

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().getFirst().currentExpenses()).isEqualByComparingTo("0.00");
        assertThat(result.categories().getFirst().baselineExpenses()).isEqualByComparingTo("300.00");
    }

    // ─── factory helpers ─────────────────────────────────────────────────────────

    private CategoryBreakdownProjection categoryRow(Integer categoryId, String name, String emoji, String amount) {
        return new CategoryBreakdownProjection() {
            @Override public Integer getCategoryId()   { return categoryId; }
            @Override public String getName()          { return name; }
            @Override public String getEmoji()         { return emoji; }
            @Override public BigDecimal getAmount()    { return new BigDecimal(amount); }
            @Override public Long getTransactionCount(){ return 1L; }
        };
    }
}
