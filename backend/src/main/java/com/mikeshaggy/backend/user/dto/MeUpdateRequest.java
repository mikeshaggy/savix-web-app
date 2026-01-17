package com.mikeshaggy.backend.user.dto;

import com.mikeshaggy.backend.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MeUpdateRequest(
        @Size(min = 2, max = 100, message = "Username must be between 2 and 100 characters")
        @NotBlank(message = "Username is required")
        String username
) {
        public void applyTo(User user) {
                user.setUsername(username);
        }
}
