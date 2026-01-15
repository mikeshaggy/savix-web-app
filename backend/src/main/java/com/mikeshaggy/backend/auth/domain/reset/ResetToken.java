package com.mikeshaggy.backend.auth.domain.reset;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.time.Instant;
import java.util.UUID;

@RedisHash("reset_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetToken {

    @Id
    private String id;

    @Indexed
    private UUID userId;

    private String tokenHash;

    private Instant createdAt;

    private Instant expiresAt;

    private boolean used;

    @TimeToLive
    private Long ttl;
}
