package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.analytics.forecast.OneOffDto;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.regression.September2026Fixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.mikeshaggy.backend.insight.detector.TestContexts.context;
import static com.mikeshaggy.backend.insight.detector.TestContexts.money;
import static org.assertj.core.api.Assertions.assertThat;

/** Stage 7.3 {@code OneOffSkewingForecast}: the audited 300 PLN repayment, and nothing once it is excluded. */
class OneOffSkewingForecastDetectorTest {

    private final OneOffSkewingForecastDetector detector = new OneOffSkewingForecastDetector();

    @Test
    void audited300PlnRepayment_fires_withTheExactTransactionId() {
        // fixture: the repayment (id 300, impact +462.33) and an already excluded one-off (id 301)
        List<InsightCandidate> candidates = detector.detectAll(context()
                .forecast(September2026Fixture.forecastV2Dto()).build());

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.severity()).isEqualTo(InsightSeverity.INFO);
            assertThat(candidate.dedupeKey()).isEqualTo("transaction:300");
            assertThat(candidate.actionType()).isEqualTo(InsightActionType.EXCLUDE_TRANSACTION);
            assertThat(candidate.actionRef()).isEqualTo("300");
            assertThat(candidate.payload())
                    .containsEntry("transactionId", 300L)
                    .containsEntry("amount", money("300.00"))
                    .containsEntry("impactOnExpectedEndBalance", money("462.33"));
        });
    }

    @Test
    void afterExclusion_theSameTransactionIsNoLongerACandidate() {
        // what ForecastService reports once the repayment is excluded: excluded = true, impacts 0.00
        OneOffDto excluded = new OneOffDto(300L, September2026Fixture.ONE_OFF_DATE, "FX_REPAYMENT", "misc",
                money("300.00"), money("19.33"), money("0.00"), money("0.00"), true);

        assertThat(detector.detectAll(context().forecast(forecast(excluded)).build())).isEmpty();
    }

    @Test
    void impactFloor_200_isInclusive() {
        assertThat(detector.detectAll(context().forecast(forecast(oneOff(1L, "200.00"))).build())).hasSize(1);
        assertThat(detector.detectAll(context().forecast(forecast(oneOff(1L, "199.99"))).build())).isEmpty();
        // the impact, not the amount, decides: a 900 PLN one-off barely moving the forecast does not fire
        OneOffDto large = new OneOffDto(2L, LocalDate.of(2026, 9, 13), "FX_BIG", "misc", money("900.00"),
                money("40.00"), money("150.00"), money("150.00"), false);
        assertThat(detector.detectAll(context().forecast(forecast(large)).build())).isEmpty();
    }

    @Test
    void severalOneOffs_oneCandidateEach_withStableKeys() {
        List<InsightCandidate> candidates = detector.detectAll(context()
                .forecast(forecast(oneOff(11L, "250.00"), oneOff(12L, "480.00"))).build());

        assertThat(candidates).extracting(InsightCandidate::dedupeKey)
                .containsExactly("transaction:11", "transaction:12");
        assertThat(detector.detectAll(context().forecast(forecast(oneOff(11L, "250.00"), oneOff(12L, "480.00")))
                .build())).isEqualTo(candidates);
    }

    @Test
    void noForecast_noInsight() {
        // forecast-v2 off, not applicable or awaiting salary: the context reports no forecast
        assertThat(detector.detectAll(context().forecast(null).build())).isEmpty();
    }

    private static OneOffDto oneOff(Long id, String impact) {
        return new OneOffDto(id, LocalDate.of(2026, 9, 13), "FX_ONE_OFF_" + id, "misc", money("300.00"),
                money("19.33"), new BigDecimal(impact), new BigDecimal(impact), false);
    }

    private static ForecastV2Dto forecast(OneOffDto... oneOffs) {
        ForecastV2Dto base = September2026Fixture.forecastV2Dto();
        return new ForecastV2Dto(base.status(), base.confidence(), base.discretionaryNow(), base.discretionaryPerDay(),
                base.committed(), base.expectedVariableRemaining(), base.expectedVariableRemainingLow(),
                base.expectedVariableRemainingHigh(), base.expectedEndBalanceTypical(), base.expectedEndBalanceLow(),
                base.expectedEndBalanceHigh(), base.trimmedDailyPace(), base.rawDailyBurnRate(),
                base.historicalTypicalPerDay(), base.historyWeight(), base.baselineCyclesUsed(), base.baselineCycles(),
                List.of(oneOffs), base.committedOccurrences(), base.projectionReason());
    }
}
