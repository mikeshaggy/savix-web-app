package com.mikeshaggy.backend.auth.api;

import com.mikeshaggy.backend.auth.dto.request.*;
import com.mikeshaggy.backend.auth.dto.response.AuthResponse;
import com.mikeshaggy.backend.auth.service.AuthService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final AuthCookieManager cookieManager;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthResponse.of("Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthService.LoginResult result = authService.login(request, httpRequest);

        cookieManager.writeTokens(httpResponse, result.tokens());

        return ResponseEntity.ok(AuthResponse.of("Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String refreshToken = cookieManager.extractRefreshToken(httpRequest);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.of("No refresh token provided"));
        }

        AuthService.LoginResult result = authService.refresh(refreshToken, httpRequest);

        cookieManager.writeTokens(httpResponse, result.tokens());

        return ResponseEntity.ok(AuthResponse.of("Token refreshed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String refreshToken = cookieManager.extractRefreshToken(httpRequest);
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        cookieManager.clearTokens(httpResponse);

        return ResponseEntity.ok(AuthResponse.of("Logout successful"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        authService.forgotPassword(request, httpRequest);

        return ResponseEntity.accepted()
                .body(AuthResponse.of("If the email exists, a password reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(AuthResponse.of("Password reset successful"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication,
            HttpServletResponse httpResponse) {
        UUID userId = UUID.fromString(authentication.getName());
        authService.changePassword(userId, request);

        cookieManager.clearTokens(httpResponse);

        return ResponseEntity.ok(AuthResponse.of("Password changed successfully. Please log in again."));
    }
}
