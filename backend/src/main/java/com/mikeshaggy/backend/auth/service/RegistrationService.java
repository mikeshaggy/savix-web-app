package com.mikeshaggy.backend.auth.service;

import com.mikeshaggy.backend.auth.dto.request.RegisterRequest;
import com.mikeshaggy.backend.auth.exception.AuthException;
import com.mikeshaggy.backend.auth.domain.password.PasswordPolicyValidator;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void register(RegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();
        
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new AuthException("Registration failed");
        }

        PasswordPolicyValidator.ValidationResult validation = passwordPolicyValidator.validate(request.password());
        if (!validation.isValid()) {
            throw new AuthException(validation.getMessage());
        }

        User user = User.builder()
                .email(normalizedEmail)
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        userRepository.save(user);
        
        log.info("User registered successfully with email: {}", normalizedEmail);
    }
}
