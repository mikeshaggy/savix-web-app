package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.forecast.CommittedOccurrenceDto;
import com.mikeshaggy.backend.analytics.forecast.ForecastStatus;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.mikeshaggy.backend.insight.detector.TestContexts.USER;
import static com.mikeshaggy.backend.insight.detector.TestContexts.WALLET;
import static com.mikeshaggy.backend.insight.detector.TestContexts.context;
import static com.mikeshaggy.backend.insight.detector.TestContexts.money;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 7.3 {@code FixedPaymentNotCoverable}: the Stage 4 SHORT walk at each occurrence's own due date (low path =
 * pessimistic variable spend prorated to the due date), never an end-of-cycle balance.
 */
class FixedPaymentNotCoverableDetectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14); // R = 24 (cycle ends Oct 8)
    /** The audited pending occurrences (sum 794.27). */
    private static final List<CommittedOccurrenceDto> PENDING = List.of(
            row(1L, "FX_UTILITY_A", "76.98", "2026-09-20", FixedOccurrenceBucket.DUE_SOON),
            row(2L, "FX_SUB_A", "105.00", "2026-09-25", FixedOccurrenceBucket.LATER_THIS_CYCLE),
            row(3L, "FX_MEMBERSHIP", "169.00", "2026-10-01", FixedOccurrenceBucket.LATER_THIS_CYCLE),
            row(4L, "FX_SUB_B", "34.99", "2026-10-06", FixedOccurrenceBucket.LATER_THIS_CYCLE),
            row(5L, "FX_INSTALMENT", "408.30", "2026-10-08", FixedOccurrenceBucket.LATER_THIS_CYCLE));
    private static final BigDecimal PESSIMISTIC = money("4677.20");

    private final FixedPaymentNotCoverableDetector detector = new FixedPaymentNotCoverableDetector();

    @Test
    void auditedFixture_everyOccurrenceIsCoverable_noInsight() {
        // balance 5,896.89: the tightest step is FX_INSTALMENT with 833.72 left on Oct 8
        assertThat(detector.detect(context().forecast(forecast("5896.89", PENDING, ForecastStatus.FINE)).build()))
                .isEmpty();
    }

    @Test
    void lowPathRunsOutBeforeOct6_firstUncoverableOccurrence_isReported() {
        // balance 4,500.00: Oct 1 leaves 1,005.00 (ok); Oct 6 leaves 4500 − 350.98 − 4287.43 = −138.41 < 34.99
        InsightCandidate candidate = detector.detect(context()
                .forecast(forecast("4500.00", PENDING, ForecastStatus.SHORT)).build()).orElseThrow();

        assertThat(candidate.severity()).isEqualTo(InsightSeverity.ALERT);
        assertThat(candidate.dedupeKey()).isEqualTo("occurrence:4");
        assertThat(candidate.actionType()).isEqualTo(InsightActionType.LINK);
        assertThat(candidate.actionRef()).isEqualTo("/transactions/fixed-payments");
        assertThat(candidate.payload())
                .containsEntry("dueDate", "2026-10-06")
                .containsEntry("amount", money("34.99"))
                .containsEntry("availableOnDueDate", money("-138.41"))
                .containsEntry("shortfall", money("173.40"))
                .containsEntry("uncoverableCount", 2);
    }

    @Test
    void theDueDateTrajectoryDecides_notTheEndOfCycleBalance() {
        // 1,000 now, 900 due tomorrow: the whole low path (2,400 over 24 days) is far beyond the balance at the end,
        // yet on the due date only 100 of it is spent → 1,000 − 100 = 900 ≥ 900, coverable
        List<CommittedOccurrenceDto> tomorrow = List.of(
                row(7L, "FX_RENT", "900.00", "2026-09-15", FixedOccurrenceBucket.DUE_SOON));
        assertThat(detector.detect(context().forecast(forecast("1000.00", tomorrow, money("2400.00"),
                ForecastStatus.SHORT)).build())).isEmpty();
        assertThat(detector.detect(context().forecast(forecast("999.99", tomorrow, money("2400.00"),
                ForecastStatus.SHORT)).build())).isPresent();
    }

    @Test
    void amountEqualToTheAvailableBalance_isCoverable() {
        List<CommittedOccurrenceDto> dueToday = List.of(
                row(8L, "FX_DUE_TODAY", "100.00", "2026-09-14", FixedOccurrenceBucket.DUE_SOON));
        assertThat(detector.detect(context().forecast(forecast("100.00", dueToday, ForecastStatus.FINE)).build()))
                .isEmpty();
        assertThat(detector.detect(context().forecast(forecast("99.99", dueToday, ForecastStatus.SHORT)).build()))
                .hasValueSatisfying(c -> assertThat(c.payload()).containsEntry("shortfall", money("0.01")));
    }

    @Test
    void noForecast_awaitingShell_orNotOpen_noInsight() {
        assertThat(detector.detect(context().forecast(null).build())).isEmpty();
        // awaiting-salary shell: status and pessimistic path are null
        ForecastV2Dto shell = new ForecastV2Dto(null, null, money("100.00"), null, money("794.27"), null, null, null,
                null, null, null, null, null, null, null, null, List.of(), List.of(), PENDING, "AWAITING_SALARY");
        PayCycle awaiting = PayCycle.awaitingSalary(USER, WALLET, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9));
        assertThat(detector.detect(context().cycle(awaiting).today(LocalDate.of(2026, 10, 12)).forecast(shell)
                .build())).isEmpty();
        assertThat(detector.detect(context().today(TODAY).forecast(shell).build())).isEmpty();
    }

    @Test
    void noCommittedOccurrence_noInsight() {
        assertThat(detector.detect(context().forecast(forecast("-50.00", List.of(), ForecastStatus.SHORT)).build()))
                .isEmpty();
    }

    private static ForecastV2Dto forecast(String balance, List<CommittedOccurrenceDto> rows, ForecastStatus status) {
        return forecast(balance, rows, PESSIMISTIC, status);
    }

    private static ForecastV2Dto forecast(String balance, List<CommittedOccurrenceDto> rows, BigDecimal pessimistic,
                                          ForecastStatus status) {
        BigDecimal committed = rows.stream().map(CommittedOccurrenceDto::expectedAmount)
                .reduce(money("0.00"), BigDecimal::add);
        BigDecimal discretionary = money(balance).subtract(committed);
        return new ForecastV2Dto(status, null, discretionary, null, committed, null, pessimistic, null, null, null,
                null, null, null, null, null, 6, List.of(), List.of(), rows, null);
    }

    private static CommittedOccurrenceDto row(Long id, String title, String amount, String due,
                                              FixedOccurrenceBucket bucket) {
        return new CommittedOccurrenceDto(id, title, "fixed", money(amount), LocalDate.parse(due), bucket);
    }
}
