package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.insight.detector.InsightContext.ImpulseSplit;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.mikeshaggy.backend.insight.detector.TestContexts.USER;
import static com.mikeshaggy.backend.insight.detector.TestContexts.WALLET;
import static com.mikeshaggy.backend.insight.detector.TestContexts.context;
import static com.mikeshaggy.backend.insight.detector.TestContexts.money;
import static org.assertj.core.api.Assertions.assertThat;

/** Stage 7.3 {@code HighImpulseSpending}: SHOULDNT_HAVE > 300 PLN and > 20 % of eligible variable spending. */
class HighImpulseSpendingDetectorTest {

    private final HighImpulseSpendingDetector detector = new HighImpulseSpendingDetector();

    @Test
    void largeAndLargeShare_fires() {
        InsightCandidate candidate = detect("1552.02", "404.78").orElseThrow();

        assertThat(candidate.severity()).isEqualTo(InsightSeverity.WARN);
        assertThat(candidate.dedupeKey()).isEqualTo("impulse");
        assertThat(candidate.actionType()).isEqualTo(InsightActionType.LINK);
        assertThat(candidate.actionRef()).isEqualTo("/analytics/breakdown");
        assertThat(candidate.payload())
                .containsEntry("shouldntHave", money("404.78"))
                .containsEntry("eligibleVariable", money("1552.02"))
                .containsEntry("sharePercent", money("26.08"));
    }

    @Test
    void auditedImpulse150_doesNotFire() {
        assertThat(detect("1552.02", "150.00")).isEmpty();
    }

    @Test
    void exactly20Percent_doesNotFire_justAbove_does() {
        assertThat(detect("2000.00", "400.00")).isEmpty();
        assertThat(detect("2000.00", "400.01")).isPresent();
    }

    @Test
    void absoluteFloor_300_isExclusive() {
        // 100 % share but only 300.00
        assertThat(detect("300.00", "300.00")).isEmpty();
        assertThat(detect("300.01", "300.01")).isPresent();
    }

    @Test
    void awaitingSalary_isEvaluatedToo() {
        PayCycle awaiting = PayCycle.awaitingSalary(USER, WALLET, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9));
        assertThat(detector.detect(context().cycle(awaiting).today(LocalDate.of(2026, 10, 12))
                .impulseSplit(new ImpulseSplit(money("1000.00"), money("400.00"))).build())).isPresent();
    }

    @Test
    void missingSplit_noInsight() {
        assertThat(detector.detect(context().impulseSplit(new ImpulseSplit(null, money("900.00"))).build())).isEmpty();
    }

    private java.util.Optional<InsightCandidate> detect(String eligible, String impulse) {
        return detector.detect(context().impulseSplit(new ImpulseSplit(money(eligible), money(impulse))).build());
    }
}
