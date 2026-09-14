package com.mikeshaggy.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.auth.domain.jwt.JwtClaims;
import com.mikeshaggy.backend.auth.domain.jwt.TokenType;
import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT Authentication Filter Tests")
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private AuthCookieManager cookieManager;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    private static final String TOKEN = "the.jwt.token";
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, cookieManager);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private JwtClaims claims(TokenType tokenType) {
        return JwtClaims.of(USER_ID, UUID.randomUUID().toString(), tokenType,
                Instant.now(), Instant.now().plusSeconds(600));
    }

    @Test
    @DisplayName("Access token authenticates the request")
    void accessToken_authenticatesRequest() throws ServletException, IOException {
        when(cookieManager.extractAccessToken(request)).thenReturn(TOKEN);
        when(jwtService.validateAndParse(TOKEN)).thenReturn(claims(TokenType.ACCESS));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo(USER_ID.toString());
    }

    @Test
    @DisplayName("Refresh token is rejected for API authentication")
    void refreshToken_doesNotAuthenticate() throws ServletException, IOException {
        when(cookieManager.extractAccessToken(request)).thenReturn(TOKEN);
        when(jwtService.validateAndParse(TOKEN)).thenReturn(claims(TokenType.REFRESH));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Token with unknown type is rejected for API authentication")
    void unknownTypeToken_doesNotAuthenticate() throws ServletException, IOException {
        when(cookieManager.extractAccessToken(request)).thenReturn(TOKEN);
        when(jwtService.validateAndParse(TOKEN)).thenReturn(claims(TokenType.UNKNOWN));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Invalid token leaves the request unauthenticated")
    void invalidToken_doesNotAuthenticate() throws ServletException, IOException {
        when(cookieManager.extractAccessToken(request)).thenReturn(TOKEN);
        when(jwtService.validateAndParse(TOKEN))
                .thenThrow(new JwtService.InvalidTokenException("invalid"));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Missing access cookie leaves the request unauthenticated")
    void missingToken_doesNotAuthenticate() throws ServletException, IOException {
        when(cookieManager.extractAccessToken(request)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
