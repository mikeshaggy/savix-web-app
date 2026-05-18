package com.mikeshaggy.backend.config.security;

import static org.assertj.core.api.Assertions.*;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("Request Correlation Filter Tests")
class RequestCorrelationFilterTest {

    private RequestCorrelationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new RequestCorrelationFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should generate UUID when X-Request-Id header is missing")
    void shouldGenerateUuidWhenHeaderMissing() throws ServletException, IOException {
        // given
        request.setRequestURI("/api/wallets");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // when
        String responseHeader = response.getHeader("X-Request-Id");
        // then
        assertThat(responseHeader).isNotNull().isNotEmpty();

        assertThatNoException().isThrownBy(() -> UUID.fromString(responseHeader));
    }

    @Test
    @DisplayName("Should reuse X-Request-Id header when provided")
    void shouldReuseProvidedRequestId() throws ServletException, IOException {
        // given
        String customId = "external-request-123";
        request.addHeader("X-Request-Id", customId);
        request.setRequestURI("/api/categories");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // when
        // then
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(customId);
    }

    @Test
    @DisplayName("Should add requestId to MDC")
    void shouldAddRequestIdToMdc() throws ServletException, IOException {
        // given
        String requestId = "test-request-id-456";
        request.addHeader("X-Request-Id", requestId);

        final String[] capturedMdc = new String[1];

        filter.doFilterInternal(
                request,
                response,
                (req, res) -> {
                    capturedMdc[0] = MDC.get("requestId");
                });

        // when
        // then
        assertThat(capturedMdc[0]).isEqualTo(requestId);
    }

    @Test
    @DisplayName("Should clean up MDC after request completes")
    void shouldCleanupMdcAfterRequest() throws ServletException, IOException {
        // given
        request.addHeader("X-Request-Id", "cleanup-test-789");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // when
        assertThat(MDC.get("requestId")).isNull();
        // then
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    @DisplayName("Should clean up MDC even when exception occurs in filter chain")
    void shouldCleanupMdcOnException() {
        // given
        request.addHeader("X-Request-Id", "exception-test-999");
        MockFilterChain chainWithException =
                new MockFilterChain() {
                    @Override
                    public void doFilter(
                            jakarta.servlet.ServletRequest servletRequest,
                            jakarta.servlet.ServletResponse servletResponse)
                            throws IOException, ServletException {
                        throw new RuntimeException("Simulated filter chain exception");
                    }
                };

        // when
        // then
        assertThatThrownBy(() -> filter.doFilterInternal(request, response, chainWithException))
                .isInstanceOf(RuntimeException.class);

        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    @DisplayName("Should extract userId from authenticated security context")
    void shouldExtractUserIdFromSecurityContext() throws ServletException, IOException {
        // given
        String userId = "user@example.com";
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        request.addHeader("X-Request-Id", "user-test-123");

        final String[] capturedUserId = new String[1];

        filter.doFilterInternal(
                request,
                response,
                (req, res) -> {
                    capturedUserId[0] = MDC.get("userId");
                });

        // when
        // then
        assertThat(capturedUserId[0]).isEqualTo(userId);
    }

    @Test
    @DisplayName("Should handle unauthenticated requests gracefully (no userId in MDC)")
    void shouldHandleUnauthenticatedRequests() throws ServletException, IOException {
        // given
        SecurityContextHolder.clearContext();
        request.addHeader("X-Request-Id", "unauth-test-456");

        final String[] capturedUserId = new String[1];
        final String[] capturedRequestId = new String[1];

        filter.doFilterInternal(
                request,
                response,
                (req, res) -> {
                    capturedUserId[0] = MDC.get("userId");
                    capturedRequestId[0] = MDC.get("requestId");
                });

        // when
        assertThat(capturedUserId[0]).isNull();
        // then
        assertThat(capturedRequestId[0]).isEqualTo("unauth-test-456");
    }

    @Test
    @DisplayName("Should handle blank X-Request-Id header and generate UUID")
    void shouldHandleBlankRequestIdHeader() throws ServletException, IOException {
        // given
        request.addHeader("X-Request-Id", "   ");
        request.setRequestURI("/api/transactions");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // when
        String responseHeader = response.getHeader("X-Request-Id");
        // then
        assertThat(responseHeader).isNotNull().isNotBlank();
        assertThatNoException().isThrownBy(() -> UUID.fromString(responseHeader));
    }

    @Test
    @DisplayName("Should add requestId to response header")
    void shouldAddRequestIdToResponseHeader() throws ServletException, IOException {
        // given
        String requestId = "header-test-789";
        request.addHeader("X-Request-Id", requestId);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // when
        // then
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(requestId);
    }

    @Test
    @DisplayName("Should handle null security authentication safely")
    void shouldHandleNullSecurityAuthentication() throws ServletException, IOException {
        // given
        SecurityContextHolder.getContext().setAuthentication(null);
        request.addHeader("X-Request-Id", "null-auth-test");

        final String[] capturedUserId = new String[1];
        final String[] capturedRequestId = new String[1];

        filter.doFilterInternal(
                request,
                response,
                (req, res) -> {
                    capturedUserId[0] = MDC.get("userId");
                    capturedRequestId[0] = MDC.get("requestId");
                });

        // when
        assertThat(capturedUserId[0]).isNull();
        // then
        assertThat(capturedRequestId[0]).isEqualTo("null-auth-test");
    }

    @Test
    @DisplayName("Should preserve requestId during filter chain execution")
    void shouldPreserveRequestIdDuringExecution() throws ServletException, IOException {
        // given
        String originalId = "nested-filter-test";
        request.addHeader("X-Request-Id", originalId);

        final String[] mdcValue = new String[1];

        filter.doFilterInternal(
                request,
                response,
                (req, res) -> {
                    mdcValue[0] = MDC.get("requestId");
                });

        // when
        assertThat(mdcValue[0]).isEqualTo(originalId);

        // then
        assertThat(MDC.get("requestId")).isNull();
    }
}
