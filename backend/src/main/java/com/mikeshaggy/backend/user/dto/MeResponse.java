package com.mikeshaggy.backend.user.dto;

import com.mikeshaggy.backend.user.domain.User;

import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String username
) {
    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername()
        );
    }
}
