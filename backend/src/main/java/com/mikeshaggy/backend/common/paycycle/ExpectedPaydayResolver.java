package com.mikeshaggy.backend.common.paycycle;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Predicts the next salary anchor after {@code lastAnchor}. Pure function over its inputs — the caller
 * (PayCycleService) loads the anchor history and the configured rule; nothing is read from repositories here.
 * <p>
 * Rule order (architectural decision T3):
 * <ol>
 *   <li>configured rule (day-of-month + weekend shift);</li>
 *   <li>learned rule: median day-of-month over the last {@value #LEARNING_WINDOW} anchors, shifted to the
 *       previous business day when at least one of those anchors fell on the last business day before a
 *       weekend day-of-month (needs at least {@value #MIN_ANCHORS_TO_LEARN} anchors);</li>
 *   <li>{@code lastAnchor + median(historical cycle length)} when at least two anchors are known;</li>
 *   <li>{@code lastAnchor + 1 month}.</li>
 * </ol>
 * A learned candidate is the first occurrence of the day-of-month strictly after
 * {@code lastAnchor + MIN_CYCLE_DAYS}, so a rule can never predict a cycle shorter than the anchor-merging window.
 */
@Component
public class ExpectedPaydayResolver {

    /** Anchor-merging window (decision T8): two anchors closer than this belong to one cycle. */
    public static final int MIN_CYCLE_DAYS = 20;

    /** Number of most recent anchors the learned rule looks at. */
    static final int LEARNING_WINDOW = 6;

    /** With fewer anchors the day-of-month is not learned; the median-length / +1 month fallbacks apply. */
    static final int MIN_ANCHORS_TO_LEARN = 3;

    /**
     * @param userId            owner of the anchors (kept on the API for the service; not used by the rules)
     * @param lastAnchor        the most recent actual salary anchor (start of the open cycle)
     * @param anchorHistoryDesc all known anchors, newest first, including {@code lastAnchor}
     */
    public ExpectedPayday resolve(UUID userId, LocalDate lastAnchor, List<LocalDate> anchorHistoryDesc) {
        return resolve(userId, lastAnchor, anchorHistoryDesc, null);
    }

    /**
     * @param configuredRule the user's configured payday rule, or {@code null} when none is configured
     */
    public ExpectedPayday resolve(UUID userId, LocalDate lastAnchor, List<LocalDate> anchorHistoryDesc,
                                  PaydayRule configuredRule) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(lastAnchor, "lastAnchor");
        List<LocalDate> history = normalise(lastAnchor, anchorHistoryDesc);

        if (configuredRule != null) {
            PaydayRule rule = new PaydayRule(configuredRule.dayOfMonth(), configuredRule.weekendShift(),
                    PaydayRuleSource.CONFIGURED);
            return new ExpectedPayday(nextOccurrence(rule, lastAnchor), rule);
        }

        if (history.size() >= MIN_ANCHORS_TO_LEARN) {
            PaydayRule rule = learn(history.subList(0, Math.min(LEARNING_WINDOW, history.size())));
            return new ExpectedPayday(nextOccurrence(rule, lastAnchor), rule);
        }

        if (history.size() >= 2) {
            int medianLength = medianCycleLength(history);
            LocalDate date = lastAnchor.plusDays(medianLength);
            return new ExpectedPayday(date, new PaydayRule(date.getDayOfMonth(), WeekendShift.NONE,
                    PaydayRuleSource.MEDIAN_LENGTH));
        }

        LocalDate date = lastAnchor.plusMonths(1);
        return new ExpectedPayday(date, new PaydayRule(date.getDayOfMonth(), WeekendShift.NONE,
                PaydayRuleSource.PLUS_ONE_MONTH));
    }

    /** Newest-first, distinct, never later than {@code lastAnchor}, and always containing {@code lastAnchor}. */
    private static List<LocalDate> normalise(LocalDate lastAnchor, List<LocalDate> anchorHistoryDesc) {
        List<LocalDate> history = new ArrayList<>();
        history.add(lastAnchor);
        if (anchorHistoryDesc != null) {
            anchorHistoryDesc.stream()
                    .filter(Objects::nonNull)
                    .filter(date -> date.isBefore(lastAnchor))
                    .distinct()
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(history::add);
        }
        return history;
    }

    private static PaydayRule learn(List<LocalDate> recentAnchorsDesc) {
        int dayOfMonth = median(recentAnchorsDesc.stream().mapToInt(LocalDate::getDayOfMonth).toArray());
        WeekendShift shift = showsPreviousBusinessDayShift(recentAnchorsDesc, dayOfMonth)
                ? WeekendShift.PREVIOUS_BUSINESS_DAY
                : WeekendShift.NONE;
        return new PaydayRule(dayOfMonth, shift, PaydayRuleSource.LEARNED);
    }

    /**
     * True when at least one anchor is the last business day before a weekend occurrence of {@code dayOfMonth}
     * — e.g. paid Fri Jan 9 because Jan 10 was a Saturday. The nominal date is looked up in the anchor's own
     * month and in the following one (an anchor at month end may precede a weekend 1st).
     */
    private static boolean showsPreviousBusinessDayShift(List<LocalDate> anchors, int dayOfMonth) {
        for (LocalDate anchor : anchors) {
            YearMonth month = YearMonth.from(anchor);
            for (YearMonth candidateMonth : List.of(month, month.plusMonths(1))) {
                LocalDate nominal = candidateMonth.atDay(Math.min(dayOfMonth, candidateMonth.lengthOfMonth()));
                if (WeekendShift.isWeekend(nominal) && anchor.equals(WeekendShift.previousBusinessDay(nominal))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** First occurrence of the rule's payday strictly after {@code lastAnchor + MIN_CYCLE_DAYS}. */
    private static LocalDate nextOccurrence(PaydayRule rule, LocalDate lastAnchor) {
        LocalDate earliest = lastAnchor.plusDays(MIN_CYCLE_DAYS);
        YearMonth month = YearMonth.from(earliest);
        LocalDate nominal = rule.nominalDateIn(month);
        while (!nominal.isAfter(earliest)) {
            month = month.plusMonths(1);
            nominal = rule.nominalDateIn(month);
        }
        return rule.weekendShift().apply(nominal);
    }

    private static int medianCycleLength(List<LocalDate> anchorsDesc) {
        int[] lengths = new int[anchorsDesc.size() - 1];
        for (int i = 0; i < lengths.length; i++) {
            lengths[i] = (int) (anchorsDesc.get(i).toEpochDay() - anchorsDesc.get(i + 1).toEpochDay());
        }
        return median(lengths);
    }

    /** Median of the values; the mean of the two middle values (rounded half up) for an even count. */
    private static int median(int[] values) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n % 2 == 1) {
            return sorted[n / 2];
        }
        return (sorted[n / 2 - 1] + sorted[n / 2] + 1) / 2;
    }
}
