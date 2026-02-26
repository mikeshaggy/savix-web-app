package com.mikeshaggy.backend.transaction.service;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.Type;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.dto.TransactionCreateRequest;
import com.mikeshaggy.backend.transaction.dto.TransactionResponse;
import com.mikeshaggy.backend.transaction.dto.TransactionUpdateRequest;
import com.mikeshaggy.backend.transaction.repo.TransactionRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletBalanceService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final WalletBalanceService walletBalanceService;
    private final CategoryService categoryService;

    public List<TransactionResponse> getTransactionsForUser(UUID userId) {
        return transactionRepository.findAllByWalletUserId(userId).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    public List<TransactionResponse> getTransactionsByWalletIdForUser(Integer walletId, UUID userId) {
        walletService.getWalletEntityByIdForUser(walletId, userId);
        
        return transactionRepository.findByWalletIdAndWalletUserId(walletId, userId).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    public TransactionResponse getTransactionByIdForUser(Long id, UUID userId) {
        Transaction transaction = getTransactionOrThrowForUser(id, userId);
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public TransactionResponse createTransaction(TransactionCreateRequest request, UUID userId) {
        Wallet wallet = walletService.getWalletEntityByIdForUser(request.walletId(), userId);
        
        Category category = categoryService.getCategoryEntityByIdForUser(request.categoryId(), userId);

        validateImportance(request.importance(), category.getType());

        Transaction transaction = request.toEntity();
        transaction.setWallet(wallet);
        transaction.setCategory(category);

        walletBalanceService.applyTransaction(wallet.getId(), transaction.getAmount(), category.getType(), userId);

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Created transaction '{}' (id: {}) for wallet: {}, amount: {}, type: {}",
                savedTransaction.getTitle(), savedTransaction.getId(), wallet.getId(),
                savedTransaction.getAmount(), category.getType());

        return TransactionResponse.from(savedTransaction);
    }

    @Transactional
    public TransactionResponse updateTransaction(Long id, TransactionUpdateRequest request, UUID userId) {
        Transaction existingTransaction = getTransactionOrThrowForUser(id, userId);

        BigDecimal oldAmount = existingTransaction.getAmount();
        Type oldType = existingTransaction.getCategory().getType();
        Wallet oldWallet = existingTransaction.getWallet();

        Wallet newWallet = oldWallet;
        if (!request.walletId().equals(oldWallet.getId())) {
            newWallet = walletService.getWalletEntityByIdForUser(request.walletId(), userId);
        }

        Category newCategory = existingTransaction.getCategory();
        if (!request.categoryId().equals(existingTransaction.getCategory().getId())) {
            newCategory = categoryService.getCategoryEntityByIdForUser(request.categoryId(), userId);
        }

        validateImportance(request.importance(), newCategory.getType());

        request.applyTo(existingTransaction);
        existingTransaction.setWallet(newWallet);
        existingTransaction.setCategory(newCategory);

        Transaction updatedTransaction = transactionRepository.save(existingTransaction);

        adjustWalletBalancesOnUpdate(
                oldWallet, oldAmount, oldType,
                newWallet, updatedTransaction.getAmount(), newCategory.getType(),
                userId
        );

        log.info("Updated transaction id: {} to title: '{}', amount: {}",
                id, updatedTransaction.getTitle(), updatedTransaction.getAmount());

        return TransactionResponse.from(updatedTransaction);
    }

    private void adjustWalletBalancesOnUpdate(
            Wallet oldWallet, BigDecimal oldAmount, Type oldType,
            Wallet newWallet, BigDecimal newAmount, Type newType,
            UUID userId) {
        walletBalanceService.reverseTransaction(oldWallet.getId(), oldAmount, oldType, userId);
        walletBalanceService.applyTransaction(newWallet.getId(), newAmount, newType, userId);
    }

    @Transactional
    public void deleteTransaction(Long id, UUID userId) {
        Transaction transaction = getTransactionOrThrowForUser(id, userId);
        Wallet wallet = transaction.getWallet();

        walletBalanceService.reverseTransaction(
                wallet.getId(),
                transaction.getAmount(),
                transaction.getCategory().getType(),
                userId
        );

        log.info("Deleting transaction '{}' (id: {}) from wallet: {}, rolled back balance",
                transaction.getTitle(), id, wallet.getId());

        transactionRepository.delete(transaction);
    }

    private void validateImportance(Importance importance, Type categoryType) {
        if (categoryType == Type.INCOME && importance != null) {
            throw new IllegalArgumentException("Importance must be null for INCOME transactions");
        }
        if (categoryType == Type.EXPENSE && importance == null) {
            throw new IllegalArgumentException("Importance is required for EXPENSE transactions");
        }
    }

    private Transaction getTransactionOrThrowForUser(Long id, UUID userId) {
        return transactionRepository.findByIdAndWalletUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + id));
    }

    public List<Transaction> getTransactionsForWalletAndPeriod(Integer walletId, PeriodDto period) {
        return transactionRepository
                .findByWalletIdAndTransactionDateBetween(walletId, period.startDate(), period.endDate());
    }

    public List<Transaction> getTransactionsForWalletAndComparePeriod(Integer walletId, PeriodDto comparePeriod) {
        if (comparePeriod == null) {
            return Collections.emptyList();
        }
        return getTransactionsForWalletAndPeriod(walletId, comparePeriod);
    }
}
