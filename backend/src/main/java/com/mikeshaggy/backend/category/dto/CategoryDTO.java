package com.mikeshaggy.backend.category.dto;

import com.mikeshaggy.backend.category.domain.Type;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CategoryDTO(
        Integer id,

        @NotNull(message = "User ID is required")
        UUID userId,

        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "Name must not exceed 50 characters")
        String name,

        @NotNull(message = "Type is required")
        Type type,
        Instant createdAt
) {}
