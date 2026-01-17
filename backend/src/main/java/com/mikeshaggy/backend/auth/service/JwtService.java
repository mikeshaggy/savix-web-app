package com.mikeshaggy.backend.auth.service;

import com.mikeshaggy.backend.auth.domain.jwt.JwtClaims;
import com.mikeshaggy.backend.auth.dto.TokenPair;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final ECKey ecKey;

    @Value("${auth.jwt.issuer}")
    private String issuer;

    @Value("${auth.jwt.audience}")
    private String audience;

    @Value("${auth.access-token.ttl-seconds}")
    private int accessTokenTtl;

    @Value("${auth.refresh-token.ttl-seconds}")
    private int refreshTokenTtl;

    @Value("${auth.jwt.clock-skew-seconds}")
    private int clockSkewSeconds;

    public TokenPair generateTokenPair(UUID userId) {
        String accessToken = generateToken(userId, accessTokenTtl);
        String refreshToken = generateToken(userId, refreshTokenTtl);
        return new TokenPair(accessToken, refreshToken);
    }

    private String generateToken(UUID userId, int ttlSeconds) {
        try {
            Instant now = Instant.now();
            Instant expiration = now.plusSeconds(ttlSeconds);

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiration))
                    .jwtID(UUID.randomUUID().toString())
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256)
                            .keyID(ecKey.getKeyID())
                            .build(),
                    claimsSet
            );

            JWSSigner signer = new ECDSASigner(ecKey);
            signedJWT.sign(signer);

            return signedJWT.serialize();

        } catch (JOSEException e) {
            log.error("Failed to generate JWT token", e);
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    public JwtClaims validateAndParse(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            JWSVerifier verifier = new ECDSAVerifier(ecKey.toECPublicKey());
            if (!signedJWT.verify(verifier)) {
                throw new InvalidTokenException("Invalid token signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            Instant expiration = claims.getExpirationTime().toInstant();
            Instant now = Instant.now();
            if (now.isAfter(expiration.plusSeconds(clockSkewSeconds))) {
                throw new InvalidTokenException("Token has expired");
            }

            if (!issuer.equals(claims.getIssuer())) {
                throw new InvalidTokenException("Invalid token issuer");
            }

            if (!claims.getAudience().contains(audience)) {
                throw new InvalidTokenException("Invalid token audience");
            }

            return JwtClaims.of(
                    UUID.fromString(claims.getSubject()),
                    claims.getJWTID(),
                    claims.getIssueTime().toInstant(),
                    claims.getExpirationTime().toInstant());

        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            throw new InvalidTokenException("Failed to parse or verify token", e);
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }

        public InvalidTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
