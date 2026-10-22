package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.UnlinkedExpense;
import com.mikeshaggy.backend.insight.detector.InsightContext.FixedPaymentHistory;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mikeshaggy.backend.insight.detector.TestContexts.context;
import static com.mikeshaggy.backend.insight.detector.TestContexts.money;
import static org.assertj.core.api.Assertions.assertThat;

/** Stage 7.3 {@code RecurringMerchantDetected}: ≥ 3 unlinked, same normalised title, ±10 %, 25–35 days apart. */
class RecurringMerchantDetectedDetectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);

    private final RecurringMerchantDetectedDetector detector = new RecurringMerchantDetectedDetector();

    @Test
    void threeMonthlyCharges_fire_withAFixedPaymentPrefill() {
        List<InsightCandidate> candidates = detect(List.of(
                expense(1L, "FX STREAM", "2026-07-12", "49.99"),
                expense(2L, "fx stream ", "2026-08-12", "49.99"),
                expense(3L, "FX  STREAM", "2026-09-11", "52.99")), List.of());

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.severity()).isEqualTo(InsightSeverity.INFO);
            assertThat(candidate.dedupeKey()).isEqualTo("merchant:fx stream");
            assertThat(candidate.actionType()).isEqualTo(InsightActionType.CREATE_FIXED_PAYMENT);
            assertThat(candidate.actionRef()).isNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> prefill = (Map<String, Object>) candidate.payload().get("prefill");
            assertThat(prefill)
                    .containsEntry("title", "FX  STREAM")
                    .containsEntry("amount", money("52.99"))
                    .containsEntry("categoryId", 4)
                    .containsEntry("walletId", TestContexts.WALLET)
                    .containsEntry("cycle", "MONTHLY")
                    .containsEntry("anchorDate", "2026-10-11");
        });
    }

    @Test
    void twoCharges_doNotFire_three_do() {
        assertThat(detect(List.of(
                expense(1L, "FX_GYM", "2026-08-12", "99.00"),
                expense(2L, "FX_GYM", "2026-09-11", "99.00")), List.of())).isEmpty();
        assertThat(detect(List.of(
                expense(1L, "FX_GYM", "2026-07-13", "99.00"),
                expense(2L, "FX_GYM", "2026-08-12", "99.00"),
                expense(3L, "FX_GYM", "2026-09-11", "99.00")), List.of())).hasSize(1);
    }

    @Test
    void gapBoundaries_25And35AreInclusive() {
        assertThat(run("2026-07-27", "2026-08-21", "2026-09-14")).isFalse();  // gaps 25, 24: broken at 24
        assertThat(run("2026-07-26", "2026-08-20", "2026-09-14")).isTrue();   // 25, 25
        assertThat(run("2026-07-06", "2026-08-10", "2026-09-14")).isTrue();   // 35, 35
        assertThat(run("2026-07-05", "2026-08-10", "2026-09-14")).isFalse();  // 36, 35: only two in the run
    }

    @Test
    void amountTolerance_10PercentOfTheLatestIsInclusive() {
        assertThat(amounts("90.00", "110.00", "100.00")).isTrue();
        assertThat(amounts("89.99", "100.00", "100.00")).isFalse();
        assertThat(amounts("100.00", "110.01", "100.00")).isFalse();
    }

    @Test
    void aWeeklyShop_neverQualifies_evenIfMonthlySubsequencesExist() {
        List<UnlinkedExpense> weekly = new ArrayList<>();
        LocalDate day = LocalDate.of(2026, 6, 1);
        long id = 1;
        while (!day.isAfter(TODAY)) {
            weekly.add(expense(id++, "FX_GROCER", day.toString(), "100.00"));
            day = day.plusDays(7);
        }
        assertThat(detect(weekly, List.of())).isEmpty();
    }

    @Test
    void aMerchantAlreadySetUpAsAFixedPayment_isNotSuggested() {
        List<FixedPaymentHistory> fixed = List.of(new FixedPaymentHistory(9, "  Fx_Stream", money("49.99"), 4,
                List.of()));
        assertThat(detect(List.of(
                expense(1L, "FX_STREAM", "2026-07-12", "49.99"),
                expense(2L, "FX_STREAM", "2026-08-12", "49.99"),
                expense(3L, "FX_STREAM", "2026-09-11", "49.99")), fixed)).isEmpty();
    }

    @Test
    void aPatternThatStopped_orTinyAmounts_areNotSuggested() {
        // latest charge 36 days ago
        assertThat(detect(List.of(
                expense(1L, "FX_OLD", "2026-06-10", "49.99"),
                expense(2L, "FX_OLD", "2026-07-10", "49.99"),
                expense(3L, "FX_OLD", "2026-08-09", "49.99")), List.of())).isEmpty();
        assertThat(detect(List.of(
                expense(1L, "FX_TINY", "2026-07-12", "9.99"),
                expense(2L, "FX_TINY", "2026-08-12", "9.99"),
                expense(3L, "FX_TINY", "2026-09-11", "9.99")), List.of())).isEmpty();
    }

    @Test
    void floorAndRecency_boundaries_areInclusive() {
        // latest exactly 10.00 PLN qualifies
        assertThat(three("FX_TEN", "2026-07-13", "2026-08-12", "2026-09-11", "10.00")).hasSize(1);
        // latest exactly 35 days before today (Aug 10 → Sep 14) qualifies; 36 days (Aug 9) does not
        assertThat(three("FX_AGE", "2026-06-11", "2026-07-11", "2026-08-10", "49.99")).hasSize(1);
        assertThat(three("FX_AGE", "2026-06-10", "2026-07-10", "2026-08-09", "49.99")).isEmpty();
    }

    private List<InsightCandidate> three(String title, String first, String second, String latest, String amount) {
        return detect(List.of(expense(1L, title, first, amount), expense(2L, title, second, amount),
                expense(3L, title, latest, amount)), List.of());
    }

    @Test
    void futureDatedExpenses_areIgnored() {
        assertThat(detect(List.of(
                expense(1L, "FX_F", "2026-08-12", "49.99"),
                expense(2L, "FX_F", "2026-09-11", "49.99"),
                expense(3L, "FX_F", "2026-10-11", "49.99")), List.of())).isEmpty();
    }

    @Test
    void longTitles_getABoundedHashedKey_stableAcrossEvaluations() {
        String title = "FX_" + "X".repeat(150);
        List<UnlinkedExpense> expenses = List.of(expense(1L, title, "2026-07-12", "49.99"),
                expense(2L, title, "2026-08-12", "49.99"), expense(3L, title, "2026-09-11", "49.99"));

        List<InsightCandidate> first = detect(expenses, List.of());
        List<InsightCandidate> second = detect(expenses, List.of());

        assertThat(first).singleElement().satisfies(candidate -> {
            assertThat(candidate.dedupeKey()).hasSizeLessThanOrEqualTo(InsightCandidate.MAX_DEDUPE_KEY_LENGTH)
                    .startsWith("merchant:fx_xxx").contains("#");
            assertThat(candidate.title()).hasSizeLessThanOrEqualTo(InsightCandidate.MAX_TITLE_LENGTH);
        });
        assertThat(second).isEqualTo(first);
    }

    private boolean run(String first, String second, String third) {
        return !detect(List.of(expense(1L, "FX_R", first, "50.00"), expense(2L, "FX_R", second, "50.00"),
                expense(3L, "FX_R", third, "50.00")), List.of()).isEmpty();
    }

    private boolean amounts(String first, String second, String latest) {
        return !detect(List.of(expense(1L, "FX_A", "2026-07-13", first), expense(2L, "FX_A", "2026-08-12", second),
                expense(3L, "FX_A", "2026-09-11", latest)), List.of()).isEmpty();
    }

    private List<InsightCandidate> detect(List<UnlinkedExpense> expenses, List<FixedPaymentHistory> fixed) {
        return detector.detectAll(context().today(TODAY).expenseHistory(expenses).fixedPayments(fixed).build());
    }

    private static UnlinkedExpense expense(Long id, String title, String date, String amount) {
        return new UnlinkedExpense(id, LocalDate.parse(date), title, 4, "subscriptions", money(amount));
    }
}
