package com.mikeshaggy.backend.common.calculation.budget;

import com.mikeshaggy.backend.common.calculation.budget.BudgetUsageCalculator.BudgetInput;
import com.mikeshaggy.backend.common.calculation.budget.BudgetUsageCalculator.BudgetItemResult;
import com.mikeshaggy.backend.common.calculation.budget.BudgetUsageCalculator.BudgetStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetUsageCalculatorTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 5, 1);
    private static final LocalDate PERIOD_END   = LocalDate.of(2026, 5, 31);
    private static final LocalDate TODAY        = LocalDate.of(2026, 5, 20); // day 20 of 31

    private static BudgetInput groceriesInput(BigDecimal budgetAmount, int threshold) {
        return new BudgetInput(10, 1, "Groceries", "🛒", budgetAmount, threshold);
    }

    private static BudgetInput groceriesInput() {
        return groceriesInput(new BigDecimal("1000.00"), 80);
    }

    @Nested
    class StatusResolution {

        @Test
        void belowThreshold_isOk() {
            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceriesInput()),
                    Map.of(1, new BigDecimal("500.00")),
                    PERIOD_START, PERIOD_END, TODAY);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(BudgetStatus.OK);
            assertThat(result.get(0).usagePercent()).isEqualByComparingTo("50.00");
        }

        @Test
        void atThreshold_isWarning() {
            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceriesInput()),
                    Map.of(1, new BigDecimal("800.00")),    // exactly 80 %
                    PERIOD_START, PERIOD_END, TODAY);

            assertThat(result.get(0).status()).isEqualTo(BudgetStatus.WARNING);
            assertThat(result.get(0).usagePercent()).isEqualByComparingTo("80.00");
        }

        @Test
        void aboveThresholdBelow100_isWarning() {
            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceriesInput()),
                    Map.of(1, new BigDecimal("950.00")),    // 95 %
                    PERIOD_START, PERIOD_END, TODAY);

            assertThat(result.get(0).status()).isEqualTo(BudgetStatus.WARNING);
        }

        @Test
        void at100Percent_isExceeded() {
            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceriesInput()),
                    Map.of(1, new BigDecimal("1000.00")),   // exactly 100 %
                    PERIOD_START, PERIOD_END, TODAY);

            assertThat(result.get(0).status()).isEqualTo(BudgetStatus.EXCEEDED);
        }

        @Test
        void over100Percent_isExceeded() {
            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceriesInput()),
                    Map.of(1, new BigDecimal("1350.00")),   // 135 %
                    PERIOD_START, PERIOD_END, TODAY);

            assertThat(result.get(0).status()).isEqualTo(BudgetStatus.EXCEEDED);
            assertThat(result.get(0).usagePercent()).isEqualByComparingTo("135.00");
        }

        @Test
        void resolveStatus_isConsistentWithCalculateItems() {
            assertThat(BudgetUsageCalculator.resolveStatus(new BigDecimal("79.99"), 80))
                    .isEqualTo(BudgetStatus.OK);
            assertThat(BudgetUsageCalculator.resolveStatus(new BigDecimal("80.00"), 80))
                    .isEqualTo(BudgetStatus.WARNING);
            assertThat(BudgetUsageCalculator.resolveStatus(new BigDecimal("100.00"), 80))
                    .isEqualTo(BudgetStatus.EXCEEDED);
        }
    }

    @Nested
    class AmountCalculations {

        @Test
        void zeroSpend_remainingEqualsFullBudget() {
            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceriesInput()),
                    Map.of(),
                    PERIOD_START, PERIOD_END, TODAY);

            assertThat(result.get(0).spentAmount()).isEqualByComparingTo("0.00");
            assertThat(result.get(0).remainingAmount()).isEqualByComparingTo("1000.00");
            assertThat(result.get(0).usagePercent()).isEqualByComparingTo("0.00");
            assertThat(result.get(0).status()).isEqualTo(BudgetStatus.OK);
        }

        @Test
        void overspent_remainingIsNegative() {
            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceriesInput()),
                    Map.of(1, new BigDecimal("1200.00")),
                    PERIOD_START, PERIOD_END, TODAY);

            assertThat(result.get(0).remainingAmount()).isEqualByComparingTo("-200.00");
        }

        @Test
        void rounding_isHalfUp() {
            // 100 / 300 = 33.333...% → rounds to 33.33
            BudgetInput input = groceriesInput(new BigDecimal("300.00"), 80);

            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(input),
                    Map.of(1, new BigDecimal("100.00")),
                    PERIOD_START, PERIOD_END, TODAY);

            assertThat(result.get(0).usagePercent()).isEqualByComparingTo("33.33");
        }
    }

    @Nested
    class ProjectionCalculations {

        @Test
        void projectedSpend_linearFromElapsedDays() {
            // day 20 of 31, spent 400 → (400/20)*31 = 620
            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceriesInput()),
                    Map.of(1, new BigDecimal("400.00")),
                    PERIOD_START, PERIOD_END, TODAY);

            assertThat(result.get(0).projectedSpend()).isEqualByComparingTo("620.00");
        }

        @Test
        void projectedSpend_zeroSpend_isZero() {
            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceriesInput()),
                    Map.of(1, BigDecimal.ZERO),
                    PERIOD_START, PERIOD_END, TODAY);

            assertThat(result.get(0).projectedSpend()).isEqualByComparingTo("0.00");
        }

        @Test
        void daysRemaining_calculatedCorrectly() {
            // TODAY = May 20, period ends May 31 → daysBetween(May 21, May 31) = 11
            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceriesInput()),
                    Map.of(),
                    PERIOD_START, PERIOD_END, TODAY);

            assertThat(result.get(0).daysRemaining()).isEqualTo(11);
        }

        @Test
        void todayAfterPeriodEnd_daysRemainingIsZero() {
            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceriesInput()),
                    Map.of(),
                    PERIOD_START, PERIOD_END, LocalDate.of(2026, 6, 5));

            assertThat(result.get(0).daysRemaining()).isZero();
        }
    }

    @Nested
    class MultibudgetBehavior {

        @Test
        void multipleBudgets_allCalculatedIndependently() {
            BudgetInput groceries   = new BudgetInput(10, 1, "Groceries",   "🛒",  new BigDecimal("1000.00"), 80);
            BudgetInput restaurants = new BudgetInput(20, 2, "Restaurants", "🍽️", new BigDecimal("400.00"),  75);

            Map<Integer, BigDecimal> spend = Map.of(
                    1, new BigDecimal("500.00"),   // 50 %  → OK
                    2, new BigDecimal("420.00"));  // 105 % → EXCEEDED

            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceries, restaurants), spend, PERIOD_START, PERIOD_END, TODAY);

            assertThat(result).hasSize(2);
            assertThat(result.stream().filter(r -> r.categoryId().equals(1)).findFirst().orElseThrow().status())
                    .isEqualTo(BudgetStatus.OK);
            assertThat(result.stream().filter(r -> r.categoryId().equals(2)).findFirst().orElseThrow().status())
                    .isEqualTo(BudgetStatus.EXCEEDED);
        }

        @Test
        void categoryNotInSpendMap_treatedAsZeroSpend() {
            List<BudgetItemResult> result = BudgetUsageCalculator.calculateItems(
                    List.of(groceriesInput()),
                    Map.of(),
                    PERIOD_START, PERIOD_END, TODAY);

            assertThat(result.get(0).spentAmount()).isEqualByComparingTo("0.00");
            assertThat(result.get(0).status()).isEqualTo(BudgetStatus.OK);
        }
    }
}
