package com.mikeshaggy.backend.user.service;

import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.domain.UserPaydayRule;
import com.mikeshaggy.backend.user.dto.MeResponse;
import com.mikeshaggy.backend.user.dto.MeUpdateRequest;
import com.mikeshaggy.backend.user.dto.PaydayRuleRequest;
import com.mikeshaggy.backend.user.repository.UserPaydayRuleRepository;
import com.mikeshaggy.backend.user.repository.UserRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
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
    private final WalletRepository walletRepository;
    private final UserPaydayRuleRepository paydayRuleRepository;
    private final FeatureFlags featureFlags;

    public MeResponse getUserById(UUID userId) {
        User user = getUserOrThrow(userId);
        return toResponse(user);
    }

    @Transactional
    public MeResponse updateUserById(UUID userId, MeUpdateRequest request) {
        User user = getUserOrThrow(userId);

        if (request.username() != null) {
            if (request.username().isBlank()) {
                throw new IllegalArgumentException("Username must not be blank");
            }
            user.setUsername(request.username());
        }
        if (request.salaryWalletId() != null) {
            user.setSalaryWallet(resolveSalaryWallet(request.salaryWalletId(), userId));
        }
        log.info("User updated: userId={}", userId);

        User updatedUser = userRepository.save(user);
        return toResponse(updatedUser);
    }

    @Transactional
    public MeResponse setPaydayRule(UUID userId, PaydayRuleRequest request) {
        User user = getUserOrThrow(userId);

        UserPaydayRule rule = paydayRuleRepository.findById(userId)
                .orElseGet(() -> UserPaydayRule.builder().userId(userId).build());
        rule.setDayOfMonth(request.dayOfMonth());
        rule.setWeekendShift(request.weekendShiftOrDefault());
        UserPaydayRule savedRule = paydayRuleRepository.save(rule);
        log.info("Payday rule set: userId={}, dayOfMonth={}, weekendShift={}",
                userId, savedRule.getDayOfMonth(), savedRule.getWeekendShift());

        return MeResponse.from(user, savedRule, featureFlags);
    }

    @Transactional
    public MeResponse deletePaydayRule(UUID userId) {
        User user = getUserOrThrow(userId);

        paydayRuleRepository.deleteById(userId);
        log.info("Payday rule removed: userId={}", userId);

        return MeResponse.from(user, null, featureFlags);
    }

    @Transactional
    public void deleteUserById(UUID userId) {
        User user = getUserOrThrow(userId);

        log.info("User deleted: userId={}", user.getId());

        userRepository.delete(user);
    }

    public User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
    }

    private MeResponse toResponse(User user) {
        UserPaydayRule paydayRule = paydayRuleRepository.findById(user.getId()).orElse(null);
        return MeResponse.from(user, paydayRule, featureFlags);
    }

    /** The salary wallet must be one of the user's own regular wallets — never a fund wallet. */
    private Wallet resolveSalaryWallet(Integer walletId, UUID userId) {
        Wallet wallet = walletRepository.findByIdAndUserId(walletId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found with id: " + walletId));
        if (wallet.isFund()) {
            throw new IllegalArgumentException("Salary wallet cannot be a fund wallet.");
        }
        return wallet;
    }
}
