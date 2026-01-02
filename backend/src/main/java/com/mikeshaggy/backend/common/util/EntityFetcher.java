package com.mikeshaggy.backend.common.util;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.category.repo.CategoryRepository;
import com.mikeshaggy.backend.transaction.repo.TransactionRepository;
import com.mikeshaggy.backend.user.repo.UserRepository;
import com.mikeshaggy.backend.wallet.repo.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntityFetcher {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public User getUserOrThrow(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
    }

    public User getUserOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));
    }

    public Transaction getTransactionOrThrow(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + transactionId));
    }

    public Wallet getWalletOrThrow(Integer walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found with id: " + walletId));
    }

    public Category getCategoryOrThrow(Integer categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + categoryId));
    }

    public void validateUserExists(Integer userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }
    }

    public void validateWalletExists(Integer walletId) {
        if (!walletRepository.existsById(walletId)) {
            throw new EntityNotFoundException("Wallet not found with id: " + walletId);
        }
    }
}
