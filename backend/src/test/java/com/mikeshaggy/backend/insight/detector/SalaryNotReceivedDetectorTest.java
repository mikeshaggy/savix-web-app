package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.mikeshaggy.backend.insight.detector.TestContexts.USER;
import static com.mikeshaggy.backend.insight.detector.TestContexts.WALLET;
import static com.mikeshaggy.backend.insight.detector.TestContexts.context;
import static org.assertj.core.api.Assertions.assertThat;

/** Stage 7.3 {@code SalaryNotReceived}: AWAITING_SALARY and today ≥ expected anchor (Oct 9) + 5 = Oct 14. */
class SalaryNotReceivedDetectorTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 9);
    private static final LocalDate EXPECTED = LocalDate.of(2026, 10, 9);
    private static final PayCycle AWAITING = PayCycle.awaitingSalary(USER, WALLET, START, EXPECTED);

    private final SalaryNotReceivedDetector detector = new SalaryNotReceivedDetector();

    @Test
    void expectedPlus5_fires_informational_withoutAction() {
        InsightCandidate candidate = detector.detect(context().cycle(AWAITING).today(LocalDate.of(2026, 10, 14))
                .build()).orElseThrow();

        assertThat(candidate.severity()).isEqualTo(InsightSeverity.INFO);
        assertThat(candidate.dedupeKey()).isEqualTo("expected:2026-10-09");
        assertThat(candidate.actionType()).isNull();
        assertThat(candidate.actionRef()).isNull();
        assertThat(candidate.payload()).containsEntry("expectedPayday", "2026-10-09").containsEntry("daysLate", 5L);
    }

    @Test
    void expectedPlus4_doesNotFire() {
        assertThat(detector.detect(context().cycle(AWAITING).today(LocalDate.of(2026, 10, 13)).build())).isEmpty();
    }

    @Test
    void laterDays_keepTheSameKey() {
        assertThat(detector.detect(context().cycle(AWAITING).today(LocalDate.of(2026, 10, 20)).build()))
                .hasValueSatisfying(candidate -> assertThat(candidate.dedupeKey()).isEqualTo("expected:2026-10-09"));
    }

    @Test
    void anOpenCycle_neverFires() {
        // OPEN: today is before the expected anchor by definition; the inputs are not even read
        assertThat(detector.detect(context().cycle(PayCycle.open(USER, WALLET, START, EXPECTED))
                .today(LocalDate.of(2026, 10, 8)).build())).isEmpty();
    }
}
