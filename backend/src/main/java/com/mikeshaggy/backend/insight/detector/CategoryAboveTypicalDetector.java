package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.forecast.HistoricalCategorySpendService.CategoryAtDay;
import com.mikeshaggy.backend.analytics.forecast.HistoricalCategorySpendService.CategoryDayBaseline;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.CATEGORY_EXCESS_SHARE;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.CATEGORY_MIN_BASELINE;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.CATEGORY_MIN_CYCLES;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.CATEGORY_MIN_EXCESS;
import static com.mikeshaggy.backend.insight.detector.InsightFormat.pln;
import static com.mikeshaggy.backend.insight.detector.InsightFormat.wholePercent;

/**
 * A category's pace-eligible spend at today's cycle day {@code d} is well above its typical level for that day:
 * <pre>current_d &gt; median_d + max(100, 0.5 × median_d)   with median_d ≥ 100</pre>
 * {@code median_d} = median over the last 6 closed cycles that reached day {@code d} of their day-1..d spend in the
 * category ({@code HistoricalCategorySpendService}); at least {@value DetectorThresholds#CATEGORY_MIN_CYCLES} such
 * cycles are required. OPEN cycles only (awaiting a late salary there is no comparable day). One insight per category,
 * the largest excess first.
 */
@Component
public class CategoryAboveTypicalDetector extends MultiInsightDetector {

    @Override
    public InsightType type() {
        return InsightType.CATEGORY_ABOVE_TYPICAL;
    }

    @Override
    public List<InsightCandidate> detectAll(InsightContext ctx) {
        if (ctx.cycle().state() != CycleState.OPEN) {
            return List.of();
        }
        CategoryDayBaseline baseline = ctx.categoryDayBaseline();
        if (baseline.cyclesUsed() < CATEGORY_MIN_CYCLES) {
            return List.of();
        }
        List<Hit> hits = new ArrayList<>();
        for (CategoryAtDay category : baseline.categories()) {
            BigDecimal typical = category.medianAtDay();
            if (typical == null || typical.compareTo(CATEGORY_MIN_BASELINE) < 0) {
                continue;
            }
            BigDecimal margin = CATEGORY_MIN_EXCESS.max(typical.multiply(CATEGORY_EXCESS_SHARE));
            if (category.currentAmount().compareTo(typical.add(margin)) > 0) {
                hits.add(new Hit(category, typical, category.currentAmount().subtract(typical)));
            }
        }
        return hits.stream()
                .sorted(Comparator.comparing(Hit::excess).reversed()
                        .thenComparing(hit -> hit.category().categoryId()))
                .map(hit -> candidate(hit, baseline))
                .toList();
    }

    private InsightCandidate candidate(Hit hit, CategoryDayBaseline baseline) {
        CategoryAtDay category = hit.category();
        // typical ≥ 100 here, so the percentage is never on a small base
        BigDecimal excessPercent = InsightFormat.percentOf(hit.excess(), hit.typical());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("categoryId", category.categoryId());
        payload.put("categoryName", category.name());
        payload.put("dayIndex", baseline.dayIndex());
        payload.put("current", category.currentAmount());
        payload.put("typical", hit.typical());
        payload.put("excess", hit.excess());
        payload.put("excessPercent", excessPercent);
        payload.put("baselineCycles", baseline.cyclesUsed());
        return new InsightCandidate(
                type(),
                InsightSeverity.WARN,
                "category:" + category.categoryId(),
                InsightFormat.clip(category.name(), 120) + " above typical",
                "%s by day %d vs typical %s (+%s, +%s).".formatted(pln(category.currentAmount()), baseline.dayIndex(),
                        pln(hit.typical()), pln(hit.excess()), wholePercent(excessPercent)),
                payload,
                InsightActionType.LINK,
                "/analytics/breakdown?category=" + category.categoryId());
    }

    private record Hit(CategoryAtDay category, BigDecimal typical, BigDecimal excess) {
    }
}
