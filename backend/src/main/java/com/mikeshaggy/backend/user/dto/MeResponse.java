package com.mikeshaggy.backend.user.dto;

import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.domain.UserPaydayRule;

import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String username,
        Integer salaryWalletId,
        PaydayRuleResponse paydayRule,
        MeFeaturesResponse features
) {
    public static MeResponse from(User user, UserPaydayRule paydayRule, FeatureFlags featureFlags) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getSalaryWallet() != null ? user.getSalaryWallet().getId() : null,
                paydayRule != null ? PaydayRuleResponse.from(paydayRule) : null,
                MeFeaturesResponse.from(featureFlags)
        );
    }
}
