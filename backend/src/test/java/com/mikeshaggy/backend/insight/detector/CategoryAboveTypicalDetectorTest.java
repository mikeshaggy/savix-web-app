package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.forecast.HistoricalCategorySpendService.CategoryAtDay;
import com.mikeshaggy.backend.analytics.forecast.HistoricalCategorySpendService.CategoryDayBaseline;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.mikeshaggy.backend.insight.detector.TestContexts.AUGUST;
import static com.mikeshaggy.backend.insight.detector.TestContexts.USER;
import static com.mikeshaggy.backend.insight.detector.TestContexts.WALLET;
import static com.mikeshaggy.backend.insight.detector.TestContexts.context;
import static com.mikeshaggy.backend.insight.detector.TestContexts.money;
import static org.assertj.core.api.Assertions.assertThat;

/** Stage 7.3 {@code CategoryAboveTypical} on the audited Sep 14 fixture (day 6) and at its threshold boundaries. */
class CategoryAboveTypicalDetectorTest {

    private final CategoryAboveTypicalDetector detector = new CategoryAboveTypicalDetector();

    @Test
    void groceries_314_84_vs_typical_162_34_atDay6_fires() {
        List<InsightCandidate> candidates = detector.detectAll(context()
                .categoryDayBaseline(baseline(6, category(3, "Groceries", "314.84", "162.34"))).build());

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.type()).isEqualTo(InsightType.CATEGORY_ABOVE_TYPICAL);
            assertThat(candidate.severity()).isEqualTo(InsightSeverity.WARN);
            assertThat(candidate.dedupeKey()).isEqualTo("category:3");
            assertThat(candidate.title()).isEqualTo("Groceries above typical");
            assertThat(candidate.actionType()).isEqualTo(InsightActionType.LINK);
            assertThat(candidate.actionRef()).isEqualTo("/analytics/breakdown?category=3");
            assertThat(candidate.payload())
                    .containsEntry("current", money("314.84"))
                    .containsEntry("typical", money("162.34"))
                    .containsEntry("excess", money("152.50"))
                    .containsEntry("excessPercent", money("93.94"))
                    .containsEntry("dayIndex", 6)
                    .containsEntry("baselineCycles", 6);
            assertThat(candidate.description()).contains("314.84 PLN", "162.34 PLN", "+152.50 PLN", "+94%");
        });
    }

    @Test
    void smoking_55_50_vs_63_56_doesNotFire() {
        assertThat(detector.detectAll(context()
                .categoryDayBaseline(baseline(6, category(5, "Smoking", "55.50", "63.56"))).build())).isEmpty();
    }

    @Test
    void transport_withA22_25Baseline_doesNotFire_noPercentageOnASmallBase() {
        // the legacy engine reported "transport up 770%" (193.62 vs 22.25)
        assertThat(detector.detectAll(context()
                .categoryDayBaseline(baseline(6, category(7, "Transport", "193.62", "22.25"))).build())).isEmpty();
    }

    @Test
    void auditedDay6_onlyGroceriesFires() {
        List<InsightCandidate> candidates = detector.detectAll(context().categoryDayBaseline(baseline(6,
                category(3, "Groceries", "314.84", "162.34"),
                category(5, "Smoking", "55.50", "63.56"),
                category(7, "Transport", "193.62", "22.25"))).build());

        assertThat(candidates).extracting(InsightCandidate::dedupeKey).containsExactly("category:3");
    }

    @Test
    void baselineFloor_isInclusiveAt100() {
        // median 100.00 → threshold 100 + max(100, 50) = 200.00, strictly exceeded
        assertThat(detect(category(1, "A", "200.00", "100.00"))).isEmpty();
        assertThat(detect(category(1, "A", "200.01", "100.00"))).hasSize(1);
        // median 99.99 never qualifies, however large the spend
        assertThat(detect(category(1, "A", "5000.00", "99.99"))).isEmpty();
    }

    @Test
    void absoluteMargin_isTheMaxOf100And50Percent() {
        // median 150 → 50 % = 75 < 100 → threshold 250.00
        assertThat(detect(category(1, "A", "250.00", "150.00"))).isEmpty();
        assertThat(detect(category(1, "A", "250.01", "150.00"))).hasSize(1);
        // median 300 → 50 % = 150 > 100 → threshold 450.00
        assertThat(detect(category(1, "A", "450.00", "300.00"))).isEmpty();
        assertThat(detect(category(1, "A", "450.01", "300.00"))).hasSize(1);
    }

    @Test
    void fewerThanThreeUsableCycles_noTypical_noInsight() {
        CategoryAtDay groceries = category(3, "Groceries", "900.00", "162.34");
        assertThat(detector.detectAll(context().categoryDayBaseline(baseline(6, 2, groceries)).build())).isEmpty();
        assertThat(detector.detectAll(context().categoryDayBaseline(baseline(6, 0, groceries)).build())).isEmpty();
        assertThat(detector.detectAll(context().categoryDayBaseline(baseline(6, 3, groceries)).build())).hasSize(1);
    }

    @Test
    void categoryWithoutMedian_isSkipped_notTreatedAsZero() {
        CategoryAtDay newCategory = new CategoryAtDay(9, "New", null, money("500.00"), null, List.of());
        assertThat(detect(newCategory)).isEmpty();
    }

    @Test
    void awaitingSalary_isNotEvaluated_andReadsNothing() {
        PayCycle awaiting = PayCycle.awaitingSalary(USER, WALLET, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9));
        // the baseline input is left unavailable: reading it would throw
        assertThat(detector.detectAll(context().cycle(awaiting).today(LocalDate.of(2026, 10, 12)).build())).isEmpty();
    }

    @Test
    void severalCategories_largestExcessFirst_andStableAcrossEvaluations() {
        CategoryDayBaseline baseline = baseline(10,
                category(3, "Groceries", "400.00", "150.00"),
                category(4, "Restaurants", "700.00", "200.00"));

        List<InsightCandidate> first = detector.detectAll(context().categoryDayBaseline(baseline).build());
        List<InsightCandidate> second = detector.detectAll(context().categoryDayBaseline(baseline).build());

        assertThat(first).extracting(InsightCandidate::dedupeKey).containsExactly("category:4", "category:3");
        assertThat(second).isEqualTo(first);
        assertThat(detector.detect(context().categoryDayBaseline(baseline).build()))
                .hasValueSatisfying(candidate -> assertThat(candidate.dedupeKey()).isEqualTo("category:4"));
    }

    private List<InsightCandidate> detect(CategoryAtDay category) {
        return detector.detectAll(context().categoryDayBaseline(baseline(6, category)).build());
    }

    private static CategoryDayBaseline baseline(int day, CategoryAtDay... categories) {
        return baseline(day, 6, categories);
    }

    private static CategoryDayBaseline baseline(int day, int cycles, CategoryAtDay... categories) {
        List<PayCycle> used = new ArrayList<>();
        for (int i = 0; i < cycles; i++) {
            used.add(PayCycle.closed(USER, WALLET, AUGUST.start().minusMonths(i), AUGUST.end().minusMonths(i)));
        }
        return new CategoryDayBaseline(day, used, List.of(categories));
    }

    private static CategoryAtDay category(int id, String name, String current, String median) {
        return new CategoryAtDay(id, name, null, money(current), median == null ? null : new BigDecimal(median),
                List.of());
    }
}
