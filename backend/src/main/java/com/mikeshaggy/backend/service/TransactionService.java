package com.mikeshaggy.backend.service;

import com.mikeshaggy.backend.domain.transaction.Category;
import com.mikeshaggy.backend.domain.transaction.Transaction;
import com.mikeshaggy.backend.domain.transaction.Type;
import com.mikeshaggy.backend.domain.transaction.Wallet;
import com.mikeshaggy.backend.dto.TransactionDTO;
import com.mikeshaggy.backend.mapper.TransactionMapper;
import com.mikeshaggy.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final WalletService walletService;
    private final EntityFetchingService entityFetchingService;

    public List<TransactionDTO> getAllTransactions() {
        return mapToDTO(transactionRepository.findAll());
    }

    public List<TransactionDTO> getTransactionsByWalletId(Integer walletId) {
        entityFetchingService.validateWalletExists(walletId);
        return mapToDTO(transactionRepository.findByWalletId(walletId));
    }

    public TransactionDTO getTransactionById(Long id) {
        Transaction transaction = entityFetchingService.getTransactionOrThrow(id);
        return transactionMapper.toDTO(transaction);
    }

    @Transactional
    public TransactionDTO createTransaction(TransactionDTO transactionDTO) {
        Wallet wallet = entityFetchingService.getWalletOrThrow(transactionDTO.walletId());
        Category category = entityFetchingService.getCategoryOrThrow(transactionDTO.categoryId());

        Transaction transaction = transactionMapper.toEntity(transactionDTO);
        transaction.setWallet(wallet);
        transaction.setCategory(category);

        walletService.applyTransaction(wallet.getId(), transaction.getAmount(), category.getType());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Created transaction '{}' (id: {}) for wallet: {}, amount: {}, type: {}",
                savedTransaction.getTitle(), savedTransaction.getId(), wallet.getId(),
                savedTransaction.getAmount(), category.getType());

        return transactionMapper.toDTO(savedTransaction);
    }

    @Transactional
    public TransactionDTO updateTransaction(Long id, TransactionDTO transactionDTO) {
        Transaction existingTransaction = entityFetchingService.getTransactionOrThrow(id);

        BigDecimal oldAmount = existingTransaction.getAmount();
        Type oldType = existingTransaction.getCategory().getType();
        Wallet oldWallet = existingTransaction.getWallet();

        transactionMapper.updateEntityFromDTO(transactionDTO, existingTransaction);

        Transaction updatedTransaction = transactionRepository.save(existingTransaction);

        adjustWalletBalancesOnUpdate(oldWallet, oldAmount, oldType,
                updatedTransaction.getWallet(),
                updatedTransaction.getAmount(),
                updatedTransaction.getCategory().getType());

        log.info("Updated transaction id: {} to title: '{}', amount: {}",
                id, updatedTransaction.getTitle(), updatedTransaction.getAmount());

        return transactionMapper.toDTO(updatedTransaction);
    }

    private void adjustWalletBalancesOnUpdate(Wallet oldWallet, BigDecimal oldAmount, Type oldType,
                                              Wallet newWallet, BigDecimal newAmount, Type newType) {
        walletService.reverseTransaction(oldWallet.getId(), oldAmount, oldType);
        walletService.applyTransaction(newWallet.getId(), newAmount, newType);
    }

    @Transactional
    public void deleteTransaction(Long id) {
        Transaction transaction = entityFetchingService.getTransactionOrThrow(id);
        Wallet wallet = transaction.getWallet();

        walletService.reverseTransaction(wallet.getId(),
                transaction.getAmount(),
                transaction.getCategory().getType());

        log.info("Deleting transaction '{}' (id: {}) from wallet: {}, rolled back balance",
                transaction.getTitle(), id, wallet.getId());

        transactionRepository.delete(transaction);
    }

    private List<TransactionDTO> mapToDTO(List<Transaction> transactions) {
        return transactions.stream()
                .map(transactionMapper::toDTO)
                .toList();
    }
}
