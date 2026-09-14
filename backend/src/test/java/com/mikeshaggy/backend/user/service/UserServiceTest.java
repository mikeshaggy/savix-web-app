package com.mikeshaggy.backend.user.service;

import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.dto.MeFeaturesResponse;
import com.mikeshaggy.backend.user.dto.MeUpdateRequest;
import com.mikeshaggy.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private UserRepository userRepository;

    @Test
    void getUserById_surfacesFlagsButNeverForecastV2Shadow() {
        // given: forecastV2Shadow is true (backend-only) alongside two client-visible flags
        UserService service = new UserService(userRepository, new FeatureFlags(true, false, true, false, true));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder()
                .id(USER_ID)
                .email("fx@example.com")
                .username("fx-user")
                .build()));

        // when
        MeFeaturesResponse features = service.getUserById(USER_ID).features();

        // then
        assertThat(features).isEqualTo(new MeFeaturesResponse(true, false, false, true));
    }

    @Test
    void updateUserById_returnsSameFeaturesShapeAsGetUserById() {
        // given: PATCH /api/me must carry the same features shape as GET /api/me (AC-3)
        UserService service = new UserService(userRepository, new FeatureFlags(true, false, true, false, true));
        User existingUser = User.builder()
                .id(USER_ID)
                .email("fx@example.com")
                .username("fx-user")
                .build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        // when
        MeFeaturesResponse features = service.updateUserById(USER_ID, new MeUpdateRequest("fx-user-renamed")).features();

        // then
        assertThat(features).isEqualTo(new MeFeaturesResponse(true, false, false, true));
    }
}
