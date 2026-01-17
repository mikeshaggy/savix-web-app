package com.mikeshaggy.backend.user.service;

import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.dto.MeResponse;
import com.mikeshaggy.backend.user.dto.MeUpdateRequest;
import com.mikeshaggy.backend.user.repo.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public MeResponse getUserById(UUID userId) {
        User user = getUserOrThrow(userId);
        return MeResponse.from(user);
    }

    @Transactional
    public MeResponse updateUserById(UUID userId, MeUpdateRequest request) {
        User user = getUserOrThrow(userId);

        request.applyTo(user);
        log.info("Updated user id: {} to username: '{}'", userId, request.username());

        User updatedUser = userRepository.save(user);
        return MeResponse.from(updatedUser);
    }

    @Transactional
    public void deleteUserById(UUID userId) {
        User user = getUserOrThrow(userId);

        log.info("Deleting user '{}' with id: {} and all related data",
                user.getUsername(), user.getId());

        userRepository.delete(user);
    }

    public User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
    }
}
