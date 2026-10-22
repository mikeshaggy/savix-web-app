package com.mikeshaggy.backend.insight.api;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** {@link AbstractInsightHttpIntegrationTest} on the default H2 test database (Spring Boot's default open-in-view). */
@SpringBootTest(properties = {
        "app.features.insights-v2=true",
        "app.features.pay-cycle-v2=true"
})
@AutoConfigureMockMvc(addFilters = false)
@Import(AbstractInsightHttpIntegrationTest.HttpDetectors.class)
class InsightHttpH2Test extends AbstractInsightHttpIntegrationTest {
}
