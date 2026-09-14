package com.mikeshaggy.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProxySecretFilter extends OncePerRequestFilter {

    private static final String PROXY_SECRET_HEADER = "X-Savix-Proxy-Secret";

    @Value("${app.proxy.secret}")
    private String expectedSecret;

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String providedSecret = request.getHeader(PROXY_SECRET_HEADER);

        if (!secretMatches(providedSecret)) {
            log.warn("Rejected request to {} - invalid or missing proxy secret", request.getRequestURI());
            
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            
            Map<String, Object> errorBody = Map.of(
                    "status", 403,
                    "error", "Forbidden",
                    "message", "Direct backend access not allowed"
            );
            
            objectMapper.writeValue(response.getOutputStream(), errorBody);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Constant-time comparison of the provided secret against the expected one,
     * so a timing side-channel cannot be used to recover the secret byte by byte.
     */
    private boolean secretMatches(String providedSecret) {
        if (providedSecret == null) {
            return false;
        }
        byte[] provided = providedSecret.getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(provided, expected);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();

        // Liveness/readiness must stay reachable for the platform health check.
        if (path.startsWith("/actuator/health")) {
            return true;
        }

        // All other actuator endpoints (metrics, prometheus, info, ...) require the
        // proxy secret so they are not publicly reachable when the backend is hit
        // directly, bypassing the Next.js proxy.
        if (path.startsWith("/actuator")) {
            return false;
        }

        return !path.startsWith("/api/");
    }
}
