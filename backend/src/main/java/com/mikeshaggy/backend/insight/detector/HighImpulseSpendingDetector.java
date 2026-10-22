package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.insight.detector.InsightContext.ImpulseSplit;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.IMPULSE_MIN_AMOUNT;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.IMPULSE_SHARE_PERCENT;
import static com.mikeshaggy.backend.insight.detector.InsightFormat.pln;

/**
 * Impulse spending is both large and a large share of this cycle's variable spending (the legacy rule, kept with an
 * absolute floor — decision I5):
 * <pre>shouldntHave &gt; 300   and   shouldntHave × 100 &gt; 20 × eligibleVariable</pre>
 * Both over the same set: pace-eligible variable EXPENSE transactions (unlinked, not excluded) from the cycle start
 * through today; {@code shouldntHave} is the part classified {@code SHOULDNT_HAVE}. Transfers are never transactions,
 * so never spending. Once per cycle.
 */
@Component
public class HighImpulseSpendingDetector implements InsightDetector {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public InsightType type() {
        return InsightType.HIGH_IMPULSE_SPENDING;
    }

    @Override
    public Optional<InsightCandidate> detect(InsightContext ctx) {
        ImpulseSplit split = ctx.impulseSplit();
        if (split == null || split.eligibleVariable() == null || split.shouldntHave() == null) {
            return Optional.empty();
        }
        BigDecimal impulse = money(split.shouldntHave());
        BigDecimal eligible = money(split.eligibleVariable());
        if (impulse.compareTo(IMPULSE_MIN_AMOUNT) <= 0
                || impulse.multiply(HUNDRED).compareTo(IMPULSE_SHARE_PERCENT.multiply(eligible)) <= 0) {
            return Optional.empty();
        }
        // eligible ≥ impulse > 300, so the share is never on a small base
        BigDecimal share = InsightFormat.percentOf(impulse, eligible);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shouldntHave", impulse);
        payload.put("eligibleVariable", eligible);
        payload.put("sharePercent", share);
        payload.put("from", ctx.cycle().start().toString());
        payload.put("through", ctx.today().toString());
        return Optional.of(new InsightCandidate(
                type(),
                InsightSeverity.WARN,
                "impulse",
                "High impulse spending",
                "%s marked shouldn't-have — %s of variable spending this cycle."
                        .formatted(pln(impulse), InsightFormat.wholePercent(share)),
                payload,
                InsightActionType.LINK,
                "/analytics/breakdown"));
    }
}
