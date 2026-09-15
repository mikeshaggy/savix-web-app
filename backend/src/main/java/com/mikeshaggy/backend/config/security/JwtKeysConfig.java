package com.mikeshaggy.backend.config.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

@Configuration
@Slf4j
public class JwtKeysConfig {

    @Bean
    @Profile("!dev | prod")
    public ECKey ecKeyPair(
            @Value("${auth.jwt.public-key-location:}") String publicKeyLocation,
            @Value("${auth.jwt.private-key-location:}") String privateKeyLocation,
            ResourceLoader resources, Environment environment) {
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        byte[] publicKeyBytes = readPem(resources, publicKeyLocation, "PUBLIC", production);
        byte[] privateKeyBytes = readPem(resources, privateKeyLocation, "PRIVATE", production);

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            ECPublicKey publicKey = (ECPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            ECPrivateKey privateKey = (ECPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            if (!Curve.P_256.equals(Curve.forECParameterSpec(publicKey.getParams()))
                    || !Curve.P_256.equals(Curve.forECParameterSpec(privateKey.getParams()))) {
                throw new IllegalArgumentException();
            }

            // Prove the supplied pair matches without changing either key or the JWT kid.
            byte[] challenge = "savix-jwt-key-pair-check".getBytes(StandardCharsets.UTF_8);
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(challenge);
            byte[] proof = signature.sign();
            signature.initVerify(publicKey);
            signature.update(challenge);
            if (!signature.verify(proof)) {
                throw new IllegalArgumentException();
            }

            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()));
            log.info("Loaded existing JWT key pair; public key SHA-256: {}", fingerprint);
            return new ECKey.Builder(Curve.P_256, publicKey).privateKey(privateKey).build();
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Do not include provider exceptions, PEM data, or host paths in startup logs.
            throw new IllegalStateException("JWT keys invalid: require a matching EC P-256 pair (PKCS#8 private key and X.509 public key). No replacement keys were generated.");
        }
    }

    private byte[] readPem(ResourceLoader resources, String location, String kind, boolean production) {
        String setting = "auth.jwt." + kind.toLowerCase(java.util.Locale.ROOT) + "-key-location";
        if (location == null || location.isBlank() || (production && !location.startsWith("file:"))) {
            throw new IllegalStateException("JWT " + kind.toLowerCase(java.util.Locale.ROOT)
                    + " key not configured: set " + setting + " to a file: location for the existing key. No replacement keys were generated.");
        }
        try {
            Resource resource = resources.getResource(location);
            if (production && (!resource.isFile() || !Files.isRegularFile(resource.getFile().toPath()))) {
                throw new IOException();
            }
            String pem;
            try (var input = resource.getInputStream()) {
                pem = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            String begin = "-----BEGIN " + kind + " KEY-----";
            String end = "-----END " + kind + " KEY-----";
            if (!pem.startsWith(begin) || !pem.endsWith(end)) {
                throw new IllegalArgumentException();
            }
            return Base64.getDecoder().decode(pem.substring(begin.length(), pem.length() - end.length()).replaceAll("\\s", ""));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("JWT " + kind.toLowerCase(java.util.Locale.ROOT)
                    + " key missing, unreadable, or malformed: check " + setting + " and the read-only mount. No replacement keys were generated.");
        }
    }

    @Bean
    @Profile("dev & !prod")
    public ECKey devEcKeyPair() throws JOSEException {
        log.warn("USING DEVELOPMENT JWT KEYS");
        return new ECKeyGenerator(Curve.P_256).keyID("dev-key").generate();
    }
}
