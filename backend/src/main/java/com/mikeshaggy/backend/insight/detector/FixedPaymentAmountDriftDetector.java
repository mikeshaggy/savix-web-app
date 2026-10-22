package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.insight.detector.InsightContext.FixedPaymentHistory;
import com.mikeshaggy.backend.insight.detector.InsightContext.OccurrenceSnapshot;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.DRIFT_MIN_DIFFERENCE;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.DRIFT_OCCURRENCES;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.DRIFT_SHARE_PERCENT;
import static com.mikeshaggy.backend.insight.detector.InsightFormat.pln;

/**
 * A fixed payment is paid at a different amount than configured, twice in a row: the two most recent occurrences that
 * are paid or already due (by due date) are both {@code PAID} with a recorded {@code paidAmount} (the linked
 * transaction's amount, Stage 3.3) and, for each,
 * <pre>|paidAmount − amount| × 100 &gt; 5 × amount   and   |paidAmount − amount| ≥ 10 PLN</pre>
 * exactly in {@code BigDecimal}. An unpaid, overdue or skipped occurrence between them breaks the run. Comparing with the
 * <em>configured</em> amount means editing it to the new price ends the insight. One insight per fixed payment.
 */
@Component
public class FixedPaymentAmountDriftDetector extends MultiInsightDetector {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public InsightType type() {
        return InsightType.FIXED_PAYMENT_AMOUNT_DRIFT;
    }

    @Override
    public List<InsightCandidate> detectAll(InsightContext ctx) {
        List<InsightCandidate> candidates = new ArrayList<>();
        for (FixedPaymentHistory payment : ctx.fixedPayments()) {
            if (payment.amount() == null || payment.amount().signum() <= 0) {
                continue;
            }
            List<OccurrenceSnapshot> latest = latestSettledOrDue(payment.occurrences(), ctx.today());
            if (latest.size() == DRIFT_OCCURRENCES
                    && latest.stream().allMatch(occurrence -> drifted(occurrence, payment.amount()))) {
                candidates.add(candidate(payment, latest));
            }
        }
        return candidates;
    }

    /** The newest occurrences that are paid (even ahead of their due date) or due by today, newest first. */
    private static List<OccurrenceSnapshot> latestSettledOrDue(List<OccurrenceSnapshot> occurrences, LocalDate today) {
        return occurrences.stream()
                .filter(occurrence -> occurrence.status() == OccurrenceStatus.PAID
                        || !occurrence.dueDate().isAfter(today))
                .sorted(Comparator.comparing(OccurrenceSnapshot::dueDate).reversed()
                        .thenComparing(OccurrenceSnapshot::id, Comparator.reverseOrder()))
                .limit(DRIFT_OCCURRENCES)
                .toList();
    }

    private static boolean drifted(OccurrenceSnapshot occurrence, BigDecimal configured) {
        if (occurrence.status() != OccurrenceStatus.PAID || occurrence.paidAmount() == null) {
            return false;
        }
        BigDecimal difference = money(occurrence.paidAmount()).subtract(money(configured)).abs();
        return difference.multiply(HUNDRED).compareTo(DRIFT_SHARE_PERCENT.multiply(money(configured))) > 0
                && difference.compareTo(DRIFT_MIN_DIFFERENCE) >= 0;
    }

    private InsightCandidate candidate(FixedPaymentHistory payment, List<OccurrenceSnapshot> latest) {
        BigDecimal configured = money(payment.amount());
        OccurrenceSnapshot newest = latest.getFirst();
        OccurrenceSnapshot previous = latest.get(1);
        BigDecimal newestDifference = money(newest.paidAmount()).subtract(configured);
        // a percentage only against a configured amount of at least 100 PLN (decision I2)
        BigDecimal differencePercent = InsightFormat.percentOf(newestDifference, configured);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (OccurrenceSnapshot occurrence : latest) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("occurrenceId", occurrence.id());
            row.put("dueDate", occurrence.dueDate().toString());
            row.put("paidAmount", money(occurrence.paidAmount()));
            row.put("difference", money(occurrence.paidAmount()).subtract(configured));
            rows.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fixedPaymentId", payment.id());
        payload.put("title", payment.title());
        payload.put("configuredAmount", configured);
        payload.put("suggestedAmount", money(newest.paidAmount()));
        payload.put("differencePercent", differencePercent);
        payload.put("occurrences", rows);

        String change = differencePercent == null ? "" : " (%s%s)".formatted(
                newestDifference.signum() > 0 ? "+" : "", InsightFormat.wholePercent(differencePercent));
        return new InsightCandidate(
                type(),
                InsightSeverity.INFO,
                "fixed-payment:" + payment.id(),
                InsightFormat.clip(payment.title(), 120) + " is paid at a different amount",
                "Paid %s and %s on the last two due dates vs %s configured%s."
                        .formatted(pln(newest.paidAmount()), pln(previous.paidAmount()), pln(configured), change),
                payload,
                InsightActionType.LINK,
                "/transactions/fixed-payments?edit=" + payment.id());
    }
}
