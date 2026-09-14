package com.mikeshaggy.backend.user.dto;

import jakarta.validation.constraints.Size;

/** Partial update: every field is optional and {@code null} leaves the current value unchanged. */
public record MeUpdateRequest(
        @Size(min = 2, max = 100, message = "Username must be between 2 and 100 characters")
        String username,
        Integer salaryWalletId
) {
}
