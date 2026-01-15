package com.mikeshaggy.backend.auth.domain.session;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.time.Instant;
import java.util.UUID;

@RedisHash("refresh_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshSession {

    @Id
    private String id;

    @Indexed
    private UUID userId;

    private UUID sessionId;

    private String refreshTokenJtiHash;

    private Instant createdAt;

    private Instant expiresAt;

    private Instant lastUsedAt;

    private String ipHash;

    private String userAgentHash;

    @TimeToLive
    private Long ttl;
}
