package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.insight.domain.InsightType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-11: every Insights v2 type has exactly one Spring-registered detector — a detector class that lost its
 * {@code @Component} (or a second one for the same type) would otherwise just silently never run, or run twice.
 */
class InsightDetectorRegistrationTest {

    @Test
    void everyInsightType_hasExactlyOneComponentDetector() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(InsightDetector.class));

        List<InsightType> registered = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.mikeshaggy.backend.insight")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (type.isAnnotationPresent(org.springframework.stereotype.Component.class)) {
                registered.add(((InsightDetector) type.getDeclaredConstructor().newInstance()).type());
            }
        }

        assertThat(registered).containsExactlyInAnyOrder(InsightType.values());
        assertThat(Arrays.stream(InsightType.values())).hasSize(10);
    }
}
