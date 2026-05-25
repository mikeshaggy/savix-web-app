package com.mikeshaggy.backend.auth.service;

import com.mikeshaggy.backend.auth.domain.jwt.JwtClaims;
import com.mikeshaggy.backend.auth.domain.session.RefreshSession;
import com.mikeshaggy.backend.auth.dto.LoginResult;
import com.mikeshaggy.backend.auth.dto.request.LoginRequest;
import com.mikeshaggy.backend.auth.exception.AuthException;
import com.mikeshaggy.backend.auth.exception.RateLimitException;
import com.mikeshaggy.backend.auth.repository.RefreshSessionRepository;
import com.mikeshaggy.backend.auth.util.crypto.CryptoUtils;
import com.mikeshaggy.backend.common.util.HttpRequestUtils;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshSessionRepository refreshSessionRepository;
    private final RateLimitService rateLimitService;
    private final CryptoUtils cryptoUtils;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth.refresh-token.ttl-seconds}")
    private int refreshTokenTtl;

    @Value("${auth.rate-limit.refresh.max-attempts}")
    private int refreshMaxAttempts;

    @Value("${auth.rate-limit.refresh.window-seconds}")
    private int refreshWindowSeconds;

    @Transactional
    public LoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
        String email = request.email().toLowerCase().trim();
        String clientIp = HttpRequestUtils.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        if (!rateLimitService.isAllowed("login:ip", clientIp)) {
            log.warn("Login rejected: reason=rate_limit_ip");
            cryptoUtils.addJitter();
            throw new RateLimitException("Too many login attempts. Please try again later.");
        }

        if (!rateLimitService.isAllowed("login:email", email)) {
            log.warn("Login rejected: reason=rate_limit_email");
            cryptoUtils.addJitter();
            throw new RateLimitException("Too many login attempts. Please try again later.");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.warn("Login failed: reason=invalid_credentials");
            cryptoUtils.addJitter();
            throw new AuthException("Invalid credentials");
        }

        User user = userOpt.get();

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: reason=invalid_credentials, userId={}", user.getId());
            cryptoUtils.addJitter();
            throw new AuthException("Invalid credentials");
        }

        rateLimitService.recordSuccess("login:ip", clientIp);
        rateLimitService.recordSuccess("login:email", email);

        LoginResult loginResult = createSession(user, clientIp, userAgent);
        log.info("Login successful: userId={}", user.getId());
        return loginResult;
    }

    @Transactional
    public LoginResult refresh(String refreshToken, HttpServletRequest httpRequest) {
        String clientIp = HttpRequestUtils.getClientIp(httpRequest);

        if (!rateLimitService.isAllowed("refresh:ip", clientIp, refreshMaxAttempts, refreshWindowSeconds, refreshWindowSeconds)) {
            log.warn("Refresh token rejected: reason=rate_limit_ip");
            throw new RateLimitException("Too many refresh attempts. Please try again later.");
        }

        JwtClaims claims;
        try {
            claims = jwtService.validateAndParse(refreshToken);
        } catch (JwtService.InvalidTokenException e) {
            log.warn("Refresh token validation failed: reason=invalid_token");
            throw new AuthException("Invalid refresh token");
        }

        String sessionKey = buildSessionKey(claims.subject(), claims.jti());
        RefreshSession session = refreshSessionRepository.findById(sessionKey)
                .orElseThrow(() -> new AuthException("Session not found or expired"));

        String providedJtiHash = cryptoUtils.sha256Hash(claims.jti());
        if (!providedJtiHash.equals(session.getRefreshTokenJtiHash())) {
            log.warn("Refresh token validation failed: reason=token_reuse, userId={}", claims.subject());
            refreshSessionRepository.deleteByUserId(claims.subject());
            throw new AuthException("Invalid refresh token");
        }

        refreshSessionRepository.delete(session);

        User user = userRepository.findById(claims.subject())
                .orElseThrow(() -> new AuthException("User not found"));

        String userAgent = httpRequest.getHeader("User-Agent");

        LoginResult refreshResult = createSession(user, clientIp, userAgent);
        log.info("Token refresh successful: userId={}", user.getId());
        return refreshResult;
    }

    @Transactional
    public void logout(String refreshToken) {
        try {
            JwtClaims claims = jwtService.validateAndParse(refreshToken);
            String sessionKey = buildSessionKey(claims.subject(), claims.jti());
            refreshSessionRepository.deleteById(sessionKey);
            log.info("Logout successful: userId={}", claims.subject());
        } catch (Exception e) {
            log.warn("Logout ignored: reason=invalid_refresh_token");
        }
    }

    @Transactional
    public void invalidateAllSessions(UUID userId) {
        refreshSessionRepository.deleteByUserId(userId);
        log.info("Sessions invalidated: userId={}", userId);
    }

    private LoginResult createSession(User user, String clientIp, String userAgent) {
        com.mikeshaggy.backend.auth.dto.TokenPair tokens = jwtService.generateTokenPair(user.getId());

        JwtClaims refreshClaims = jwtService.validateAndParse(tokens.refreshToken());

        Instant now = Instant.now();
        RefreshSession session = RefreshSession.builder()
                .id(buildSessionKey(user.getId(), refreshClaims.jti()))
                .userId(user.getId())
                .sessionId(UUID.fromString(refreshClaims.jti()))
                .refreshTokenJtiHash(cryptoUtils.sha256Hash(refreshClaims.jti()))
                .createdAt(now)
                .expiresAt(now.plusSeconds(refreshTokenTtl))
                .lastUsedAt(now)
                .ipHash(cryptoUtils.hashIpAddress(clientIp))
                .userAgentHash(cryptoUtils.hashUserAgent(userAgent))
                .ttl((long) refreshTokenTtl)
                .build();

        refreshSessionRepository.save(session);

        return new LoginResult(tokens);
    }

    private String buildSessionKey(UUID userId, String jti) {
        return userId + ":" + jti;
    }
}
