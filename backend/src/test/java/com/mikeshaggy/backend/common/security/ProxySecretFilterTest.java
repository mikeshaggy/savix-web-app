package com.mikeshaggy.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("Proxy Secret Filter Tests")
class ProxySecretFilterTest {

    private static final String SECRET = "the-expected-proxy-secret";
    private static final String HEADER = "X-Savix-Proxy-Secret";

    private ProxySecretFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new ProxySecretFilter(new ObjectMapper());
        ReflectionTestUtils.setField(filter, "expectedSecret", SECRET);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test
    @DisplayName("Valid secret allows the request through the chain")
    void validSecret_passesThrough() throws ServletException, IOException {
        request.setRequestURI("/api/wallets");
        request.addHeader(HEADER, SECRET);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Wrong secret is rejected with 403")
    void wrongSecret_isForbidden() throws ServletException, IOException {
        request.setRequestURI("/api/wallets");
        request.addHeader(HEADER, "not-the-secret");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("Missing secret is rejected with 403")
    void missingSecret_isForbidden() throws ServletException, IOException {
        request.setRequestURI("/api/wallets");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("Health endpoint is not filtered (stays public)")
    void healthEndpoint_isNotFiltered() {
        request.setRequestURI("/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("Metrics and Prometheus endpoints are filtered (require the secret)")
    void metricsAndPrometheus_areFiltered() {
        request.setRequestURI("/actuator/prometheus");
        assertThat(filter.shouldNotFilter(request)).isFalse();

        MockHttpServletRequest metrics = new MockHttpServletRequest();
        metrics.setRequestURI("/actuator/metrics");
        assertThat(filter.shouldNotFilter(metrics)).isFalse();
    }

    @Test
    @DisplayName("API requests are filtered; non-API/non-actuator paths are skipped")
    void apiFilteredAndOtherPathsSkipped() {
        request.setRequestURI("/api/transactions");
        assertThat(filter.shouldNotFilter(request)).isFalse();

        MockHttpServletRequest other = new MockHttpServletRequest();
        other.setRequestURI("/favicon.ico");
        assertThat(filter.shouldNotFilter(other)).isTrue();
    }
}
