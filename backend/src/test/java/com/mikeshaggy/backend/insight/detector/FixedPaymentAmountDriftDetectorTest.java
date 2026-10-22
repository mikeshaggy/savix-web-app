package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.insight.detector.InsightContext.FixedPaymentHistory;
import com.mikeshaggy.backend.insight.detector.InsightContext.OccurrenceSnapshot;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.mikeshaggy.backend.insight.detector.TestContexts.context;
import static com.mikeshaggy.backend.insight.detector.TestContexts.money;
import static org.assertj.core.api.Assertions.assertThat;

/** Stage 7.3 {@code FixedPaymentAmountDrift}: > 5 % (exact decimals) on two consecutive paid occurrences. */
class FixedPaymentAmountDriftDetectorTest {

    private final FixedPaymentAmountDriftDetector detector = new FixedPaymentAmountDriftDetector();

    @Test
    void twoConsecutivePaymentsAbove5Percent_fire() {
        FixedPaymentHistory internet = payment(20, "FX_UTILITY_A", "200.00",
                paid(1L, "2026-07-20", "200.00"), paid(2L, "2026-08-20", "219.00"), paid(3L, "2026-09-20", "219.00"),
                pending(4L, "2026-10-20"));

        List<InsightCandidate> candidates = detector.detectAll(context().today(LocalDate.of(2026, 9, 25))
                .fixedPayments(List.of(internet)).build());

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.severity()).isEqualTo(InsightSeverity.INFO);
            assertThat(candidate.dedupeKey()).isEqualTo("fixed-payment:20");
            assertThat(candidate.actionType()).isEqualTo(InsightActionType.LINK);
            assertThat(candidate.actionRef()).isEqualTo("/transactions/fixed-payments?edit=20");
            assertThat(candidate.payload())
                    .containsEntry("configuredAmount", money("200.00"))
                    .containsEntry("suggestedAmount", money("219.00"))
                    .containsEntry("differencePercent", money("9.50"));
            assertThat(candidate.description()).contains("+10%");
        });
    }

    @Test
    void exactly5Percent_doesNotFire_justAbove_does() {
        // 5 % of 200.00 = 10.00 exactly (also the 10 PLN floor): equal is not "more than 5 %"
        assertThat(drift("200.00", "210.00", "210.00")).isFalse();
        assertThat(drift("200.00", "210.01", "210.01")).isTrue();
        assertThat(drift("200.00", "189.99", "189.99")).isTrue(); // cheaper counts too
        // a binary-floating-point-hostile amount: 5 % of 0.30 × 1000
        assertThat(drift("300.00", "315.00", "315.00")).isFalse();
        assertThat(drift("300.00", "315.01", "315.01")).isTrue();
    }

    @Test
    void absoluteFloor_10Pln_onSmallPayments() {
        // 26.99 → 29.99 is +11 % but only 3.00 PLN: below the absolute floor
        assertThat(drift("26.99", "29.99", "29.99")).isFalse();
        assertThat(drift("26.99", "36.99", "36.99")).isTrue();
    }

    @Test
    void absoluteFloor_isInclusiveAt10Pln_whenTheShareIsAbove5Percent() {
        // 150.00: 10.00 is 6.67 % (fires), 9.99 is 6.66 % but under the floor
        assertThat(drift("150.00", "160.00", "160.00")).isTrue();
        assertThat(drift("150.00", "159.99", "159.99")).isFalse();
        assertThat(drift("150.00", "140.00", "140.00")).isTrue();
        assertThat(drift("150.00", "140.01", "140.01")).isFalse();
    }

    @Test
    void smallConfiguredAmount_noPercentageShown() {
        List<InsightCandidate> candidates = detector.detectAll(context().today(LocalDate.of(2026, 9, 25))
                .fixedPayments(List.of(payment(21, "FX_SUB_D", "26.99", paid(1L, "2026-08-12", "39.99"),
                        paid(2L, "2026-09-12", "39.99")))).build());

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.payload()).containsEntry("differencePercent", null);
            assertThat(candidate.description()).doesNotContain("%");
        });
    }

    @Test
    void onlyOneDriftingOccurrence_orABreakInBetween_doesNotFire() {
        assertThat(drift("200.00", "200.00", "230.00")).isFalse();
        // the latest due occurrence is unpaid (overdue): the run of paid occurrences is broken
        FixedPaymentHistory overdue = payment(22, "FX_X", "200.00", paid(1L, "2026-07-20", "230.00"),
                paid(2L, "2026-08-20", "230.00"), new OccurrenceSnapshot(3L, LocalDate.parse("2026-09-20"),
                        OccurrenceStatus.OVERDUE, money("200.00"), null));
        assertThat(detector.detectAll(context().today(LocalDate.of(2026, 9, 25)).fixedPayments(List.of(overdue))
                .build())).isEmpty();
        // a skipped occurrence between two drifting payments breaks it too
        FixedPaymentHistory skipped = payment(23, "FX_Y", "200.00", paid(1L, "2026-07-20", "230.00"),
                new OccurrenceSnapshot(2L, LocalDate.parse("2026-08-20"), OccurrenceStatus.SKIPPED,
                        money("200.00"), null),
                paid(3L, "2026-09-20", "230.00"));
        assertThat(detector.detectAll(context().today(LocalDate.of(2026, 9, 25)).fixedPayments(List.of(skipped))
                .build())).isEmpty();
    }

    @Test
    void aFutureOccurrencePaidEarly_counts_aFuturePendingOneDoesNot() {
        FixedPaymentHistory early = payment(24, "FX_Z", "200.00", paid(1L, "2026-08-20", "230.00"),
                paid(2L, "2026-09-30", "230.00"));
        assertThat(detector.detectAll(context().today(LocalDate.of(2026, 9, 25)).fixedPayments(List.of(early))
                .build())).hasSize(1);
    }

    @Test
    void paidWithoutARecordedAmount_isNotEvidence() {
        FixedPaymentHistory legacy = payment(25, "FX_L", "200.00", paid(1L, "2026-08-20", "230.00"),
                new OccurrenceSnapshot(2L, LocalDate.parse("2026-09-20"), OccurrenceStatus.PAID, money("200.00"),
                        null));
        assertThat(detector.detectAll(context().today(LocalDate.of(2026, 9, 25)).fixedPayments(List.of(legacy))
                .build())).isEmpty();
    }

    @Test
    void auditedFixture_rentPaid1879_91Of1900_doesNotFire() {
        // 20.09 below plan = 1.06 %
        assertThat(drift("1900.00", "1879.91", "1879.91")).isFalse();
    }

    @Test
    void noFixedPayments_noInsight() {
        assertThat(detector.detectAll(context().fixedPayments(List.of()).build())).isEmpty();
    }

    private boolean drift(String configured, String previous, String latest) {
        FixedPaymentHistory payment = payment(30, "FX_P", configured, paid(1L, "2026-08-20", previous),
                paid(2L, "2026-09-20", latest));
        return !detector.detectAll(context().today(LocalDate.of(2026, 9, 25)).fixedPayments(List.of(payment)).build())
                .isEmpty();
    }

    private static FixedPaymentHistory payment(int id, String title, String amount, OccurrenceSnapshot... rows) {
        return new FixedPaymentHistory(id, title, money(amount), 4, List.of(rows));
    }

    private static OccurrenceSnapshot paid(Long id, String due, String amount) {
        return new OccurrenceSnapshot(id, LocalDate.parse(due), OccurrenceStatus.PAID, null, money(amount));
    }

    private static OccurrenceSnapshot pending(Long id, String due) {
        return new OccurrenceSnapshot(id, LocalDate.parse(due), OccurrenceStatus.PENDING, null, null);
    }
}
