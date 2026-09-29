package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stage 4.8 — shadow mode. While {@code app.features.forecast-v2-shadow} is on and {@code forecast-v2} is
 * <em>off</em>, every dashboard / projection request additionally computes Forecast v2 server-side and writes
 * one structured INFO line, {@code forecast.shadow …}, next to the legacy numbers it served. The payload never
 * carries v2; the frontend never learns the flag. With {@code forecast-v2} on the active result is
 * authoritative and nothing is shadow-logged.
 *
 * <p>Observational by contract: a failure inside the v2 computation is caught, logged at WARN with the request
 * context, and never changes the legacy response. The computation runs in its <em>own</em> read-only transaction
 * ({@code REQUIRES_NEW}): the callers are already inside a read-only transaction, and a participating
 * {@code @Transactional} bean (the orchestrator, the query services, the repositories) that throws would mark that
 * shared transaction rollback-only before this class could catch anything — the legacy request would then fail at
 * commit with {@code UnexpectedRollbackException}. Isolating the shadow work costs one extra pooled connection per
 * shadowed request while the flag is on. The line is privacy-safe — no transaction titles, notes,
 * category names or serialised objects; one-offs appear as a count plus {@code id:amount[:x]} pairs
 * ({@code x} = already excluded from the pace).
 *
 * <p>One line per request: the projection endpoint path emits it from {@code SpendingProjectionService}; the
 * dashboard emits it itself after deriving its legacy verdict, so {@code legacyStatus} there is the real
 * {@code DashboardHealthStatus} (ON_TRACK / WARNING / DANGER). The projection page shows no verdict, so its
 * lines carry {@link #legacyVerdict} — the same DANGER rule without the comparison-based WARNING.
 */
@Component
public class ForecastShadowObserver {

    static final String SHADOW_MARKER = "forecast.shadow";

    private static final Logger log = LoggerFactory.getLogger(ForecastShadowObserver.class);

    private final FeatureFlags featureFlags;
    private final ForecastService forecastService;
    private final TransactionTemplate shadowTransaction;

    public ForecastShadowObserver(FeatureFlags featureFlags, ForecastService forecastService,
                                 PlatformTransactionManager transactionManager) {
        this.featureFlags = featureFlags;
        this.forecastService = forecastService;
        this.shadowTransaction = new TransactionTemplate(transactionManager);
        this.shadowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.shadowTransaction.setReadOnly(true);
    }

    /** Shadow mode is exactly {@code forecast-v2-shadow && !forecast-v2}. */
    public boolean active() {
        return featureFlags.forecastV2Shadow() && !featureFlags.forecastV2();
    }

    /**
     * Computes v2 for {@code period} and logs the comparison — a no-op unless {@link #active()} and the period
     * is applicable. Never throws.
     *
     * @param tile         the Stage 3 tile the caller already holds for this request, or {@code null}
     * @param legacy       the legacy projection served to the client
     * @param legacyStatus the legacy verdict the caller shows for it ({@code null} when it shows none)
     */
    public void observe(Wallet wallet, UUID userId, PeriodDto period, LocalDate asOfDate,
                        FixedTransactionsTileDto tile, SpendingProjectionDto legacy, String legacyStatus) {
        if (!active() || !forecastService.isApplicable(period)) {
            return;
        }
        try {
            // own transaction: a failure below must not poison the caller's (see class comment)
            ForecastV2Dto v2 = shadowTransaction.execute(
                    status -> forecastService.forecast(wallet, userId, period, asOfDate, tile));
            if (v2 == null) {
                return;
            }
            log.info(comparisonLine(userId, wallet.getId(), asOfDate, legacy, legacyStatus, v2));
        } catch (RuntimeException e) {
            log.warn("{} failed userId={} walletId={} asOf={} periodType={} cycleState={} start={} end={}: {}",
                    SHADOW_MARKER, userId, wallet.getId(), asOfDate, period.periodType(), period.cycleState(),
                    period.startDate(), period.endDate(), e.toString(), e);
        }
    }

    /**
     * The legacy DANGER rule of the dashboard applied to a projection alone: {@code DANGER} when safe-to-spend or
     * the projected end balance is negative, {@code ON_TRACK} otherwise, {@code null} without a projection. The
     * dashboard's WARNING needs the previous-cycle pace delta and is not reproduced here.
     */
    public static String legacyVerdict(SpendingProjectionDto legacy) {
        if (legacy == null || !legacy.projectionAvailable()
                || legacy.safeToSpendToday() == null || legacy.projectedEndBalance() == null) {
            return null;
        }
        boolean danger = legacy.safeToSpendToday().signum() < 0 || legacy.projectedEndBalance().signum() < 0;
        return danger ? "DANGER" : "ON_TRACK";
    }

    static String comparisonLine(UUID userId, Integer walletId, LocalDate asOf, SpendingProjectionDto legacy,
                                 String legacyStatus, ForecastV2Dto v2) {
        return SHADOW_MARKER
                + " userId=" + userId
                + " walletId=" + walletId
                + " asOf=" + asOf
                + " legacyStatus=" + legacyStatus
                + " legacySafeToSpend=" + plain(legacy == null ? null : legacy.safeToSpendToday())
                + " legacyProjectedEnd=" + plain(legacy == null ? null : legacy.projectedEndBalance())
                + " v2Status=" + v2.status()
                + " v2Discretionary=" + plain(v2.discretionaryNow())
                + " v2Typical=" + plain(v2.expectedEndBalanceTypical())
                + " v2Low=" + plain(v2.expectedEndBalanceLow())
                + " v2High=" + plain(v2.expectedEndBalanceHigh())
                + " cyclesUsed=" + v2.baselineCyclesUsed()
                + " wHist=" + plain(v2.historyWeight())
                + " oneOffs=" + oneOffs(v2.oneOffs())
                + (v2.projectionReason() == null ? "" : " reason=" + v2.projectionReason());
    }

    /** {@code count[id:amount[:x],…]} — ids and amounts only, in the detector's order. */
    private static String oneOffs(List<OneOffDto> oneOffs) {
        if (oneOffs == null || oneOffs.isEmpty()) {
            return "0";
        }
        return oneOffs.size() + oneOffs.stream()
                .map(o -> o.transactionId() + ":" + plain(o.amount()) + (o.excluded() ? ":x" : ""))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String plain(BigDecimal value) {
        return value == null ? "null" : value.toPlainString();
    }
}
