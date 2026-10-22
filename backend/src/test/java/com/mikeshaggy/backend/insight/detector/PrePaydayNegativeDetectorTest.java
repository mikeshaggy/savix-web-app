package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.CycleMinimum;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.mikeshaggy.backend.insight.detector.TestContexts.USER;
import static com.mikeshaggy.backend.insight.detector.TestContexts.WALLET;
import static com.mikeshaggy.backend.insight.detector.TestContexts.context;
import static com.mikeshaggy.backend.insight.detector.TestContexts.money;
import static org.assertj.core.api.Assertions.assertThat;

/** Stage 7.3 {@code PrePaydayNegative}: the audited Aug 10 – Sep 8 cycle bottomed out at −93.29. */
class PrePaydayNegativeDetectorTest {

    private final PrePaydayNegativeDetector detector = new PrePaydayNegativeDetector();

    @Test
    void auditedAugustMinimum_minus93_29_fires_keyedByTheClosedCycle() {
        List<CycleMinimum> minima = List.of(
                minimum("2026-07-10", "2026-08-09", "412.00", "2026-08-07"),
                minimum("2026-08-10", "2026-09-08", "-93.29", "2026-09-05"));

        InsightCandidate candidate = detector.detect(context().sparklineMinima(minima).build()).orElseThrow();

        assertThat(candidate.severity()).isEqualTo(InsightSeverity.WARN);
        assertThat(candidate.dedupeKey()).isEqualTo("cycle:2026-08-10");
        assertThat(candidate.actionType()).isEqualTo(InsightActionType.LINK);
        assertThat(candidate.actionRef()).isEqualTo("/wallets/balance-history");
        assertThat(candidate.payload())
                .containsEntry("minBalance", money("-93.29"))
                .containsEntry("cycleStart", "2026-08-10")
                .containsEntry("cycleEnd", "2026-09-08")
                .containsEntry("date", "2026-09-05");
    }

    @Test
    void onlyThePreviousCycleCounts_olderNegativeMinimaAreIgnored() {
        List<CycleMinimum> minima = List.of(
                minimum("2026-07-10", "2026-08-09", "-500.00", "2026-08-07"),
                minimum("2026-08-10", "2026-09-08", "12.00", "2026-09-05"));

        assertThat(detector.detect(context().sparklineMinima(minima).build())).isEmpty();
    }

    @Test
    void zeroIsNotNegative() {
        assertThat(detector.detect(context().sparklineMinima(List.of(
                minimum("2026-08-10", "2026-09-08", "0.00", "2026-09-05"))).build())).isEmpty();
        assertThat(detector.detect(context().sparklineMinima(List.of(
                minimum("2026-08-10", "2026-09-08", "-0.01", "2026-09-05"))).build())).isPresent();
    }

    @Test
    void noMinimumForThePreviousCycle_noInsight() {
        // no ledger entries before / in the previous cycle → no minimum at all; never "0"
        assertThat(detector.detect(context().sparklineMinima(List.of()).build())).isEmpty();
        // a minimum exists, but not for the cycle that ended right before the current one
        assertThat(detector.detect(context().sparklineMinima(List.of(
                minimum("2026-07-10", "2026-08-09", "-1.00", "2026-08-07"))).build())).isEmpty();
    }

    @Test
    void afterRollover_theNewPreviousCycleIsJudged_withItsOwnKey() {
        PayCycle october = PayCycle.open(USER, WALLET, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 11, 10));
        List<CycleMinimum> minima = List.of(
                minimum("2026-08-10", "2026-09-08", "-93.29", "2026-09-05"),
                minimum("2026-09-09", "2026-10-08", "-10.00", "2026-10-07"));

        assertThat(detector.detect(context().cycle(october).today(LocalDate.of(2026, 10, 12))
                .sparklineMinima(minima).build()))
                .hasValueSatisfying(candidate -> assertThat(candidate.dedupeKey()).isEqualTo("cycle:2026-09-09"));
    }

    private static CycleMinimum minimum(String start, String end, String balance, String date) {
        return new CycleMinimum(LocalDate.parse(start), LocalDate.parse(end), money(balance), LocalDate.parse(date));
    }
}
