package com.mikeshaggy.backend.auth.service;

import com.mikeshaggy.backend.auth.domain.password.PasswordPolicyValidator;
import com.mikeshaggy.backend.auth.domain.reset.ResetToken;
import com.mikeshaggy.backend.auth.dto.request.ChangePasswordRequest;
import com.mikeshaggy.backend.auth.dto.request.ForgotPasswordRequest;
import com.mikeshaggy.backend.auth.dto.request.ResetPasswordRequest;
import com.mikeshaggy.backend.auth.exception.AuthException;
import com.mikeshaggy.backend.auth.exception.RateLimitException;
import com.mikeshaggy.backend.auth.repo.ResetTokenRepository;
import com.mikeshaggy.backend.auth.util.crypto.CryptoUtils;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.repo.UserRepository;
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
public class PasswordResetService {

    private final UserRepository userRepository;
    private final ResetTokenRepository resetTokenRepository;
    private final RateLimitService rateLimitService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final CryptoUtils cryptoUtils;
    private final EmailService emailService;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth.reset-token.ttl-seconds}")
    private int resetTokenTtl;

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String email = request.email().toLowerCase().trim();
        String clientIp = sessionService.getClientIp(httpRequest);

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
        
        log.info("Password reset email sent for user: {}", user.getId());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = cryptoUtils.sha256Hash(request.token());

        ResetToken resetToken = findValidResetToken(tokenHash);

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

        validateAndUpdatePassword(user, request.newPassword());

        resetTokenRepository.delete(resetToken);
        sessionService.invalidateAllSessions(user.getId());

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

        validateAndUpdatePassword(user, request.newPassword());
        sessionService.invalidateAllSessions(user.getId());

        log.info("Password changed successfully for user: {}", user.getId());
    }

    private ResetToken findValidResetToken(String tokenHash) {
        Iterable<ResetToken> allTokens = resetTokenRepository.findAll();
        
        for (ResetToken token : allTokens) {
            if (tokenHash.equals(token.getTokenHash()) && !token.isUsed()) {
                return token;
            }
        }
        return null;
    }

    private void validateAndUpdatePassword(User user, String newPassword) {
        PasswordPolicyValidator.ValidationResult validation = passwordPolicyValidator.validate(newPassword);
        if (!validation.isValid()) {
            throw new AuthException(validation.getMessage());
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void addJitter() {
        try {
            Thread.sleep(cryptoUtils.generateJitterMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
