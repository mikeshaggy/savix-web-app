package com.mikeshaggy.backend.auth.domain.ratelimit;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.time.Instant;

@RedisHash("rate_limits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitEntry {

    @Id
    private String key;

    private int attempts;

    private Instant firstAttemptAt;

    private Instant lastAttemptAt;

    private Instant lockedUntil;

    @TimeToLive
    private Long ttl;
}
