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
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@Slf4j
public class JwtKeysConfig {

    @Value("${auth.jwt.public-key-location}")
    private Resource publicKeyResource;

    @Value("${auth.jwt.private-key-location}")
    private Resource privateKeyResource;

    @Bean
    @Profile("!dev")
    public ECKey ecKeyPair() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        if (publicKeyResource == null || privateKeyResource == null) {
            throw new IllegalStateException(
                    "JWT keys not configured. Set auth.jwt.public-key-location and auth.jwt.private-key-location"
            );
        }

        String publicKeyPem = new String(publicKeyResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        String privateKeyPem = new String(privateKeyResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyPem);
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyPem);

        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);

        ECPublicKey publicKey = (ECPublicKey) keyFactory.generatePublic(publicKeySpec);
        ECPrivateKey privateKey = (ECPrivateKey) keyFactory.generatePrivate(privateKeySpec);

        return new ECKey.Builder(Curve.P_256, publicKey)
                .privateKey(privateKey)
                .build();
    }

    @Bean
    @Profile("dev")
    public ECKey devEcKeyPair() throws JOSEException {
        log.warn("=".repeat(80));
        log.warn("USING DEVELOPMENT JWT KEYS");
        log.warn("=".repeat(80));
        
        return new ECKeyGenerator(Curve.P_256)
                .keyID("dev-key")
                .generate();
    }
}
