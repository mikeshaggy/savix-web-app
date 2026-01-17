package com.mikeshaggy.backend.config.security;

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

        if (providedSecret == null || !providedSecret.equals(expectedSecret)) {
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

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        
        if (path.startsWith("/actuator/health")) {
            return true;
        }
        
        return !path.startsWith("/api/");
    }
}
