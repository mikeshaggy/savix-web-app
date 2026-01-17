package com.mikeshaggy.backend.auth.service;

import com.mikeshaggy.backend.auth.domain.ratelimit.RateLimitEntry;
import com.mikeshaggy.backend.auth.repo.RateLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RateLimitRepository rateLimitRepository;

    @Value("${auth.rate-limit.max-attempts}")
    private int maxAttempts;

    @Value("${auth.rate-limit.window-seconds}")
    private int windowSeconds;

    @Value("${auth.rate-limit.lockout-seconds}")
    private int lockoutSeconds;

    public boolean isAllowed(String type, String identifier) {
        return isAllowed(type, identifier, maxAttempts, windowSeconds, lockoutSeconds);
    }

    public boolean isAllowed(String type, String identifier, int customMaxAttempts, int customWindowSeconds, int customLockoutSeconds) {
        String key = buildKey(type, identifier);
        RateLimitEntry entry = rateLimitRepository.findById(key).orElse(null);

        Instant now = Instant.now();

        if (entry == null) {
            createNewEntry(key, now, customWindowSeconds);
            return true;
        }

        if (entry.getLockedUntil() != null && now.isBefore(entry.getLockedUntil())) {
            log.warn("Rate limit lockout active for {}", key);
            return false;
        }

        Duration timeSinceFirst = Duration.between(entry.getFirstAttemptAt(), now);
        if (timeSinceFirst.getSeconds() > customWindowSeconds) {
            createNewEntry(key, now, customWindowSeconds);
            return true;
        }

        if (entry.getAttempts() < customMaxAttempts) {
            entry.setAttempts(entry.getAttempts() + 1);
            entry.setLastAttemptAt(now);
            rateLimitRepository.save(entry);
            return true;
        }

        entry.setLockedUntil(now.plusSeconds(customLockoutSeconds));
        entry.setTtl((long) (customLockoutSeconds + customWindowSeconds));
        rateLimitRepository.save(entry);
        
        log.warn("Rate limit exceeded for {}. Locked until {}", key, entry.getLockedUntil());
        return false;
    }

    public void recordSuccess(String type, String identifier) {
        String key = buildKey(type, identifier);
        rateLimitRepository.deleteById(key);
    }

    public int getRemainingAttempts(String type, String identifier) {
        String key = buildKey(type, identifier);
        RateLimitEntry entry = rateLimitRepository.findById(key).orElse(null);

        if (entry == null) {
            return maxAttempts;
        }

        Instant now = Instant.now();
        
        if (entry.getLockedUntil() != null && now.isBefore(entry.getLockedUntil())) {
            return 0;
        }

        Duration timeSinceFirst = Duration.between(entry.getFirstAttemptAt(), now);
        if (timeSinceFirst.getSeconds() > windowSeconds) {
            return maxAttempts;
        }

        return Math.max(0, maxAttempts - entry.getAttempts());
    }

    private void createNewEntry(String key, Instant now, int ttlSeconds) {
        RateLimitEntry entry = RateLimitEntry.builder()
                .key(key)
                .attempts(1)
                .firstAttemptAt(now)
                .lastAttemptAt(now)
                .ttl((long) ttlSeconds)
                .build();
        rateLimitRepository.save(entry);
    }

    private void createNewEntry(String key, Instant now) {
        createNewEntry(key, now, windowSeconds);
    }

    private String buildKey(String type, String identifier) {
        return type + ":" + identifier;
    }
}
