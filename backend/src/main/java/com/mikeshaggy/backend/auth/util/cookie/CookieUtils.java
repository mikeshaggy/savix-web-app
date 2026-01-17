package com.mikeshaggy.backend.auth.util.cookie;

import jakarta.servlet.http.Cookie;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CookieUtils {

    @Value("${auth.cookie.secure}")
    private boolean secure;

    @Value("${auth.cookie.domain}")
    private String domain;

    @Getter
    @Value("${auth.access-token.cookie-name}")
    private String accessTokenCookieName;

    @Getter
    @Value("${auth.refresh-token.cookie-name}")
    private String refreshTokenCookieName;

    @Value("${auth.access-token.ttl-seconds}")
    private int accessTokenTtl;

    @Value("${auth.refresh-token.ttl-seconds}")
    private int refreshTokenTtl;

    public Cookie createAccessTokenCookie(String token) {
        return createCookie(accessTokenCookieName, token, accessTokenTtl, "/");
    }

    public Cookie createRefreshTokenCookie(String token) {
        return createCookie(refreshTokenCookieName, token, refreshTokenTtl, "/");
    }

    public Cookie deleteAccessTokenCookie() {
        return createCookie(accessTokenCookieName, "", 0, "/");
    }

    public Cookie deleteRefreshTokenCookie() {
        return createCookie(refreshTokenCookieName, "", 0, "/");
    }

    private Cookie createCookie(String name, String value, int maxAgeSeconds, String path) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath(path);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");
        
        if (domain != null && !domain.isBlank()) {
            cookie.setDomain(domain);
        }
        
        return cookie;
    }
}
