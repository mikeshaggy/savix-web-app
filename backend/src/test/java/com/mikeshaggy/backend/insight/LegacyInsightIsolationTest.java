package com.mikeshaggy.backend.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikeshaggy.backend.analytics.api.AnalyticsController;
import com.mikeshaggy.backend.analytics.insight.InsightEngine;
import com.mikeshaggy.backend.dashboard.service.DashboardSummaryService;
import com.mikeshaggy.backend.dashboard.service.DashboardV2Sections;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Stage 7.2 regression guard: until Stage 7.4 rewires the dashboard, no legacy read path can reach the Insights v2
 * service, repository or writer — so a legacy dashboard / analytics read can never write an {@code insights} row,
 * whatever the flags, and the legacy engine stays the dashboard's insight source.
 */
class LegacyInsightIsolationTest {

    private static final String V2_PACKAGE = "com.mikeshaggy.backend.insight.";

    @Test
    void legacyReadPaths_doNotDependOnInsightsV2() {
        for (Class<?> legacy : new Class<?>[] {
                DashboardSummaryService.class, DashboardV2Sections.class, AnalyticsController.class, InsightEngine.class}) {
            assertThat(dependencies(legacy))
                    .as("%s must not depend on the insights v2 package before Stage 7.4", legacy.getSimpleName())
                    .noneMatch(type -> type.getName().startsWith(V2_PACKAGE));
        }
    }

    @Test
    void dashboardStillUsesTheLegacyEngine() {
        assertThat(dependencies(DashboardSummaryService.class)).contains(InsightEngine.class);
    }

    private static Stream<Class<?>> dependencies(Class<?> type) {
        Stream<Class<?>> fields = Arrays.stream(type.getDeclaredFields()).map(Field::getType);
        Stream<Class<?>> ctorParams = Arrays.stream(type.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream);
        return Stream.concat(fields, ctorParams).distinct().toList().stream();
    }
}
