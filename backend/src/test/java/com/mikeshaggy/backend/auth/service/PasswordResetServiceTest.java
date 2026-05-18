package com.mikeshaggy.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ResetTokenRepository resetTokenRepository;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;

    @Mock
    private CryptoUtils cryptoUtils;

    @Mock
    private EmailService emailService;

    @Mock
    private SessionService sessionService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks private PasswordResetService passwordResetService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "user@example.com";
    private static final String CLIENT_IP = "192.168.1.1";
    private static final String RAW_TOKEN = "secure-reset-token-base64";
    private static final String TOKEN_HASH = "sha256hashoftoken";
    private static final String NEW_PASSWORD = "NewSecureP@ss1";
    private static final String ENCODED_PASSWORD = "$2a$10$newencodedvalue";
    private static final int RESET_TOKEN_TTL = 900;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "resetTokenTtl", RESET_TOKEN_TTL);

        user =
                User.builder()
                        .id(USER_ID)
                        .email(EMAIL)
                        .username("testuser")
                        .passwordHash("$2a$10$oldhash")
                        .build();
    }

    private HttpServletRequest mockHttpRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(CLIENT_IP);
        return request;
    }

    private ResetToken validResetToken(Instant expiresAt) {
        return ResetToken.builder()
                .id(USER_ID + ":" + TOKEN_HASH)
                .userId(USER_ID)
                .tokenHash(TOKEN_HASH)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(expiresAt)
                .used(false)
                .ttl((long) RESET_TOKEN_TTL)
                .build();
    }

    @Nested
    class ForgotPassword {

        private HttpServletRequest httpRequest;

        @BeforeEach
        void setUpRequest() {
            httpRequest = mockHttpRequest();
        }

        @Test
        void existingUser_generatesTokenAndSendsEmail() {
            // given
            when(rateLimitService.isAllowed("reset:ip", CLIENT_IP)).thenReturn(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(cryptoUtils.generateSecureToken(32)).thenReturn(RAW_TOKEN);
            when(cryptoUtils.sha256Hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);

            // when
            passwordResetService.forgotPassword(new ForgotPasswordRequest(EMAIL), httpRequest);

            ArgumentCaptor<ResetToken> captor = ArgumentCaptor.forClass(ResetToken.class);
            // then
            verify(resetTokenRepository).deleteByUserId(USER_ID);
            verify(resetTokenRepository).save(captor.capture());

            ResetToken saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getTokenHash()).isEqualTo(TOKEN_HASH);
            assertThat(saved.isUsed()).isFalse();
            assertThat(saved.getExpiresAt()).isAfter(saved.getCreatedAt());
            assertThat(saved.getTtl()).isEqualTo((long) RESET_TOKEN_TTL);

            verify(emailService).sendPasswordResetEmail(EMAIL, RAW_TOKEN);
        }

        @Test
        void existingUser_deletesOldTokensBeforeCreatingNew() {
            // given
            when(rateLimitService.isAllowed("reset:ip", CLIENT_IP)).thenReturn(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(cryptoUtils.generateSecureToken(32)).thenReturn(RAW_TOKEN);
            when(cryptoUtils.sha256Hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);

            // when
            passwordResetService.forgotPassword(new ForgotPasswordRequest(EMAIL), httpRequest);
            // then

            var inOrder = inOrder(resetTokenRepository);
            inOrder.verify(resetTokenRepository).deleteByUserId(USER_ID);
            inOrder.verify(resetTokenRepository).save(any(ResetToken.class));
        }

        @Test
        void nonExistingUser_doesNotSendEmailButAddsJitter() {
            // given
            when(rateLimitService.isAllowed("reset:ip", CLIENT_IP)).thenReturn(true);
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            // when
            passwordResetService.forgotPassword(
                    new ForgotPasswordRequest("unknown@example.com"), httpRequest);

            // then
            verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
            verify(resetTokenRepository, never()).save(any());
            verify(cryptoUtils).addJitter();
        }

        @Test
        void nonExistingUser_doesNotThrow() {
            // given
            when(rateLimitService.isAllowed("reset:ip", CLIENT_IP)).thenReturn(true);
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            // Should complete without exception — silent return for security
            // when
            passwordResetService.forgotPassword(
                    // then
                    new ForgotPasswordRequest("unknown@example.com"), httpRequest);
        }

        @Test
        void rateLimited_throwsRateLimitException() {
            // given
            when(rateLimitService.isAllowed("reset:ip", CLIENT_IP)).thenReturn(false);

            // when
            // then
            assertThatThrownBy(
                            () ->
                                    passwordResetService.forgotPassword(
                                            new ForgotPasswordRequest(EMAIL), httpRequest))
                    .isInstanceOf(RateLimitException.class);

            verify(userRepository, never()).findByEmail(any());
            verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
        }

        @Test
        void normalizesEmailToLowerCaseTrimmed() {
            // given
            String rawEmail = "  User@Example.COM  ";
            when(rateLimitService.isAllowed("reset:ip", CLIENT_IP)).thenReturn(true);
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(cryptoUtils.generateSecureToken(32)).thenReturn(RAW_TOKEN);
            when(cryptoUtils.sha256Hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);

            // when
            passwordResetService.forgotPassword(new ForgotPasswordRequest(rawEmail), httpRequest);

            // then
            verify(userRepository).findByEmail("user@example.com");
        }
    }

    @Nested
    class ResetPassword {

        @Test
        void validToken_updatesPasswordAndInvalidatesSessions() {
            // given
            Instant futureExpiry = Instant.now().plusSeconds(600);
            ResetToken token = validResetToken(futureExpiry);

            when(cryptoUtils.sha256Hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(resetTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordPolicyValidator.validate(NEW_PASSWORD))
                    .thenReturn(PasswordPolicyValidator.ValidationResult.valid());
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

            // when
            passwordResetService.resetPassword(new ResetPasswordRequest(RAW_TOKEN, NEW_PASSWORD));

            // then
            assertThat(user.getPasswordHash()).isEqualTo(ENCODED_PASSWORD);
            verify(userRepository).save(user);
            verify(resetTokenRepository).delete(token);
            verify(sessionService).invalidateAllSessions(USER_ID);
        }

        @Test
        void validToken_passwordIsPersistedViaRepository() {
            // given
            Instant futureExpiry = Instant.now().plusSeconds(600);
            ResetToken token = validResetToken(futureExpiry);

            when(cryptoUtils.sha256Hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(resetTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordPolicyValidator.validate(NEW_PASSWORD))
                    .thenReturn(PasswordPolicyValidator.ValidationResult.valid());
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

            // when
            passwordResetService.resetPassword(new ResetPasswordRequest(RAW_TOKEN, NEW_PASSWORD));

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            // then
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo(ENCODED_PASSWORD);
        }

        @Test
        void invalidToken_throwsAuthExceptionWithJitter() {
            // given
            when(cryptoUtils.sha256Hash("bad-token")).thenReturn("bad-hash");
            when(resetTokenRepository.findByTokenHash("bad-hash")).thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(
                            () ->
                                    passwordResetService.resetPassword(
                                            new ResetPasswordRequest("bad-token", NEW_PASSWORD)))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Invalid or expired");

            verify(cryptoUtils).addJitter();
            verify(userRepository, never()).save(any());
            verify(sessionService, never()).invalidateAllSessions(any());
        }

        @Test
        void usedToken_treatedAsInvalid() {
            // given
            ResetToken usedToken = validResetToken(Instant.now().plusSeconds(600));
            usedToken.setUsed(true);

            when(cryptoUtils.sha256Hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            // findByTokenHash returns the token, but findValidResetToken filters out used tokens
            when(resetTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(usedToken));

            // when
            // then
            assertThatThrownBy(
                            () ->
                                    passwordResetService.resetPassword(
                                            new ResetPasswordRequest(RAW_TOKEN, NEW_PASSWORD)))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Invalid or expired");

            verify(cryptoUtils).addJitter();
            verify(userRepository, never()).save(any());
        }

        @Test
        void expiredToken_throwsAuthExceptionAndDeletesToken() {
            // given
            Instant pastExpiry = Instant.now().minusSeconds(60);
            ResetToken expiredToken = validResetToken(pastExpiry);

            when(cryptoUtils.sha256Hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(resetTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(expiredToken));

            // when
            // then
            assertThatThrownBy(
                            () ->
                                    passwordResetService.resetPassword(
                                            new ResetPasswordRequest(RAW_TOKEN, NEW_PASSWORD)))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Invalid or expired");

            verify(resetTokenRepository).delete(expiredToken);
            verify(cryptoUtils).addJitter();
            verify(userRepository, never()).save(any());
        }

        @Test
        void weakNewPassword_throwsAuthExceptionWithPolicyMessage() {
            // given
            Instant futureExpiry = Instant.now().plusSeconds(600);
            ResetToken token = validResetToken(futureExpiry);

            when(cryptoUtils.sha256Hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(resetTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordPolicyValidator.validate("weak"))
                    .thenReturn(
                            PasswordPolicyValidator.ValidationResult.invalid(
                                    "Password must be at least 12 characters long"));

            // when
            // then
            assertThatThrownBy(
                            () -> passwordResetService.resetPassword(new ResetPasswordRequest(RAW_TOKEN, "weak")))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("at least 12 characters");

            verify(userRepository, never()).save(any());
        }

        @Test
        void validReset_tokenIsDeletedAfterPasswordUpdate() {
            // given
            Instant futureExpiry = Instant.now().plusSeconds(600);
            ResetToken token = validResetToken(futureExpiry);

            when(cryptoUtils.sha256Hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(resetTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordPolicyValidator.validate(NEW_PASSWORD))
                    .thenReturn(PasswordPolicyValidator.ValidationResult.valid());
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

            // when
            passwordResetService.resetPassword(new ResetPasswordRequest(RAW_TOKEN, NEW_PASSWORD));
            // then

            var inOrder = inOrder(userRepository, resetTokenRepository, sessionService);
            inOrder.verify(userRepository).save(user);
            inOrder.verify(resetTokenRepository).delete(token);
            inOrder.verify(sessionService).invalidateAllSessions(USER_ID);
        }
    }

    @Nested
    class ChangePassword {

        @Test
        void happyPath_updatesPasswordAndInvalidatesSessions() {
            // given
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldP@ssword123", user.getPasswordHash())).thenReturn(true);
            when(passwordPolicyValidator.validate(NEW_PASSWORD))
                    .thenReturn(PasswordPolicyValidator.ValidationResult.valid());
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

            // when
            passwordResetService.changePassword(
                    USER_ID, new ChangePasswordRequest("OldP@ssword123", NEW_PASSWORD));

            // then
            assertThat(user.getPasswordHash()).isEqualTo(ENCODED_PASSWORD);
            verify(userRepository).save(user);
            verify(sessionService).invalidateAllSessions(USER_ID);
        }

        @Test
        void wrongCurrentPassword_throwsAuthExceptionWithJitter() {
            // given
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongP@ss123!", user.getPasswordHash())).thenReturn(false);

            // when
            // then
            assertThatThrownBy(
                            () ->
                                    passwordResetService.changePassword(
                                            USER_ID, new ChangePasswordRequest("WrongP@ss123!", NEW_PASSWORD)))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Current password is incorrect");

            verify(cryptoUtils).addJitter();
            verify(userRepository, never()).save(any());
        }

        @Test
        void sameAsCurrentPassword_throwsAuthException() {
            // given
            String samePassword = "SameP@ssword1!";
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(samePassword, user.getPasswordHash())).thenReturn(true);

            // when
            // then
            assertThatThrownBy(
                            () ->
                                    passwordResetService.changePassword(
                                            USER_ID, new ChangePasswordRequest(samePassword, samePassword)))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("different from current password");

            verify(userRepository, never()).save(any());
        }

        @Test
        void failsPolicyValidation_throwsAuthExceptionWithPolicyMessage() {
            // given
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldP@ssword123", user.getPasswordHash())).thenReturn(true);
            when(passwordPolicyValidator.validate("weaknewpass!!"))
                    .thenReturn(
                            PasswordPolicyValidator.ValidationResult.invalid(
                                    "Password must contain at least 3 of the following: lowercase letters, uppercase"
                                            + " letters, digits, special characters"));

            // when
            // then
            assertThatThrownBy(
                            () ->
                                    passwordResetService.changePassword(
                                            USER_ID, new ChangePasswordRequest("OldP@ssword123", "weaknewpass!!")))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("at least 3");

            verify(userRepository, never()).save(any());
        }

        @Test
        void unknownUser_throwsAuthException() {
            // given
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(
                            () ->
                                    passwordResetService.changePassword(
                                            USER_ID, new ChangePasswordRequest("OldP@ssword123", NEW_PASSWORD)))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("User not found");
        }
    }
}
