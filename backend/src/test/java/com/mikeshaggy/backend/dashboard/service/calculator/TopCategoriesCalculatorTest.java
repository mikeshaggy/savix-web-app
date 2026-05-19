package com.mikeshaggy.backend.dashboard.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.CategorySpendingDto;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TopCategoriesCalculatorTest {

    private TopCategoriesCalculator calculator;
    private Map<String, Integer> categoryIds;

    @BeforeEach
    void setUp() {
        calculator = new TopCategoriesCalculator();
        categoryIds = new HashMap<>();
    }

    private Category category(String name, CategoryType type) {
        return category(name, type, false);
    }

    private Category category(String name, CategoryType type, boolean excludedFromTopCategories) {
        return Category.builder()
                .id(categoryIds.computeIfAbsent(type + ":" + name, ignored -> categoryIds.size() + 1))
                .name(name)
                .type(type)
                .excludedFromTopCategories(excludedFromTopCategories)
                .build();
    }

    private Transaction expense(String categoryName, String amount) {
        return Transaction.builder()
                .amount(new BigDecimal(amount))
                .category(category(categoryName, CategoryType.EXPENSE))
                .build();
    }

    private Transaction expense(String categoryName, String amount, boolean excludedFromTopCategories) {
        return Transaction.builder()
                .amount(new BigDecimal(amount))
                .category(category(categoryName, CategoryType.EXPENSE, excludedFromTopCategories))
                .build();
    }

    private Transaction income(String amount) {
        return Transaction.builder()
                .amount(new BigDecimal(amount))
                .category(category("Salary", CategoryType.INCOME))
                .build();
    }

    @Nested
    class BasicCalculations {

        @Test
        void returnsTopCategoriesSortedByAmount() {
            // given
            List<Transaction> current =
                    List.of(
                            expense("Food", "500"),
                            expense("Transport", "300"),
                            expense("Entertainment", "200"),
                            expense("Food", "100") // Food total = 600
                            );

            // when
            List<CategorySpendingDto> result = calculator.calculate(current, Collections.emptyList());

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).categoryName()).isEqualTo("Food");
            assertThat(result.get(0).amount()).isEqualByComparingTo("600.00");
            assertThat(result.get(1).categoryName()).isEqualTo("Transport");
            assertThat(result.get(2).categoryName()).isEqualTo("Entertainment");
        }

        @Test
        void limitsToTop5() {
            // given
            List<Transaction> current =
                    List.of(
                            expense("Cat1", "600"),
                            expense("Cat2", "500"),
                            expense("Cat3", "400"),
                            expense("Cat4", "300"),
                            expense("Cat5", "200"),
                            expense("Cat6", "100"),
                            expense("Cat7", "50"));

            // when
            List<CategorySpendingDto> result = calculator.calculate(current, Collections.emptyList());

            // then
            assertThat(result).hasSize(5);
            assertThat(result.get(0).categoryName()).isEqualTo("Cat1");
            assertThat(result.get(4).categoryName()).isEqualTo("Cat5");
        }

        @Test
        void calculatesPercentageOfTotal() {
            // given
            List<Transaction> current = List.of(expense("Food", "600"), expense("Transport", "400"));

            // when
            List<CategorySpendingDto> result = calculator.calculate(current, Collections.emptyList());

            // then
            assertThat(result.get(0).percentageOfTotal()).isEqualByComparingTo("60.00");
            assertThat(result.get(1).percentageOfTotal()).isEqualByComparingTo("40.00");
        }

        @Test
        void ignoresIncomeTransactions() {
            // given
            List<Transaction> current =
                    List.of(income("5000"), expense("Food", "600"), expense("Transport", "400"));

            // when
            List<CategorySpendingDto> result = calculator.calculate(current, Collections.emptyList());

            // then
            assertThat(result).hasSize(2);
            assertThat(result).noneMatch(dto -> dto.categoryName().equals("Salary"));
        }

        @Test
        void includesRentWhenNotExcludedFromTopCategories() {
            // given
            List<Transaction> current = List.of(expense("rent", "1200", false), expense("Food", "300"));

            // when
            List<CategorySpendingDto> result = calculator.calculate(current, Collections.emptyList());

            // then
            assertThat(result).extracting(CategorySpendingDto::categoryName).contains("rent", "Food");
            assertThat(result.getFirst().categoryName()).isEqualTo("rent");
            assertThat(result.getFirst().amount()).isEqualByComparingTo("1200.00");
        }

        @Test
        void excludesCategoriesMarkedExcludedFromTopCategories() {
            // given
            List<Transaction> current =
                    List.of(
                            expense("Rent", "1200", true),
                            expense("Food", "500"),
                            expense("Transport", "300"));

            // when
            List<CategorySpendingDto> result = calculator.calculate(current, Collections.emptyList());

            // then
            assertThat(result).extracting(CategorySpendingDto::categoryName)
                    .containsExactly("Food", "Transport");
        }

        @Test
        void groupsCategoriesByIdInsteadOfName() {
            // given
            Category groceries = Category.builder()
                    .id(1)
                    .name("Food")
                    .type(CategoryType.EXPENSE)
                    .build();
            Category dining = Category.builder()
                    .id(2)
                    .name("Food")
                    .type(CategoryType.EXPENSE)
                    .build();
            List<Transaction> current =
                    List.of(
                            Transaction.builder().amount(new BigDecimal("600")).category(groceries).build(),
                            Transaction.builder().amount(new BigDecimal("400")).category(dining).build());

            // when
            List<CategorySpendingDto> result = calculator.calculate(current, Collections.emptyList());

            // then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(CategorySpendingDto::amount)
                    .containsExactly(new BigDecimal("600.00"), new BigDecimal("400.00"));
        }

        @Test
        void emptyTransactions_returnsEmpty() {
            // given
            // when
            List<CategorySpendingDto> result =
                    calculator.calculate(Collections.emptyList(), Collections.emptyList());

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class PercentageChanges {

        @Test
        void computesChangeFromPreviousPeriod() {
            // given
            List<Transaction> current = List.of(expense("Food", "600"));
            List<Transaction> previous = List.of(expense("Food", "400"));

            // when
            List<CategorySpendingDto> result = calculator.calculate(current, previous);

            // then
            assertThat(result.getFirst().change().percentage()).isEqualByComparingTo("50.00");
            assertThat(result.getFirst().change().isPositive()).isTrue();
        }

        @Test
        void computesChangeByCategoryIdWhenNameChanged() {
            // given
            Category currentCategory = Category.builder()
                    .id(1)
                    .name("Food")
                    .type(CategoryType.EXPENSE)
                    .build();
            Category previousCategory = Category.builder()
                    .id(1)
                    .name("Groceries")
                    .type(CategoryType.EXPENSE)
                    .build();
            List<Transaction> current = List.of(
                    Transaction.builder().amount(new BigDecimal("600")).category(currentCategory).build());
            List<Transaction> previous = List.of(
                    Transaction.builder().amount(new BigDecimal("400")).category(previousCategory).build());

            // when
            List<CategorySpendingDto> result = calculator.calculate(current, previous);

            // then
            assertThat(result.getFirst().categoryName()).isEqualTo("Food");
            assertThat(result.getFirst().change().percentage()).isEqualByComparingTo("50.00");
            assertThat(result.getFirst().change().isPositive()).isTrue();
        }

        @Test
        void decrease_showsNegativeChange() {
            // given
            List<Transaction> current = List.of(expense("Food", "300"));
            List<Transaction> previous = List.of(expense("Food", "500"));

            // when
            List<CategorySpendingDto> result = calculator.calculate(current, previous);

            // then
            assertThat(result.getFirst().change().percentage()).isEqualByComparingTo("40.00");
            assertThat(result.getFirst().change().isPositive()).isFalse();
        }

        @Test
        void noPreviousData_zeroPercentagePositive() {
            // given
            List<Transaction> current = List.of(expense("Food", "500"));

            // when
            List<CategorySpendingDto> result = calculator.calculate(current, Collections.emptyList());

            // then
            assertThat(result.getFirst().change().percentage()).isEqualByComparingTo("0.00");
            assertThat(result.getFirst().change().isPositive()).isTrue();
        }

        @Test
        void newCategoryNotInPrevious_zeroPercentageChange() {
            // given
            List<Transaction> current = List.of(expense("Food", "500"), expense("NewCategory", "200"));
            List<Transaction> previous = List.of(expense("Food", "400"));

            // when
            List<CategorySpendingDto> result = calculator.calculate(current, previous);

            CategorySpendingDto newCat =
                    result.stream()
                            .filter(dto -> dto.categoryName().equals("NewCategory"))
                            .findFirst()
                            .orElseThrow();

            // then
            assertThat(newCat.change().percentage()).isEqualByComparingTo("0.00");
            assertThat(newCat.change().isPositive()).isTrue();
        }
    }
}
