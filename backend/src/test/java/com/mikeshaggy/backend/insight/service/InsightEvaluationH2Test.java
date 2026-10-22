package com.mikeshaggy.backend.insight.service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** {@link AbstractInsightEvaluationIntegrationTest} on the default H2 test database (schema from the entities). */
@SpringBootTest(properties = {
        "app.features.insights-v2=true",
        "app.features.pay-cycle-v2=true"
})
@Import(AbstractInsightEvaluationIntegrationTest.Detectors.class)
class InsightEvaluationH2Test extends AbstractInsightEvaluationIntegrationTest {
}
