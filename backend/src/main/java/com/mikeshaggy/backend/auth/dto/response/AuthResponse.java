package com.mikeshaggy.backend.auth.dto.response;

public record AuthResponse(String message) {

    public static AuthResponse of(String message) {
        return new AuthResponse(message);
    }
}
