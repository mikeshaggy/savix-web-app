package com.mikeshaggy.backend.transaction.service;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.Type;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.dto.TransactionCreateRequest;
import com.mikeshaggy.backend.transaction.dto.TransactionPageResponse;
import com.mikeshaggy.backend.transaction.dto.TransactionResponse;
import com.mikeshaggy.backend.transaction.dto.TransactionUpdateRequest;
import com.mikeshaggy.backend.transaction.repo.TransactionRepository;
import com.mikeshaggy.backend.transaction.repo.TransactionSpecifications;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletBalanceService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("transactionDate", "amount", "title");
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 20, 50, 100);
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;


    public TransactionPageResponse getTransactionsForUser(
            UUID userId,
            int page,
            int size,
            List<Type> types,
            List<Integer> categoryIds,
            List<Importance> importances,
            LocalDate startDate,
            LocalDate endDate,
            String q,
            String sort) {
        int effectivePage = Math.max(page, 0);
        int effectiveSize = normalizeSize(size);
        Sort effectiveSort = normalizeSort(sort);
        Pageable pageable = PageRequest.of(effectivePage, effectiveSize, effectiveSort);

        return searchTransactions(
                userId,
                types,
                categoryIds,
                importances,
                startDate,
                endDate,
                q,
                pageable
        );
    }

    private int normalizeSize(int size) {
        int s = ALLOWED_PAGE_SIZES.contains(size) ? size : DEFAULT_PAGE_SIZE;
        return Math.min(s, MAX_PAGE_SIZE);
    }

    private Sort normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "transactionDate");
        }

        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction direction = Sort.Direction.DESC;

        if (parts.length > 1) {
            String dir = parts[1].trim().toLowerCase();
            if (dir.equals("asc")) {
                direction = Sort.Direction.ASC;
            }
        }

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            field = "transactionDate";
            direction = Sort.Direction.DESC;
        }

        return Sort.by(direction, field);
    }

    private TransactionPageResponse searchTransactions(
            UUID userId,
            List<Type> types,
            List<Integer> categoryIds,
            List<Importance> importances,
            LocalDate startDate,
            LocalDate endDate,
            String q,
            Pageable pageable
    ) {
        Specification<Transaction> spec = TransactionSpecifications.buildSpecification(
                userId, types, categoryIds, importances, startDate, endDate, q
        );

        Page<Transaction> page = transactionRepository.findAll(spec, pageable);

        List<TransactionResponse> items = page.getContent().stream()
                .map(TransactionResponse::from)
                .toList();

        return new TransactionPageResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
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

        Transaction savedTransaction = transactionRepository.save(transaction);

        walletBalanceService.applyTransaction(wallet.getId(), savedTransaction.getAmount(), category.getType(),
                userId, savedTransaction.getId(), savedTransaction.getTransactionDate());

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

        walletBalanceService.adjustForTransactionEdit(
                oldWallet, oldAmount, oldType,
                newWallet, updatedTransaction.getAmount(), newCategory.getType(),
                updatedTransaction.getId(), updatedTransaction.getTransactionDate()
        );

        log.info("Updated transaction id: {} to title: '{}', amount: {}",
                id, updatedTransaction.getTitle(), updatedTransaction.getAmount());

        return TransactionResponse.from(updatedTransaction);
    }

    @Transactional
    public void deleteTransaction(Long id, UUID userId) {
        Transaction transaction = getTransactionOrThrowForUser(id, userId);
        Wallet wallet = transaction.getWallet();

        walletBalanceService.reverseTransaction(
                wallet.getId(),
                transaction.getAmount(),
                transaction.getCategory().getType(),
                userId,
                transaction.getId(),
                transaction.getTransactionDate()
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
