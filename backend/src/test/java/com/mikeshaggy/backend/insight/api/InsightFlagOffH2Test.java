package com.mikeshaggy.backend.insight.api;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Flag off on a private in-memory H2 database (so dropping {@code insights} cannot affect other test classes). */
@SpringBootTest(properties = {
        "app.features.insights-v2=false",
        "app.features.pay-cycle-v2=true",
        "spring.datasource.url=jdbc:h2:mem:insights-flag-off"
})
@AutoConfigureMockMvc(addFilters = false)
@Import(AbstractInsightFlagOffIntegrationTest.CountingDetector.class)
class InsightFlagOffH2Test extends AbstractInsightFlagOffIntegrationTest {
}
