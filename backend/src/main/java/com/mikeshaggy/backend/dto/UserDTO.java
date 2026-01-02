package com.mikeshaggy.backend.dto;

import java.time.Instant;

public record UserDTO(
        Integer id,
        String username,
        Instant createdAt
) {}
