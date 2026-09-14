package com.mikeshaggy.backend.user.dto;

import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.user.domain.User;

import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String username,
        MeFeaturesResponse features
) {
    public static MeResponse from(User user, FeatureFlags featureFlags) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                MeFeaturesResponse.from(featureFlags)
        );
    }
}
