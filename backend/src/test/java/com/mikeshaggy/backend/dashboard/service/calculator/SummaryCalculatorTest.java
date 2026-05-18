package com.mikeshaggy.backend.dashboard.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.SummaryDto;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SummaryCalculatorTest {

    private SummaryCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new SummaryCalculator();
    }

    private Category category(CategoryType type) {
        return Category.builder().name(type.name()).type(type).build();
    }

    private Transaction transaction(BigDecimal amount, CategoryType type) {
        return Transaction.builder().amount(amount).category(category(type)).build();
    }

    @Nested
    class BasicCalculations {

        @Test
        void computesIncomeExpensesSavedAndRate() {
            // given
            List<Transaction> current =
                    List.of(
                            transaction(new BigDecimal("3000.00"), CategoryType.INCOME),
                            transaction(new BigDecimal("500.00"), CategoryType.INCOME),
                            transaction(new BigDecimal("1200.00"), CategoryType.EXPENSE),
                            transaction(new BigDecimal("800.00"), CategoryType.EXPENSE));

            // when
            SummaryDto result = calculator.calculate(current, Collections.emptyList());

            // then
            assertThat(result.income()).isEqualByComparingTo("3500.00");
            assertThat(result.expenses()).isEqualByComparingTo("2000.00");
            assertThat(result.saved()).isEqualByComparingTo("1500.00");
            assertThat(result.savingsRate()).isEqualByComparingTo("42.86");
        }

        @Test
        void emptyTransactions_allZeros() {
            // given
            // when
            SummaryDto result = calculator.calculate(Collections.emptyList(), Collections.emptyList());

            // then
            assertThat(result.income()).isEqualByComparingTo("0.00");
            assertThat(result.expenses()).isEqualByComparingTo("0.00");
            assertThat(result.saved()).isEqualByComparingTo("0.00");
            assertThat(result.savingsRate()).isEqualByComparingTo("0.00");
        }

        @Test
        void zeroIncome_savingsRateIsZero() {
            // given
            List<Transaction> current =
                    List.of(transaction(new BigDecimal("500.00"), CategoryType.EXPENSE));

            // when
            SummaryDto result = calculator.calculate(current, Collections.emptyList());

            // then
            assertThat(result.income()).isEqualByComparingTo("0.00");
            assertThat(result.expenses()).isEqualByComparingTo("500.00");
            assertThat(result.saved()).isEqualByComparingTo("-500.00");
            assertThat(result.savingsRate()).isEqualByComparingTo("0.00");
        }

        @Test
        void onlyIncome_fullSavings() {
            // given
            List<Transaction> current =
                    List.of(transaction(new BigDecimal("2000.00"), CategoryType.INCOME));

            // when
            SummaryDto result = calculator.calculate(current, Collections.emptyList());

            // then
            assertThat(result.income()).isEqualByComparingTo("2000.00");
            assertThat(result.expenses()).isEqualByComparingTo("0.00");
            assertThat(result.saved()).isEqualByComparingTo("2000.00");
            assertThat(result.savingsRate()).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    class PercentageChanges {

        @Test
        void computesPercentageChangeFromPreviousPeriod() {
            // given
            List<Transaction> current =
                    List.of(
                            transaction(new BigDecimal("4000.00"), CategoryType.INCOME),
                            transaction(new BigDecimal("2000.00"), CategoryType.EXPENSE));
            List<Transaction> previous =
                    List.of(
                            transaction(new BigDecimal("3000.00"), CategoryType.INCOME),
                            transaction(new BigDecimal("1500.00"), CategoryType.EXPENSE));

            // when
            SummaryDto result = calculator.calculate(current, previous);

            // then
            assertThat(result.incomeChange().percentage()).isEqualByComparingTo("33.33");
            assertThat(result.incomeChange().isPositive()).isTrue();

            assertThat(result.expensesChange().percentage()).isEqualByComparingTo("33.33");
            assertThat(result.expensesChange().isPositive()).isTrue();

            assertThat(result.savedChange().percentage()).isEqualByComparingTo("33.33");
            assertThat(result.savedChange().isPositive()).isTrue();
        }

        @Test
        void decrease_showsNegativeChange() {
            // given
            List<Transaction> current =
                    List.of(
                            transaction(new BigDecimal("2000.00"), CategoryType.INCOME),
                            transaction(new BigDecimal("1000.00"), CategoryType.EXPENSE));
            List<Transaction> previous =
                    List.of(
                            transaction(new BigDecimal("4000.00"), CategoryType.INCOME),
                            transaction(new BigDecimal("2000.00"), CategoryType.EXPENSE));

            // when
            SummaryDto result = calculator.calculate(current, previous);

            // then
            assertThat(result.incomeChange().percentage()).isEqualByComparingTo("50.00");
            assertThat(result.incomeChange().isPositive()).isFalse();
        }

        @Test
        void zeroPrevious_returnsZeroPercentagePositive() {
            // given
            List<Transaction> current =
                    List.of(transaction(new BigDecimal("1000.00"), CategoryType.INCOME));

            // when
            SummaryDto result = calculator.calculate(current, Collections.emptyList());

            // then
            assertThat(result.incomeChange().percentage()).isEqualByComparingTo("0.00");
            assertThat(result.incomeChange().isPositive()).isTrue();
        }

        @Test
        void bothZero_returnsZeroPercentagePositive() {
            // given
            // when
            SummaryDto result = calculator.calculate(Collections.emptyList(), Collections.emptyList());

            // then
            assertThat(result.incomeChange().percentage()).isEqualByComparingTo("0.00");
            assertThat(result.incomeChange().isPositive()).isTrue();
        }
    }
}
