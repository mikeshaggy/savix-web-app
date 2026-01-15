package com.mikeshaggy.backend.auth.util.cookie;

import com.mikeshaggy.backend.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AuthCookieManager {

    private final CookieUtils cookieUtils;

    public String extractToken(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(cookie -> cookie.getName().equals(cookieName))
                    .findFirst()
                    .map(Cookie::getValue)
                    .orElse(null);
        }
        return null;
    }

    public String extractAccessToken(HttpServletRequest request) {
        return extractToken(request, cookieUtils.getAccessTokenCookieName());
    }

    public String extractRefreshToken(HttpServletRequest request) {
        return extractToken(request, cookieUtils.getRefreshTokenCookieName());
    }

    public void writeTokens(HttpServletResponse response, AuthService.Tokens tokens) {
        response.addCookie(cookieUtils.createAccessTokenCookie(tokens.accessToken()));
        response.addCookie(cookieUtils.createRefreshTokenCookie(tokens.refreshToken()));
    }

    public void clearTokens(HttpServletResponse response) {
        response.addCookie(cookieUtils.deleteAccessTokenCookie());
        response.addCookie(cookieUtils.deleteRefreshTokenCookie());
    }
}
