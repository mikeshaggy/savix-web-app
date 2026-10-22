package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.forecast.CommittedOccurrenceDto;
import com.mikeshaggy.backend.analytics.forecast.ForecastStatus;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.CommittedOccurrence;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.ForecastInput;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.ForecastV2;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.HistoricalBaseline;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mikeshaggy.backend.insight.detector.TestContexts.SEPTEMBER;
import static com.mikeshaggy.backend.insight.detector.TestContexts.context;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-5: {@code FixedPaymentNotCoverable} is the Stage 4 {@code SHORT} sub-rule, so over every combination the real
 * {@link ForecastV2Calculator} calls {@code SHORT} (with at least one committed occurrence) the detector must report an
 * uncoverable occurrence, and it must stay silent whenever the forecast is {@code TIGHT} or {@code FINE}. The forecast is
 * produced by the production calculator — not hand-written — so a drift between the two rules fails here.
 */
class FixedPaymentNotCoverableAgreesWithForecastStatusTest {

    private static final PayCycle CYCLE = SEPTEMBER; // Sep 9 → Oct 8, next salary expected Oct 9
    private static final List<String> BALANCES = List.of("-500.00", "0.00", "99.99", "100.00", "250.00", "500.00",
            "794.26", "794.27", "900.00", "1200.00", "1500.00", "2400.00", "3000.00", "4500.00", "5896.89", "9000.00");
    private static final List<LocalDate> TODAYS = List.of(
            LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8));
    private static final List<String> DAILY_SPEND = List.of("0.00", "35.50", "120.02", "260.00");
    private static final List<String> P75 = List.of("1000.00", "2400.00", "3400.00");

    private final ForecastV2Calculator calculator = new ForecastV2Calculator();
    private final FixedPaymentNotCoverableDetector detector = new FixedPaymentNotCoverableDetector();

    @Test
    void detectorFiresExactlyWhenTheForecastIsShort_overAGridOfBalancesPaceHistoryAndDays() {
        int combinations = 0;
        int shortCases = 0;
        for (LocalDate today : TODAYS) {
            for (List<Occurrence> occurrences : occurrenceSets(today)) {
                for (String balance : BALANCES) {
                    for (String daily : DAILY_SPEND) {
                        for (String p75 : P75) {
                            for (int cyclesUsed : new int[]{0, 3}) {
                                ForecastV2 forecast = forecast(today, balance, daily, p75, cyclesUsed, occurrences);
                                Optional<InsightCandidate> insight = detector.detect(context().today(today)
                                        .forecast(dto(forecast, occurrences)).build());
                                String label = "today=%s balance=%s daily=%s p75=%s cycles=%d occurrences=%s".formatted(
                                        today, balance, daily, p75, cyclesUsed, occurrences);
                                assertThat(insight.isPresent())
                                        .as(label + " status=" + forecast.status())
                                        .isEqualTo(forecast.status() == ForecastStatus.SHORT);
                                combinations++;
                                if (forecast.status() == ForecastStatus.SHORT) {
                                    shortCases++;
                                }
                            }
                        }
                    }
                }
            }
        }
        // the grid reaches both sides of the rule
        assertThat(shortCases).isGreaterThan(100);
        assertThat(combinations - shortCases).isGreaterThan(100);
    }

    @Test
    void aNegativeDiscretionaryBalance_isReportedAtItsLastOccurrence_asTheForecastCallsItShort() {
        LocalDate today = LocalDate.of(2026, 9, 14);
        List<Occurrence> occurrences = List.of(
                new Occurrence(1L, "Rent", "600.00", LocalDate.of(2026, 9, 20), FixedOccurrenceBucket.DUE_SOON),
                new Occurrence(2L, "Phone", "100.00", LocalDate.of(2026, 10, 2), FixedOccurrenceBucket.LATER_THIS_CYCLE));
        // 650 on hand against 700 committed → discretionaryNow = -50 and no spending assumed at all
        ForecastV2 forecast = forecast(today, "650.00", "0.00", "0.00", 0, occurrences);
        assertThat(forecast.status()).isEqualTo(ForecastStatus.SHORT);

        InsightCandidate candidate = detector.detect(context().today(today).forecast(dto(forecast, occurrences)).build())
                .orElseThrow();

        assertThat(candidate.dedupeKey()).isEqualTo("occurrence:2");
        assertThat(candidate.payload()).containsEntry("shortfall", new BigDecimal("50.00"));
    }

    @Test
    void anOccurrenceDueOnTheSalaryDay_isNotThisCycle() {
        // the committed window ends before the expected payday; a row dated on it is never judged here
        LocalDate today = LocalDate.of(2026, 9, 14);
        ForecastV2 forecast = forecast(today, "10.00", "0.00", "0.00", 0, List.of());
        CommittedOccurrenceDto onPayday = new CommittedOccurrenceDto(9L, "Next cycle", "x", new BigDecimal("500.00"),
                CYCLE.expectedNextAnchor(), FixedOccurrenceBucket.LATER_THIS_CYCLE);
        ForecastV2Dto dto = new ForecastV2Dto(forecast.status(), null, forecast.discretionaryNow(), null,
                forecast.committed(), null, forecast.pessimisticVariableRemaining(), null, null, null, null, null, null,
                null, null, 0, List.of(), List.of(), List.of(onPayday), null);

        assertThat(detector.detect(context().today(today).forecast(dto).build())).isEmpty();
    }

    private ForecastV2 forecast(LocalDate today, String balance, String daily, String p75, int cyclesUsed,
                                List<Occurrence> occurrences) {
        int dayIndex = (int) ChronoUnit.DAYS.between(CYCLE.start(), today) + 1;
        int daysRemaining = (int) ChronoUnit.DAYS.between(today, CYCLE.end());
        int length = (int) ChronoUnit.DAYS.between(CYCLE.start(), CYCLE.end()) + 1;
        List<DailyTotal> series = new ArrayList<>();
        for (int i = 0; i < dayIndex; i++) {
            series.add(new DailyTotal(CYCLE.start().plusDays(i), new BigDecimal(daily)));
        }
        HistoricalBaseline baseline = cyclesUsed == 0
                ? new HistoricalBaseline(0, null, null, null, List.of(), null)
                : new HistoricalBaseline(cyclesUsed, new BigDecimal(p75).multiply(new BigDecimal("0.8")),
                new BigDecimal(p75).multiply(new BigDecimal("0.6")), new BigDecimal(p75), List.of(),
                new BigDecimal("80.00"));
        return calculator.forecast(new ForecastInput(new BigDecimal(balance), today, dayIndex, length, daysRemaining,
                occurrences.stream().map(Occurrence::toCalculatorInput).toList(),
                calculator.currentPace(series, daysRemaining), baseline));
    }

    private static ForecastV2Dto dto(ForecastV2 forecast, List<Occurrence> occurrences) {
        // exactly what ForecastService publishes: only the committed buckets, due-date order
        List<CommittedOccurrenceDto> committed = occurrences.stream()
                .filter(o -> o.toCalculatorInput().committed())
                .sorted(java.util.Comparator.comparing(Occurrence::due))
                .map(Occurrence::toDto)
                .toList();
        return new ForecastV2Dto(forecast.status(), null, forecast.discretionaryNow(), forecast.discretionaryPerDay(),
                forecast.committed(), forecast.expectedVariableRemaining(), forecast.pessimisticVariableRemaining(),
                forecast.optimisticVariableRemaining(), forecast.expectedEndBalanceTypical(),
                forecast.expectedEndBalanceLow(), forecast.expectedEndBalanceHigh(), null, null, null,
                forecast.historyWeight(), forecast.baseline().cyclesUsed(), List.of(), List.of(), committed, null);
    }

    private static List<List<Occurrence>> occurrenceSets(LocalDate today) {
        List<List<Occurrence>> sets = new ArrayList<>();
        // the audited pending set (sum 794.27)
        sets.add(List.of(
                new Occurrence(1L, "A", "76.98", LocalDate.of(2026, 9, 20), FixedOccurrenceBucket.DUE_SOON),
                new Occurrence(2L, "B", "105.00", LocalDate.of(2026, 9, 25), FixedOccurrenceBucket.LATER_THIS_CYCLE),
                new Occurrence(3L, "C", "169.00", LocalDate.of(2026, 10, 1), FixedOccurrenceBucket.LATER_THIS_CYCLE),
                new Occurrence(4L, "D", "34.99", LocalDate.of(2026, 10, 6), FixedOccurrenceBucket.LATER_THIS_CYCLE),
                new Occurrence(5L, "E", "408.30", LocalDate.of(2026, 10, 8), FixedOccurrenceBucket.LATER_THIS_CYCLE)));
        // overdue + due today + later, plus a row after payday and a paid-like bucket the forecast must ignore
        sets.add(List.of(
                new Occurrence(10L, "Overdue", "300.00", today.minusDays(3), FixedOccurrenceBucket.OVERDUE),
                new Occurrence(11L, "Today", "150.00", today, FixedOccurrenceBucket.DUE_SOON),
                new Occurrence(12L, "Later", "220.50", CYCLE.end(), FixedOccurrenceBucket.LATER_THIS_CYCLE),
                new Occurrence(13L, "After payday", "5000.00", CYCLE.expectedNextAnchor().plusDays(2),
                        FixedOccurrenceBucket.AFTER_PAYDAY)));
        // a single large payment on the last day of the cycle
        sets.add(List.of(new Occurrence(20L, "Instalment", "1000.00", CYCLE.end(),
                FixedOccurrenceBucket.LATER_THIS_CYCLE)));
        return sets;
    }

    private record Occurrence(Long id, String title, String amount, LocalDate due, FixedOccurrenceBucket bucket) {

        CommittedOccurrence toCalculatorInput() {
            return new CommittedOccurrence(due, new BigDecimal(amount), bucket);
        }

        CommittedOccurrenceDto toDto() {
            return new CommittedOccurrenceDto(id, title, "fixed", new BigDecimal(amount), due, bucket);
        }
    }
}
