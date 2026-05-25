package com.mikeshaggy.backend.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter for request correlation using request IDs and MDC (Mapped Diagnostic Context).
 * 
 * Every HTTP request receives a unique requestId that is:
 * - Read from X-Request-Id header if present
 * - Generated as UUID if missing
 * - Added to MDC for structured logging
 * - Added to response headers
 * - Removed from MDC after request completes (prevents memory leaks in thread pools)
 * 
 * Optionally adds userId to MDC if the request is authenticated.
 * 
 * Thread-safe: MDC is cleaned up in a finally block to ensure cleanup even on exceptions.
 */
@Component
@Slf4j
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID_KEY = "requestId";
    private static final String MDC_USER_ID_KEY = "userId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Generate or retrieve request ID
        String requestId = extractOrGenerateRequestId(request);

        // Put into MDC
        MDC.put(MDC_REQUEST_ID_KEY, requestId);

        // Optionally add userId if authenticated
        addUserIdToMdc();

        try {
            // Add to response header
            response.addHeader(REQUEST_ID_HEADER, requestId);

            // Continue with filter chain
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: Clean up MDC to prevent memory leaks in thread pool contexts
            MDC.remove(MDC_REQUEST_ID_KEY);
            MDC.remove(MDC_USER_ID_KEY);
        }
    }

    /**
     * Extracts the request ID from the X-Request-Id header, or generates a new UUID.
     *
     * @param request The HTTP request
     * @return The request ID
     */
    private String extractOrGenerateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        return requestId;
    }

    /**
     * Adds the authenticated user's ID to MDC if available.
     * Safely handles missing authentication without crashing.
     */
    private void addUserIdToMdc() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                // Extract the principal (username/userId)
                Object principal = authentication.getPrincipal();

                if (principal != null) {
                    MDC.put(MDC_USER_ID_KEY, principal.toString());
                }
            }
        } catch (Exception e) {
            // Silently ignore - do not crash if security context is unavailable
            // Log at debug level to avoid noise in logs
            log.debug("Could not extract userId from SecurityContext", e);
        }
    }
}
