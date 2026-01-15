package com.mikeshaggy.backend.auth.service;

import com.mikeshaggy.backend.auth.domain.jwt.JwtClaims;
import com.mikeshaggy.backend.auth.dto.request.*;
import com.mikeshaggy.backend.auth.domain.session.RefreshSession;
import com.mikeshaggy.backend.auth.domain.session.RefreshSessionRepository;
import com.mikeshaggy.backend.auth.domain.reset.ResetToken;
import com.mikeshaggy.backend.auth.domain.reset.ResetTokenRepository;
import com.mikeshaggy.backend.auth.util.crypto.CryptoUtils;
import com.mikeshaggy.backend.auth.domain.password.PasswordPolicyValidator;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshSessionRepository refreshSessionRepository;
    private final ResetTokenRepository resetTokenRepository;
    private final RateLimitService rateLimitService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final CryptoUtils cryptoUtils;
    private final EmailService emailService;

    @Value("${auth.refresh-token.ttl-seconds}")
    private int refreshTokenTtl;

    @Value("${auth.reset-token.ttl-seconds}")
    private int resetTokenTtl;

    private final Argon2PasswordEncoder passwordEncoder = new Argon2PasswordEncoder(
            16,    // saltLength
            32,    // hashLength
            1,     // parallelism
            65536, // memory (64MB)
            3      // iterations
    );

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase().trim())) {
            throw new AuthException("Registration failed");
        }

        PasswordPolicyValidator.ValidationResult validation = passwordPolicyValidator.validate(request.password());
        if (!validation.isValid()) {
            throw new AuthException(validation.getMessage());
        }

        User user = User.builder()
                .email(request.email().toLowerCase().trim())
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        userRepository.save(user);
    }

    @Transactional
    public LoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
        String email = request.email().toLowerCase().trim();
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        if (!rateLimitService.isAllowed("login:ip", clientIp)) {
            addJitter();
            throw new RateLimitException("Too many login attempts. Please try again later.");
        }

        if (!rateLimitService.isAllowed("login:email", email)) {
            addJitter();
            throw new RateLimitException("Too many login attempts. Please try again later.");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            addJitter();
            throw new AuthException("Invalid credentials");
        }

        User user = userOpt.get();

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            addJitter();
            throw new AuthException("Invalid credentials");
        }

        rateLimitService.recordSuccess("login:ip", clientIp);
        rateLimitService.recordSuccess("login:email", email);

        return createSession(user, clientIp, userAgent);
    }

    @Transactional
    public LoginResult refresh(String refreshToken, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);

        if (!rateLimitService.isAllowed("refresh:ip", clientIp)) {
            throw new RateLimitException("Too many refresh attempts. Please try again later.");
        }

        JwtClaims claims;
        try {
            claims = jwtService.validateAndParse(refreshToken);
        } catch (JwtService.InvalidTokenException e) {
            throw new AuthException("Invalid refresh token");
        }

        String sessionKey = buildSessionKey(claims.subject(), claims.jti());
        RefreshSession session = refreshSessionRepository.findById(sessionKey)
                .orElseThrow(() -> new AuthException("Session not found or expired"));

        String providedJtiHash = cryptoUtils.sha256Hash(claims.jti());
        if (!providedJtiHash.equals(session.getRefreshTokenJtiHash())) {
            log.warn("Refresh token reuse detected for user: {}", claims.subject());
            refreshSessionRepository.deleteByUserId(claims.subject());
            throw new AuthException("Invalid refresh token");
        }

        refreshSessionRepository.delete(session);

        User user = userRepository.findById(claims.subject())
                .orElseThrow(() -> new AuthException("User not found"));

        String userAgent = httpRequest.getHeader("User-Agent");

        return createSession(user, clientIp, userAgent);
    }

    @Transactional
    public void logout(String refreshToken) {
        try {
            JwtClaims claims = jwtService.validateAndParse(refreshToken);
            String sessionKey = buildSessionKey(claims.subject(), claims.jti());
            refreshSessionRepository.deleteById(sessionKey);
        } catch (Exception e) {
            log.debug("Logout called with invalid token", e);
        }
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String email = request.email().toLowerCase().trim();
        String clientIp = getClientIp(httpRequest);

        if (!rateLimitService.isAllowed("reset:ip", clientIp)) {
            throw new RateLimitException("Too many password reset attempts. Please try again later.");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            addJitter();
            return;
        }

        User user = userOpt.get();

        String resetToken = cryptoUtils.generateSecureToken(32);
        String tokenHash = cryptoUtils.sha256Hash(resetToken);

        resetTokenRepository.deleteByUserId(user.getId());

        Instant now = Instant.now();
        ResetToken resetTokenEntity = ResetToken.builder()
                .id(user.getId() + ":" + tokenHash)
                .userId(user.getId())
                .tokenHash(tokenHash)
                .createdAt(now)
                .expiresAt(now.plusSeconds(resetTokenTtl))
                .used(false)
                .ttl((long) resetTokenTtl)
                .build();

        resetTokenRepository.save(resetTokenEntity);

        emailService.sendPasswordResetEmail(user.getEmail(), resetToken);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = cryptoUtils.sha256Hash(request.token());

        Iterable<ResetToken> allTokens = resetTokenRepository.findAll();
        ResetToken resetToken = null;

        for (ResetToken token : allTokens) {
            if (tokenHash.equals(token.getTokenHash()) && !token.isUsed()) {
                resetToken = token;
                break;
            }
        }

        if (resetToken == null) {
            addJitter();
            throw new AuthException("Invalid or expired reset token");
        }

        if (Instant.now().isAfter(resetToken.getExpiresAt())) {
            resetTokenRepository.delete(resetToken);
            addJitter();
            throw new AuthException("Invalid or expired reset token");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new AuthException("User not found"));

        PasswordPolicyValidator.ValidationResult validation = passwordPolicyValidator.validate(request.newPassword());
        if (!validation.isValid()) {
            throw new AuthException(validation.getMessage());
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetTokenRepository.delete(resetToken);

        refreshSessionRepository.deleteByUserId(user.getId());

        log.info("Password reset successful for user: {}", user.getId());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            addJitter();
            throw new AuthException("Current password is incorrect");
        }

        if (request.currentPassword().equals(request.newPassword())) {
            throw new AuthException("New password must be different from current password");
        }

        PasswordPolicyValidator.ValidationResult validation = passwordPolicyValidator.validate(request.newPassword());
        if (!validation.isValid()) {
            throw new AuthException(validation.getMessage());
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        refreshSessionRepository.deleteByUserId(user.getId());

        log.info("Password changed successfully for user: {}", user.getId());
    }

    private LoginResult createSession(User user, String clientIp, String userAgent) {
        Tokens tokens = jwtService.generateTokens(user.getId());

        JwtClaims refreshClaims = jwtService.validateAndParse(tokens.refreshToken);

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

        return new LoginResult(tokens, user);
    }

    private String buildSessionKey(UUID userId, String jti) {
        return userId + ":" + jti;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }

        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    private void addJitter() {
        try {
            Thread.sleep(cryptoUtils.generateJitterMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record Tokens(String accessToken, String refreshToken) {}

    public record LoginResult(Tokens tokens, User user) {}

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }

    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
