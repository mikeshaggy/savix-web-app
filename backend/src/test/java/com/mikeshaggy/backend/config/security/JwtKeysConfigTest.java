package com.mikeshaggy.backend.config.security;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.nimbusds.jose.jwk.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtKeysConfigTest {
    @TempDir Path directory;
    private KeyPair original;
    private Path privateKey;
    private Path publicKey;

    @BeforeEach
    void keys() throws Exception {
        original = pair("secp256r1");
        privateKey = directory.resolve("private.pem");
        publicKey = directory.resolve("public.pem");
        write(privateKey, "PRIVATE", original.getPrivate().getEncoded());
        write(publicKey, "PUBLIC", original.getPublic().getEncoded());
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "prod,dev", "dev,prod"})
    void productionLoadsSameIdentityEvenWithDevProfile(String profiles) {
        context(profiles).run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(ECKey.class);
            ECKey loaded = ctx.getBean(ECKey.class);
            assertThat(loaded.toECPrivateKey().getEncoded()).isEqualTo(original.getPrivate().getEncoded());
            assertThat(loaded.toECPublicKey().getEncoded()).isEqualTo(original.getPublic().getEncoded());
            assertThat(loaded.getKeyID()).isNull();
            assertThat(ctx).doesNotHaveBean("devEcKeyPair");
        });
    }

    @Test
    void tokensRemainValidAfterReloadingTheSameFiles() {
        UUID user = UUID.randomUUID();
        context("prod").run(before -> {
            var tokens = service(before.getBean(ECKey.class)).generateTokenPair(user);
            context("prod").run(after -> {
                var reloaded = service(after.getBean(ECKey.class));
                assertThat(reloaded.validateAndParse(tokens.accessToken()).subject()).isEqualTo(user);
                assertThat(reloaded.validateAndParse(tokens.refreshToken()).subject()).isEqualTo(user);
            });
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"public", "private"})
    void missingFileFailsWithoutGeneratingReplacement(String kind) throws Exception {
        Files.delete(kind.equals("private") ? privateKey : publicKey);
        context("prod,dev").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasRootCauseMessage("JWT " + kind
                    + " key missing, unreadable, or malformed: check auth.jwt." + kind
                    + "-key-location and the read-only mount. No replacement keys were generated.");
        });
        assertThat(Files.exists(kind.equals("private") ? privateKey : publicKey)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "classpath:jwt-public-key.pem", "https://example.invalid/key.pem"})
    void productionRejectsUnsetAndNonFileLocations(String location) {
        context("prod").withPropertyValues("auth.jwt.public-key-location=" + location).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasRootCauseMessage("JWT public key not configured: set auth.jwt.public-key-location to a file: location for the existing key. No replacement keys were generated.");
        });
    }

    @Test
    void directoriesAreNotKeyFiles() {
        context("prod").withPropertyValues("auth.jwt.private-key-location=" + directory.toUri()).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void malformedKeyFailsWithoutLoggingItsContents() throws Exception {
        Files.writeString(privateKey, "DO-NOT-LOG-THIS-INVALID-TEST-KEY");
        context("prod").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("JWT private key missing, unreadable, or malformed");
            assertThat(ctx.getStartupFailure().getCause().getCause().getMessage())
                    .doesNotContain("DO-NOT-LOG", directory.toString());
        });
    }

    @Test
    void mismatchedKeysFail() throws Exception {
        write(privateKey, "PRIVATE", pair("secp256r1").getPrivate().getEncoded());
        context("prod").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("JWT keys invalid: require a matching EC P-256 pair");
        });
    }

    @Test
    void wrongCurveFails() throws Exception {
        var wrong = pair("secp384r1");
        write(privateKey, "PRIVATE", wrong.getPrivate().getEncoded());
        write(publicKey, "PUBLIC", wrong.getPublic().getEncoded());
        context("prod").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("JWT keys invalid: require a matching EC P-256 pair");
        });
    }

    @Test
    void developmentCanStillUseEphemeralKeys() {
        context("dev").withPropertyValues("auth.jwt.public-key-location=", "auth.jwt.private-key-location=").run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(ECKey.class);
            assertThat(ctx.getBean(ECKey.class).getKeyID()).isEqualTo("dev-key");
        });
    }

    private ApplicationContextRunner context(String profiles) {
        return new ApplicationContextRunner().withUserConfiguration(JwtKeysConfig.class)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profiles.split(",")))
                .withPropertyValues("auth.jwt.private-key-location=" + privateKey.toUri(),
                        "auth.jwt.public-key-location=" + publicKey.toUri());
    }

    private static KeyPair pair(String curve) throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(curve));
        return generator.generateKeyPair();
    }

    private static void write(Path file, String kind, byte[] bytes) throws Exception {
        Files.writeString(file, "-----BEGIN " + kind + " KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + kind + " KEY-----\n");
    }

    private static JwtService service(ECKey key) {
        var service = new JwtService(key);
        ReflectionTestUtils.setField(service, "issuer", "test-issuer");
        ReflectionTestUtils.setField(service, "audience", "test-audience");
        ReflectionTestUtils.setField(service, "accessTokenTtl", 600);
        ReflectionTestUtils.setField(service, "refreshTokenTtl", 1209600);
        return service;
    }
}
