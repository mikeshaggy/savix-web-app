package com.mikeshaggy.backend.analytics.forecast;

import org.springframework.transaction.PlatformTransactionManager;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.regression.September2026Fixture;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

import static com.mikeshaggy.backend.regression.September2026Fixture.PAY_CYCLE_PERIOD_V2;
import static com.mikeshaggy.backend.regression.September2026Fixture.SALARY_WALLET_ID;
import static com.mikeshaggy.backend.regression.September2026Fixture.TODAY;
import static com.mikeshaggy.backend.regression.September2026Fixture.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Stage 4.8: the shadow line, its flag gate and its observational safety. */
@ExtendWith(MockitoExtension.class)
class ForecastShadowObserverTest {

    @Mock
    private ForecastService forecastService;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;
    private Wallet wallet;

    @BeforeEach
    void captureLog() {
        logger = (Logger) LoggerFactory.getLogger(ForecastShadowObserver.class);
        appender.start();
        logger.addAppender(appender);
        wallet = Wallet.builder().id(SALARY_WALLET_ID).balance(September2026Fixture.SALARY_WALLET_BALANCE).build();
        lenient().when(forecastService.isApplicable(any())).thenCallRealMethod();
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private ForecastShadowObserver observer(boolean forecastV2, boolean shadow) {
        return new ForecastShadowObserver(new FeatureFlags(false, forecastV2, shadow, false, false), forecastService,
                mock(PlatformTransactionManager.class));
    }

    @Test
    void shadowIsExactlyShadowOnAndActiveOff() {
        assertThat(observer(false, false).active()).isFalse();
        assertThat(observer(false, true).active()).isTrue();
        assertThat(observer(true, false).active()).isFalse();
        assertThat(observer(true, true).active()).isFalse();
    }

    @Test
    void shadowOnAndActiveOffComputesV2AndLogsExactlyOneStructuredLine() {
        when(forecastService.forecast(wallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, null)).thenReturn(fixtureV2());

        observer(false, true).observe(wallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, null, legacy(), "DANGER");

        List<ILoggingEvent> lines = appender.list;
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().getLevel()).isEqualTo(Level.INFO);
        String line = lines.getFirst().getFormattedMessage();
        assertThat(line).startsWith("forecast.shadow ")
                .contains(" userId=" + USER_ID)
                .contains(" walletId=1")
                .contains(" asOf=2026-09-14")
                .contains(" legacyStatus=DANGER")
                .contains(" legacySafeToSpend=-1105.46")
                .contains(" legacyProjectedEnd=-1105.46")
                .contains(" v2Status=FINE")
                .contains(" v2Discretionary=5102.62")
                .contains(" v2Typical=1232.48")
                .contains(" v2Low=425.42")
                .contains(" v2High=2008.30")
                .contains(" cyclesUsed=3")
                .contains(" wHist=0.8000")
                .contains(" oneOffs=2[300:300.00,301:250.00:x]");
        // privacy: ids and amounts only — never titles, category names or serialised DTOs
        assertThat(line).doesNotContain(September2026Fixture.ONE_OFF_TITLE)
                .doesNotContain(September2026Fixture.ONE_OFF_CATEGORY)
                .doesNotContain("OneOffDto[").doesNotContain("ForecastV2Dto[");
        verify(forecastService, times(1)).forecast(any(), any(), any(), any(), any());
    }

    @Test
    void awaitingSalaryLineCarriesNullVerdictsAndTheReason() {
        ForecastV2Dto awaiting = new ForecastV2Dto(null, null, new BigDecimal("5714.91"), null, new BigDecimal("181.98"),
                null, null, null, null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(),
                SpendingProjectionCalculator.REASON_AWAITING_SALARY);
        when(forecastService.forecast(any(), any(), any(), any(), any())).thenReturn(awaiting);

        observer(false, true).observe(wallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, null, legacyReportingOnly(), null);

        String line = appender.list.getFirst().getFormattedMessage();
        assertThat(line).contains(" legacyStatus=null").contains(" legacySafeToSpend=null")
                .contains(" v2Status=null").contains(" v2Discretionary=5714.91").contains(" cyclesUsed=null")
                .contains(" wHist=null").contains(" oneOffs=0").endsWith(" reason=AWAITING_SALARY");
    }

    @Test
    void bothFlagsOffNeitherComputesNorLogs() {
        observer(false, false).observe(wallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, null, legacy(), "DANGER");

        verify(forecastService, never()).forecast(any(), any(), any(), any(), any());
        assertThat(appender.list).isEmpty();
    }

