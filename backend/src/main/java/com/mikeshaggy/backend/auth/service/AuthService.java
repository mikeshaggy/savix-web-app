package com.mikeshaggy.backend.auth.service;

import com.mikeshaggy.backend.auth.dto.LoginResult;
import com.mikeshaggy.backend.auth.dto.TokenPair;
import com.mikeshaggy.backend.auth.dto.request.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final RegistrationService registrationService;
    private final SessionService sessionService;
    private final PasswordResetService passwordResetService;

    @Transactional
    public void register(RegisterRequest request) {
        registrationService.register(request);
    }

    @Transactional
    public LoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
        LoginResult result = sessionService.login(request, httpRequest);
        return new LoginResult(
                new TokenPair(result.tokens().accessToken(), result.tokens().refreshToken()),
                result.user()
        );
    }

    @Transactional
    public LoginResult refresh(String refreshToken, HttpServletRequest httpRequest) {
        LoginResult result = sessionService.refresh(refreshToken, httpRequest);
        return new LoginResult(
                new TokenPair(result.tokens().accessToken(), result.tokens().refreshToken()),
                result.user()
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        sessionService.logout(refreshToken);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        passwordResetService.forgotPassword(request, httpRequest);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        passwordResetService.changePassword(userId, request);
    }
}
