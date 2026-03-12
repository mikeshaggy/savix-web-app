package com.mikeshaggy.backend.auth.util.crypto;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class CryptoUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generateSecureToken(int byteLength) {
        byte[] tokenBytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    public String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public String hashIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        return sha256Hash("ip:" + ipAddress);
    }

    public String hashUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return sha256Hash("ua:" + userAgent);
    }

    public long generateJitterMs() {
        return 50 + SECURE_RANDOM.nextInt(101); // 50-150ms
    }

    public void addJitter() {
        try {
            Thread.sleep(generateJitterMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
