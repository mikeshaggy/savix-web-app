package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.UnlinkedExpense;
import com.mikeshaggy.backend.insight.detector.InsightContext.FixedPaymentHistory;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.RECURRING_AMOUNT_TOLERANCE;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.RECURRING_MAX_AGE_DAYS;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.RECURRING_MAX_GAP_DAYS;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.RECURRING_MIN_AMOUNT;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.RECURRING_MIN_GAP_DAYS;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.RECURRING_MIN_OCCURRENCES;
import static com.mikeshaggy.backend.insight.detector.InsightFormat.pln;

/**
 * A monthly charge that is not set up as a fixed payment: among the unlinked expenses of the last 210 days (an expense
 * paying a fixed-payment occurrence is linked and never seen here), per normalised title, the latest run of
 * consecutive expenses where
 * <ul>
 *   <li>every gap between neighbours is 25–35 days (inclusive) — any expense of that title in between breaks the run,
 *       so a weekly shop never qualifies;</li>
 *   <li>every amount is within ±10 % of the latest amount;</li>
 *   <li>there are at least 3 of them, the latest at most 35 days ago and at least 10 PLN;</li>
 *   <li>no active fixed payment of the wallet already has that normalised title.</li>
 * </ul>
 * Normalised title = NFKC, lower case, whitespace collapsed, trimmed. Action {@code CREATE_FIXED_PAYMENT} with the
 * prefill in the payload; keyed by the normalised title (a dismissed merchant stays dismissed: UNTIL_DISMISSED).
 */
@Component
public class RecurringMerchantDetectedDetector extends MultiInsightDetector {

    static final int MAX_PLAIN_KEY_TITLE = 100;
    static final int HASHED_KEY_PREFIX = 80;

    @Override
    public InsightType type() {
        return InsightType.RECURRING_MERCHANT_DETECTED;
    }

    @Override
    public List<InsightCandidate> detectAll(InsightContext ctx) {
        Set<String> fixedTitles = ctx.fixedPayments().stream()
                .map(FixedPaymentHistory::title)
                .map(RecurringMerchantDetectedDetector::normalise)
                .filter(title -> !title.isEmpty())
                .collect(Collectors.toSet());

        Map<String, List<UnlinkedExpense>> byTitle = new TreeMap<>();
        for (UnlinkedExpense expense : ctx.expenseHistory()) {
            String title = normalise(expense.title());
            if (title.isEmpty() || expense.date() == null || expense.amount() == null
                    || expense.date().isAfter(ctx.today())) {
                continue;
            }
            byTitle.computeIfAbsent(title, key -> new ArrayList<>()).add(expense);
        }

        List<InsightCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<UnlinkedExpense>> entry : byTitle.entrySet()) {
            if (fixedTitles.contains(entry.getKey())) {
                continue;
            }
            List<UnlinkedExpense> run = latestRun(entry.getValue());
            if (run.size() < RECURRING_MIN_OCCURRENCES) {
                continue;
            }
            UnlinkedExpense latest = run.getLast();
            if (ChronoUnit.DAYS.between(latest.date(), ctx.today()) > RECURRING_MAX_AGE_DAYS
                    || money(latest.amount()).compareTo(RECURRING_MIN_AMOUNT) < 0) {
                continue;
            }
            candidates.add(candidate(ctx, entry.getKey(), run));
        }
        return candidates;
    }

    /** Walks back from the newest expense while the gap and amount rules hold; oldest first. */
    static List<UnlinkedExpense> latestRun(List<UnlinkedExpense> expenses) {
        List<UnlinkedExpense> sorted = expenses.stream()
                .sorted(Comparator.comparing(UnlinkedExpense::date).thenComparing(UnlinkedExpense::transactionId))
                .toList();
        if (sorted.isEmpty()) {
            return List.of();
        }
        UnlinkedExpense latest = sorted.getLast();
        BigDecimal reference = money(latest.amount());
        BigDecimal tolerance = reference.multiply(RECURRING_AMOUNT_TOLERANCE);
        List<UnlinkedExpense> run = new ArrayList<>();
        run.add(latest);
        for (int i = sorted.size() - 2; i >= 0; i--) {
            UnlinkedExpense earlier = sorted.get(i);
            long gap = ChronoUnit.DAYS.between(earlier.date(), run.getFirst().date());
            if (gap < RECURRING_MIN_GAP_DAYS || gap > RECURRING_MAX_GAP_DAYS
                    || money(earlier.amount()).subtract(reference).abs().compareTo(tolerance) > 0) {
                break;
            }
            run.addFirst(earlier);
        }
        return List.copyOf(run);
    }

    static String normalise(String title) {
        if (title == null) {
            return "";
        }
        return Normalizer.normalize(title, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }

    /** {@code merchant:<title>}; a very long title is cut and suffixed with a hash so the key stays unique and ≤ 120. */
    static String dedupeKey(String normalisedTitle) {
        if (normalisedTitle.length() <= MAX_PLAIN_KEY_TITLE) {
            return "merchant:" + normalisedTitle;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalisedTitle.getBytes(StandardCharsets.UTF_8));
            return "merchant:" + normalisedTitle.substring(0, HASHED_KEY_PREFIX) + "#"
                    + HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private InsightCandidate candidate(InsightContext ctx, String normalisedTitle, List<UnlinkedExpense> run) {
        UnlinkedExpense latest = run.getLast();
        BigDecimal amount = money(latest.amount());
        LocalDate nextDue = latest.date().plusMonths(1);

        List<Map<String, Object>> occurrences = new ArrayList<>();
        for (UnlinkedExpense expense : run) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("transactionId", expense.transactionId());
            row.put("date", expense.date().toString());
            row.put("amount", money(expense.amount()));
            occurrences.add(row);
        }
        Map<String, Object> prefill = new LinkedHashMap<>();
        prefill.put("title", latest.title());
        prefill.put("amount", amount);
        prefill.put("categoryId", latest.categoryId());
        prefill.put("categoryName", latest.categoryName());
        prefill.put("walletId", ctx.wallet().id());
        prefill.put("cycle", "MONTHLY");
        prefill.put("anchorDate", nextDue.toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("normalisedTitle", normalisedTitle);
        payload.put("occurrences", occurrences);
        payload.put("prefill", prefill);
        return new InsightCandidate(
                type(),
                InsightSeverity.INFO,
                dedupeKey(normalisedTitle),
                InsightFormat.clip(latest.title(), 120) + " looks like a monthly payment",
                "%d payments of about %s roughly monthly, the latest on %s. Add it as a fixed payment?"
                        .formatted(run.size(), pln(amount), latest.date()),
                payload,
                InsightActionType.CREATE_FIXED_PAYMENT,
                null);
    }
}
