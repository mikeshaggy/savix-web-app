package com.mikeshaggy.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.auth.domain.jwt.JwtClaims;
import com.mikeshaggy.backend.auth.domain.session.RefreshSession;
import com.mikeshaggy.backend.auth.dto.LoginResult;
import com.mikeshaggy.backend.auth.dto.TokenPair;
import com.mikeshaggy.backend.auth.dto.request.LoginRequest;
import com.mikeshaggy.backend.auth.exception.AuthException;
import com.mikeshaggy.backend.auth.exception.RateLimitException;
import com.mikeshaggy.backend.auth.repository.RefreshSessionRepository;
import com.mikeshaggy.backend.auth.util.crypto.CryptoUtils;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.repository.UserRepository;
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
class SessionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshSessionRepository refreshSessionRepository;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private CryptoUtils cryptoUtils;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SessionService sessionService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "SecureP@ss123!";
    private static final String PASSWORD_HASH = "$2a$10$hashedvalue";
    private static final String CLIENT_IP = "192.168.1.1";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String ACCESS_TOKEN = "access.token.value";
    private static final String REFRESH_TOKEN = "refresh.token.value";
    private static final String JTI = UUID.randomUUID().toString();
    private static final String JTI_HASH = "sha256hashvalue";

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sessionService, "refreshTokenTtl", 1209600);
        ReflectionTestUtils.setField(sessionService, "refreshMaxAttempts", 30);
        ReflectionTestUtils.setField(sessionService, "refreshWindowSeconds", 60);

        user =
                User.builder()
                        .id(USER_ID)
                        .email(EMAIL)
                        .username("testuser")
                        .passwordHash(PASSWORD_HASH)
                        .build();
    }

    private HttpServletRequest mockHttpRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(CLIENT_IP);
        lenient().when(request.getHeader("User-Agent")).thenReturn(USER_AGENT);
        return request;
    }

    private void stubSuccessfulSessionCreation() {
        TokenPair tokens = new TokenPair(ACCESS_TOKEN, REFRESH_TOKEN);
        JwtClaims refreshClaims =
                JwtClaims.of(USER_ID, JTI, Instant.now(), Instant.now().plusSeconds(1209600));

        when(jwtService.generateTokenPair(USER_ID)).thenReturn(tokens);
        when(jwtService.validateAndParse(REFRESH_TOKEN)).thenReturn(refreshClaims);
        when(cryptoUtils.sha256Hash(JTI)).thenReturn(JTI_HASH);
        when(cryptoUtils.hashIpAddress(CLIENT_IP)).thenReturn("hashed-ip");
        when(cryptoUtils.hashUserAgent(USER_AGENT)).thenReturn("hashed-ua");
    }

    @Nested
    class Login {

        private HttpServletRequest httpRequest;

        @BeforeEach
        void setUpRequest() {
            httpRequest = mockHttpRequest();
        }

        @Test
        void happyPath_returnsTokensAndPersistsSession() {
            // given
            when(rateLimitService.isAllowed("login:ip", CLIENT_IP)).thenReturn(true);
            when(rateLimitService.isAllowed("login:email", EMAIL)).thenReturn(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
            stubSuccessfulSessionCreation();

            // when
            LoginResult result = sessionService.login(new LoginRequest(EMAIL, PASSWORD), httpRequest);

            // then
            assertThat(result.tokens().accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.tokens().refreshToken()).isEqualTo(REFRESH_TOKEN);

            ArgumentCaptor<RefreshSession> captor = ArgumentCaptor.forClass(RefreshSession.class);
            verify(refreshSessionRepository).save(captor.capture());

            RefreshSession saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getRefreshTokenJtiHash()).isEqualTo(JTI_HASH);
            assertThat(saved.getTtl()).isEqualTo(1209600L);
        }

        @Test
        void clearsRateLimitOnSuccess() {
            // given
            when(rateLimitService.isAllowed("login:ip", CLIENT_IP)).thenReturn(true);
            when(rateLimitService.isAllowed("login:email", EMAIL)).thenReturn(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
            stubSuccessfulSessionCreation();

            // when
            sessionService.login(new LoginRequest(EMAIL, PASSWORD), httpRequest);

            // then
            verify(rateLimitService).recordSuccess("login:ip", CLIENT_IP);
            verify(rateLimitService).recordSuccess("login:email", EMAIL);
        }

        @Test
        void normalizesEmailToLowerCaseTrimmed() {
            // given
            String rawEmail = "  User@Example.COM  ";
            when(rateLimitService.isAllowed(eq("login:ip"), any())).thenReturn(true);
            when(rateLimitService.isAllowed("login:email", "user@example.com")).thenReturn(true);
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
            stubSuccessfulSessionCreation();

            // when
            sessionService.login(new LoginRequest(rawEmail, PASSWORD), httpRequest);

            // then
            verify(userRepository).findByEmail("user@example.com");
        }

        @Test
        void unknownEmail_throwsAuthExceptionWithJitter() {
            // given
            when(rateLimitService.isAllowed("login:ip", CLIENT_IP)).thenReturn(true);
            when(rateLimitService.isAllowed("login:email", EMAIL)).thenReturn(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(() -> sessionService.login(new LoginRequest(EMAIL, PASSWORD), httpRequest))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("Invalid credentials");

            verify(cryptoUtils).addJitter();
        }

        @Test
        void wrongPassword_throwsAuthExceptionWithJitter() {
            // given
            when(rateLimitService.isAllowed("login:ip", CLIENT_IP)).thenReturn(true);
            when(rateLimitService.isAllowed("login:email", EMAIL)).thenReturn(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong-password", PASSWORD_HASH)).thenReturn(false);

            // when
            // then
            assertThatThrownBy(
                            () -> sessionService.login(new LoginRequest(EMAIL, "wrong-password"), httpRequest))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("Invalid credentials");

            verify(cryptoUtils).addJitter();
        }

        @Test
        void wrongPassword_sameErrorMessageAsUnknownEmail() {
            // error message must not reveal whether the email exists
            // given
            when(rateLimitService.isAllowed(eq("login:ip"), any())).thenReturn(true);
            when(rateLimitService.isAllowed(eq("login:email"), any())).thenReturn(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            String wrongPwMsg = null;
            String unknownEmailMsg = null;

            try {
                sessionService.login(new LoginRequest(EMAIL, PASSWORD), httpRequest);
            } catch (AuthException e) {
                wrongPwMsg = e.getMessage();
            }

            try {
                sessionService.login(new LoginRequest("unknown@example.com", PASSWORD), httpRequest);
            } catch (AuthException e) {
                unknownEmailMsg = e.getMessage();
            }

            // when
            // then
            assertThat(wrongPwMsg).isNotNull().isEqualTo(unknownEmailMsg);
        }

        @Test
        void ipRateLimited_throwsRateLimitException() {
            // given
            when(rateLimitService.isAllowed("login:ip", CLIENT_IP)).thenReturn(false);

            // when
            // then
            assertThatThrownBy(() -> sessionService.login(new LoginRequest(EMAIL, PASSWORD), httpRequest))
                    .isInstanceOf(RateLimitException.class);

            verify(cryptoUtils).addJitter();
            verifyNoInteractions(userRepository, passwordEncoder);
        }

        @Test
        void emailRateLimited_throwsRateLimitException() {
            // given
            when(rateLimitService.isAllowed("login:ip", CLIENT_IP)).thenReturn(true);
            when(rateLimitService.isAllowed("login:email", EMAIL)).thenReturn(false);

            // when
            // then
            assertThatThrownBy(() -> sessionService.login(new LoginRequest(EMAIL, PASSWORD), httpRequest))
                    .isInstanceOf(RateLimitException.class);

            verify(cryptoUtils).addJitter();
            verifyNoInteractions(userRepository, passwordEncoder);
        }

        @Test
        void ipRateLimited_doesNotCheckEmail() {
            // given
            when(rateLimitService.isAllowed("login:ip", CLIENT_IP)).thenReturn(false);

            // when
            // then
            assertThatThrownBy(() -> sessionService.login(new LoginRequest(EMAIL, PASSWORD), httpRequest))
                    .isInstanceOf(RateLimitException.class);

            verify(rateLimitService, never()).isAllowed(eq("login:email"), any());
        }
    }

    @Nested
    class Refresh {

        private HttpServletRequest httpRequest;
        private JwtClaims validClaims;
        private RefreshSession existingSession;

        @BeforeEach
        void setUp() {
            httpRequest = mockHttpRequest();
            validClaims = JwtClaims.of(USER_ID, JTI, Instant.now(), Instant.now().plusSeconds(1209600));

            existingSession =
                    RefreshSession.builder()
                            .id(USER_ID + ":" + JTI)
                            .userId(USER_ID)
                            .sessionId(UUID.fromString(JTI))
                            .refreshTokenJtiHash(JTI_HASH)
                            .build();
        }

        @Test
        void happyPath_rotatesSessionAndReturnsNewTokens() {
            // given
            when(rateLimitService.isAllowed(eq("refresh:ip"), eq(CLIENT_IP), eq(30), eq(60), eq(60)))
                    .thenReturn(true);
            when(jwtService.validateAndParse(REFRESH_TOKEN)).thenReturn(validClaims);
            when(refreshSessionRepository.findById(USER_ID + ":" + JTI))
                    .thenReturn(Optional.of(existingSession));
            when(cryptoUtils.sha256Hash(JTI)).thenReturn(JTI_HASH);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            String newJti = UUID.randomUUID().toString();
            TokenPair newTokens = new TokenPair("new-access", "new-refresh");
            JwtClaims newRefreshClaims =
                    JwtClaims.of(USER_ID, newJti, Instant.now(), Instant.now().plusSeconds(1209600));
            when(jwtService.generateTokenPair(USER_ID)).thenReturn(newTokens);
            when(jwtService.validateAndParse("new-refresh")).thenReturn(newRefreshClaims);
            when(cryptoUtils.sha256Hash(newJti)).thenReturn("new-jti-hash");
            when(cryptoUtils.hashIpAddress(CLIENT_IP)).thenReturn("hashed-ip");
            when(cryptoUtils.hashUserAgent(USER_AGENT)).thenReturn("hashed-ua");

            // when
            LoginResult result = sessionService.refresh(REFRESH_TOKEN, httpRequest);

            // then
            assertThat(result.tokens().accessToken()).isEqualTo("new-access");
            assertThat(result.tokens().refreshToken()).isEqualTo("new-refresh");

            verify(refreshSessionRepository).delete(existingSession);
            verify(refreshSessionRepository).save(any(RefreshSession.class));
        }

        @Test
        void invalidToken_throwsAuthException() {
            // given
            when(rateLimitService.isAllowed(eq("refresh:ip"), eq(CLIENT_IP), eq(30), eq(60), eq(60)))
                    .thenReturn(true);
            when(jwtService.validateAndParse("bad-token"))
                    .thenThrow(new JwtService.InvalidTokenException("expired"));

            // when
            // then
            assertThatThrownBy(() -> sessionService.refresh("bad-token", httpRequest))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("Invalid refresh token");
        }

        @Test
        void sessionNotFound_throwsAuthException() {
            // given
            when(rateLimitService.isAllowed(eq("refresh:ip"), eq(CLIENT_IP), eq(30), eq(60), eq(60)))
                    .thenReturn(true);
            when(jwtService.validateAndParse(REFRESH_TOKEN)).thenReturn(validClaims);
            when(refreshSessionRepository.findById(USER_ID + ":" + JTI)).thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(() -> sessionService.refresh(REFRESH_TOKEN, httpRequest))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("Session not found or expired");
        }

        @Test
        void tokenReuseDetected_invalidatesAllSessionsForUser() {
            // given
            when(rateLimitService.isAllowed(eq("refresh:ip"), eq(CLIENT_IP), eq(30), eq(60), eq(60)))
                    .thenReturn(true);
            when(jwtService.validateAndParse(REFRESH_TOKEN)).thenReturn(validClaims);
            when(refreshSessionRepository.findById(USER_ID + ":" + JTI))
                    .thenReturn(Optional.of(existingSession));
            when(cryptoUtils.sha256Hash(JTI)).thenReturn("different-hash");

            // when
            // then
            assertThatThrownBy(() -> sessionService.refresh(REFRESH_TOKEN, httpRequest))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("Invalid refresh token");

            verify(refreshSessionRepository).deleteByUserId(USER_ID);
        }

        @Test
        void rateLimited_throwsRateLimitException() {
            // given
            when(rateLimitService.isAllowed(eq("refresh:ip"), eq(CLIENT_IP), eq(30), eq(60), eq(60)))
                    .thenReturn(false);

            // when
            // then
            assertThatThrownBy(() -> sessionService.refresh(REFRESH_TOKEN, httpRequest))
                    .isInstanceOf(RateLimitException.class);

            verifyNoInteractions(refreshSessionRepository);
        }

        @Test
        void userDeletedBetweenTokenValidationAndLookup_throwsAuthException() {
            // given
            when(rateLimitService.isAllowed(eq("refresh:ip"), eq(CLIENT_IP), eq(30), eq(60), eq(60)))
                    .thenReturn(true);
            when(jwtService.validateAndParse(REFRESH_TOKEN)).thenReturn(validClaims);
            when(refreshSessionRepository.findById(USER_ID + ":" + JTI))
                    .thenReturn(Optional.of(existingSession));
            when(cryptoUtils.sha256Hash(JTI)).thenReturn(JTI_HASH);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(() -> sessionService.refresh(REFRESH_TOKEN, httpRequest))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("User not found");
        }
    }

    @Nested
    class Logout {

        @Test
        void validToken_deletesSession() {
            // given
            JwtClaims claims = JwtClaims.of(USER_ID, JTI, Instant.now(), Instant.now().plusSeconds(600));
            when(jwtService.validateAndParse(REFRESH_TOKEN)).thenReturn(claims);

            // when
            sessionService.logout(REFRESH_TOKEN);

            // then
            verify(refreshSessionRepository).deleteById(USER_ID + ":" + JTI);
        }

        @Test
        void invalidToken_doesNotThrow() {
            // given
            when(jwtService.validateAndParse("garbage"))
                    .thenThrow(new JwtService.InvalidTokenException("bad"));

            // when
            sessionService.logout("garbage");

            // then
            verify(refreshSessionRepository, never()).deleteById(any());
        }
    }

    @Nested
    class InvalidateAllSessions {

        @Test
        void deletesAllSessionsForUser() {
            // given
            // when
            sessionService.invalidateAllSessions(USER_ID);

            // then
            verify(refreshSessionRepository).deleteByUserId(USER_ID);
        }
    }
}
