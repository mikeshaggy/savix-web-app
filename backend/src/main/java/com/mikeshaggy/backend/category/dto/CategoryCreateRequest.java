package com.mikeshaggy.backend.category.dto;

import com.mikeshaggy.backend.category.domain.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "Name must not exceed 50 characters")
        String name,

        @NotNull(message = "Type is required")
        CategoryType type,

        @Size(max = 16, message = "Emoji must not exceed 16 characters")
        String emoji
) {}
