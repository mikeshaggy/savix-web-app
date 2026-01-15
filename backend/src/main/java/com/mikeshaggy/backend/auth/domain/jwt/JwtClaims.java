package com.mikeshaggy.backend.auth.domain.jwt;

import java.time.Instant;
import java.util.UUID;

public record JwtClaims(
        UUID subject,
        String jti,
        Instant issuedAt,
        Instant expiresAt
) {
    public static JwtClaims of(
            UUID subject,
            String jti,
            Instant issuedAt,
            Instant expiresAt
    ) {
        if (issuedAt.isAfter(expiresAt)) {
            throw new IllegalArgumentException("issuedAt cannot be after expiresAt");
        }
        return new JwtClaims(subject, jti, issuedAt, expiresAt);
    }
}
