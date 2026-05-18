package com.mikeshaggy.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mikeshaggy.backend.auth.domain.jwt.JwtClaims;
import com.mikeshaggy.backend.auth.dto.TokenPair;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static ECKey ecKey;
    private static ECKey otherKey;

    private JwtService jwtService;

    private static final String ISSUER = "savix-backend-test";
    private static final String AUDIENCE = "savix-frontend-test";
    private static final int ACCESS_TTL = 600;
    private static final int REFRESH_TTL = 1209600;
    private static final int CLOCK_SKEW = 30;

    @BeforeAll
    static void generateKeys() throws JOSEException {
        ecKey = new ECKeyGenerator(Curve.P_256).keyID("test-key").generate();
        otherKey = new ECKeyGenerator(Curve.P_256).keyID("other-key").generate();
    }

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(ecKey);
        ReflectionTestUtils.setField(jwtService, "issuer", ISSUER);
        ReflectionTestUtils.setField(jwtService, "audience", AUDIENCE);
        ReflectionTestUtils.setField(jwtService, "accessTokenTtl", ACCESS_TTL);
        ReflectionTestUtils.setField(jwtService, "refreshTokenTtl", REFRESH_TTL);
        ReflectionTestUtils.setField(jwtService, "clockSkewSeconds", CLOCK_SKEW);
    }

    @Nested
    class GenerateTokenPair {

        @Test
        void producesTwoDistinctTokens() {
            // given
            UUID userId = UUID.randomUUID();

            // when
            TokenPair pair = jwtService.generateTokenPair(userId);

            // then
            assertThat(pair.accessToken()).isNotBlank();
            assertThat(pair.refreshToken()).isNotBlank();
            assertThat(pair.accessToken()).isNotEqualTo(pair.refreshToken());
        }

        @Test
        void accessTokenContainsExpectedClaims() {
            // given
            UUID userId = UUID.randomUUID();
            Instant before = Instant.now();

            // when
            TokenPair pair = jwtService.generateTokenPair(userId);
            JwtClaims claims = jwtService.validateAndParse(pair.accessToken());

            // then
            assertThat(claims.subject()).isEqualTo(userId);
            assertThat(claims.jti()).isNotBlank();
            assertThat(claims.issuedAt()).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
            assertThat(claims.expiresAt())
                    .isBetween(before.plusSeconds(ACCESS_TTL - 2), Instant.now().plusSeconds(ACCESS_TTL + 2));
        }

        @Test
        void refreshTokenContainsExpectedClaims() {
            // given
            UUID userId = UUID.randomUUID();
            Instant before = Instant.now();

            // when
            TokenPair pair = jwtService.generateTokenPair(userId);
            JwtClaims claims = jwtService.validateAndParse(pair.refreshToken());

            // then
            assertThat(claims.subject()).isEqualTo(userId);
            assertThat(claims.jti()).isNotBlank();
            assertThat(claims.expiresAt())
                    .isBetween(
                            before.plusSeconds(REFRESH_TTL - 2), Instant.now().plusSeconds(REFRESH_TTL + 2));
        }

        @Test
        void eachTokenPairHasUniqueJtiValues() {
            // given
            UUID userId = UUID.randomUUID();

            // when
            TokenPair pair1 = jwtService.generateTokenPair(userId);
            TokenPair pair2 = jwtService.generateTokenPair(userId);

            JwtClaims access1 = jwtService.validateAndParse(pair1.accessToken());
            JwtClaims access2 = jwtService.validateAndParse(pair2.accessToken());
            JwtClaims refresh1 = jwtService.validateAndParse(pair1.refreshToken());
            JwtClaims refresh2 = jwtService.validateAndParse(pair2.refreshToken());

            // then
            assertThat(access1.jti())
                    .isNotEqualTo(access2.jti())
                    .isNotEqualTo(refresh1.jti())
                    .isNotEqualTo(refresh2.jti());
        }
    }

    @Nested
    class ValidateAndParse {

        @Test
        void validTokenReturnsCorrectClaims() {
            // given
            UUID userId = UUID.randomUUID();

            // when
            TokenPair pair = jwtService.generateTokenPair(userId);
            JwtClaims claims = jwtService.validateAndParse(pair.accessToken());

            // then
            assertThat(claims.subject()).isEqualTo(userId);
            assertThat(claims.jti()).isNotBlank();
            assertThat(claims.issuedAt()).isNotNull();
            assertThat(claims.expiresAt()).isAfter(claims.issuedAt());
        }

        @Test
        void expiredTokenThrowsInvalidTokenException() throws JOSEException {
            // given
            String expiredToken =
                    buildSignedToken(
                            ecKey,
                            ISSUER,
                            AUDIENCE,
                            Instant.now().minusSeconds(3600),
                            Instant.now().minusSeconds(3000));

            // when
            // then
            assertThatThrownBy(() -> jwtService.validateAndParse(expiredToken))
                    .isInstanceOf(JwtService.InvalidTokenException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        void tokenWithinClockSkewIsAccepted() throws JOSEException {
            // token expired 10 seconds ago, within the 30-second clock skew
            // given
            String token =
                    buildSignedToken(
                            ecKey,
                            ISSUER,
                            AUDIENCE,
                            Instant.now().minusSeconds(ACCESS_TTL + 10),
                            Instant.now().minusSeconds(10));

            // when
            JwtClaims claims = jwtService.validateAndParse(token);

            // then
            assertThat(claims.subject()).isNotNull();
        }

        @Test
        void tokenBeyondClockSkewIsRejected() throws JOSEException {
            // token expired 60 seconds ago, beyond the 30-second clock skew
            // given
            String token =
                    buildSignedToken(
                            ecKey,
                            ISSUER,
                            AUDIENCE,
                            Instant.now().minusSeconds(ACCESS_TTL + 60),
                            Instant.now().minusSeconds(60));

            // when
            // then
            assertThatThrownBy(() -> jwtService.validateAndParse(token))
                    .isInstanceOf(JwtService.InvalidTokenException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        void wrongSignatureKeyThrowsInvalidTokenException() throws JOSEException {
            // given
            String tokenFromOtherKey =
                    buildSignedToken(
                            otherKey, ISSUER, AUDIENCE, Instant.now(), Instant.now().plusSeconds(ACCESS_TTL));

            // when
            // then
            assertThatThrownBy(() -> jwtService.validateAndParse(tokenFromOtherKey))
                    .isInstanceOf(JwtService.InvalidTokenException.class)
                    .hasMessageContaining("signature");
        }

        @Test
        void wrongIssuerThrowsInvalidTokenException() throws JOSEException {
            // given
            String token =
                    buildSignedToken(
                            ecKey,
                            "wrong-issuer",
                            AUDIENCE,
                            Instant.now(),
                            Instant.now().plusSeconds(ACCESS_TTL));

            // when
            // then
            assertThatThrownBy(() -> jwtService.validateAndParse(token))
                    .isInstanceOf(JwtService.InvalidTokenException.class)
                    .hasMessageContaining("issuer");
        }

        @Test
        void wrongAudienceThrowsInvalidTokenException() throws JOSEException {
            // given
            String token =
                    buildSignedToken(
                            ecKey,
                            ISSUER,
                            "wrong-audience",
                            Instant.now(),
                            Instant.now().plusSeconds(ACCESS_TTL));

            // when
            // then
            assertThatThrownBy(() -> jwtService.validateAndParse(token))
                    .isInstanceOf(JwtService.InvalidTokenException.class)
                    .hasMessageContaining("audience");
        }

        @Test
        void malformedStringThrowsInvalidTokenException() {
            // given
            // when
            // then
            assertThatThrownBy(() -> jwtService.validateAndParse("not.a.jwt"))
                    .isInstanceOf(JwtService.InvalidTokenException.class);
        }

        @Test
        void completeGarbageThrowsInvalidTokenException() {
            // given
            // when
            // then
            assertThatThrownBy(() -> jwtService.validateAndParse("garbage"))
                    .isInstanceOf(JwtService.InvalidTokenException.class);
        }

        @Test
        void emptyStringThrowsInvalidTokenException() {
            // given
            // when
            // then
            assertThatThrownBy(() -> jwtService.validateAndParse(""))
                    .isInstanceOf(JwtService.InvalidTokenException.class);
        }
    }

    private static String buildSignedToken(
            ECKey signingKey, String issuer, String audience, Instant issuedAt, Instant expiration)
            throws JOSEException {
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject(UUID.randomUUID().toString())
                        .issuer(issuer)
                        .audience(audience)
                        .issueTime(Date.from(issuedAt))
                        .expirationTime(Date.from(expiration))
                        .jwtID(UUID.randomUUID().toString())
                        .build();

        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signingKey.getKeyID()).build(), claims);

        JWSSigner signer = new ECDSASigner(signingKey);
        jwt.sign(signer);
        return jwt.serialize();
    }
}