    @Test
    void activeV2OnNeverShadowLogsEvenWithTheShadowFlagOn() {
        observer(true, true).observe(wallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, null, legacy(), "DANGER");
        observer(true, false).observe(wallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, null, legacy(), "DANGER");

        verify(forecastService, never()).forecast(any(), any(), any(), any(), any());
        assertThat(appender.list).isEmpty();
    }

    @Test
    void nonApplicablePeriodsProduceNoLine() {
        observer(false, true).observe(wallet, USER_ID, September2026Fixture.MONTHLY_PERIOD, TODAY, null,
                legacyReportingOnly(), null);
        observer(false, true).observe(wallet, USER_ID, September2026Fixture.LAST_PAY_CYCLE_PERIOD_V2, TODAY, null,
                legacyReportingOnly(), null);

        verify(forecastService, never()).forecast(any(), any(), any(), any(), any());
        assertThat(appender.list).isEmpty();
    }

    @Test
    void aFailingShadowComputationIsSwallowedAndLoggedAsAWarning() {
        when(forecastService.forecast(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom: baseline query failed"));

        assertThatCode(() -> observer(false, true).observe(wallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, null, legacy(), "DANGER"))
                .doesNotThrowAnyException();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent warning = appender.list.getFirst();
        assertThat(warning.getLevel()).isEqualTo(Level.WARN);
        assertThat(warning.getFormattedMessage()).startsWith("forecast.shadow failed")
                .contains("userId=" + USER_ID).contains("walletId=1").contains("asOf=2026-09-14")
                .contains("periodType=PAY_CYCLE").contains("cycleState=OPEN")
                .contains("boom: baseline query failed");
        assertThat(warning.getThrowableProxy()).isNotNull();
    }

    @Test
    void legacyVerdictFollowsTheDashboardDangerRule() {
        assertThat(ForecastShadowObserver.legacyVerdict(legacy())).isEqualTo("DANGER");
        assertThat(ForecastShadowObserver.legacyVerdict(legacy().withForecast(null))).isEqualTo("DANGER");
        SpendingProjectionDto fine = new SpendingProjectionDto(PeriodType.PAY_CYCLE, "Current pay cycle",
                September2026Fixture.CURRENT_CYCLE_START, September2026Fixture.LEGACY_CURRENT_CYCLE_END, 30, 6, 24,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"),
                BigDecimal.ZERO, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, true, null, null);
        assertThat(ForecastShadowObserver.legacyVerdict(fine)).isEqualTo("ON_TRACK");
        assertThat(ForecastShadowObserver.legacyVerdict(legacyReportingOnly())).isNull();
        assertThat(ForecastShadowObserver.legacyVerdict(null)).isNull();
    }

    // --- fixtures -----------------------------------------------------------------------------------

    static ForecastV2Dto fixtureV2() {
        return September2026Fixture.forecastV2Dto();
    }

    /** The audited legacy numbers on Sep 14: safe-to-spend −1105.46, projected end −1105.46 → DANGER. */
    static SpendingProjectionDto legacy() {
        return new SpendingProjectionDto(PeriodType.PAY_CYCLE, "Current pay cycle",
                September2026Fixture.CURRENT_CYCLE_START, September2026Fixture.LEGACY_CURRENT_CYCLE_END, 30, 6, 24,
                September2026Fixture.INCOME_FOR_PERIOD, September2026Fixture.INCOME_FOR_PERIOD,
                September2026Fixture.EXPENSES_TO_DATE, new BigDecimal("258.67"), new BigDecimal("10550.27"),
                September2026Fixture.LEGACY_PAY_CYCLE_PROJECTED_END, September2026Fixture.REMAINING_FIXED_PAY_CYCLE,
                September2026Fixture.LEGACY_PAY_CYCLE_SAFE_TO_SPEND, September2026Fixture.LEGACY_PAY_CYCLE_SAFE_PER_DAY,
                September2026Fixture.VARIABLE_EXPENSES_TO_DATE, September2026Fixture.LINKED_FIXED_EXPENSES_TO_DATE,
                new BigDecimal("258.67"), new BigDecimal("6208.08"), true, null, null);
    }

    static SpendingProjectionDto legacyReportingOnly() {
        return new SpendingProjectionDto(PeriodType.PAY_CYCLE, "Current pay cycle",
                September2026Fixture.CURRENT_CYCLE_START, September2026Fixture.LEGACY_CURRENT_CYCLE_END, 30, 6, 24,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, BigDecimal.ZERO, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, false,
                SpendingProjectionCalculator.REASON_AWAITING_SALARY, null);
    }
}
