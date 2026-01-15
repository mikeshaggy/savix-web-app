package com.mikeshaggy.backend.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserDTO(
        UUID id,
        String username,
        String email,
        Instant createdAt
) {}
