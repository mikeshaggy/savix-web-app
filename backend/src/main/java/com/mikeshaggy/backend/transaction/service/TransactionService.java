package com.mikeshaggy.backend.transaction.service;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.dto.*;
import com.mikeshaggy.backend.transaction.repository.TransactionDateCountProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.transaction.repository.TransactionSpecifications;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletBalanceService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;


    public TransactionPageResponse getTransactionsForUser(TransactionFilterParams filter) {
        int effectivePage = Math.max(filter.page(), 0);
        int effectiveSize = normalizeSize(filter.size());
        Sort effectiveSort = normalizeSort(filter.sort());

        Specification<Transaction> baseSpec = TransactionSpecifications.buildSpecification(
                filter.userId(),
                filter.walletId(),
                filter.types(),
                filter.categoryIds(),
                filter.importances(),
                filter.startDate(),
                filter.endDate(),
                filter.q());

        List<TransactionDateCountProjection> dateCounts =
                transactionRepository.findTransactionDateCounts(baseSpec);

        GroupedPaginationPlan plan = paginateDateCounts(
                dateCounts,
                effectivePage,
                effectiveSize,
                dateDirection(effectiveSort));

        List<Transaction> transactions = plan.dates().isEmpty()
                ? List.of()
                : transactionRepository.findAll(
                        baseSpec.and(TransactionSpecifications.hasTransactionDates(plan.dates())),
                        effectiveSort);

        List<TransactionDateGroupResponse> groups = groupTransactionsByDate(transactions);

        return new TransactionPageResponse(
                groups,
                plan.activePage(),
                effectiveSize,
                plan.totalElements(),
                plan.totalPages(),
                plan.activePage() + 1 < plan.totalPages(),
                plan.activePage() > 0
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
            return Sort.by(Sort.Direction.DESC, "transactionDate");
        }

        if (field.equals("transactionDate")) {
            return Sort.by(direction, "transactionDate");
        }

        return Sort.by(Sort.Direction.DESC, "transactionDate").and(Sort.by(direction, field));
    }

    private List<TransactionDateGroupResponse> groupTransactionsByDate(List<Transaction> transactions) {
        Map<LocalDate, List<TransactionResponse>> grouped = new LinkedHashMap<>();
        for (Transaction t : transactions) {
            grouped.computeIfAbsent(t.getTransactionDate(), ignored -> new ArrayList<>())
                    .add(TransactionResponse.from(t));
        }
        return grouped.entrySet().stream()
                .map(e -> new TransactionDateGroupResponse(e.getKey(), e.getValue()))
                .toList();
    }

    private GroupedPaginationPlan paginateDateCounts(
            List<TransactionDateCountProjection> dateCounts,
            int requestedPage,
            int pageSize,
            Sort.Direction dateDirection
    ) {
        List<DateBucket> buckets = dateCounts.stream()
                .map(row -> new DateBucket(row.getDate(), row.getTransactionCount()))
                .sorted((left, right) -> dateDirection == Sort.Direction.ASC
                        ? left.date().compareTo(right.date())
                        : right.date().compareTo(left.date()))
                .toList();

        long totalRows = buckets.stream().mapToLong(DateBucket::transactionCount).sum();
        if (totalRows == 0) {
            return new GroupedPaginationPlan(List.of(), 0L, 0, 0);
        }

        List<List<DateBucket>> pages = new ArrayList<>();
        List<DateBucket> pageBucket = new ArrayList<>();
        long pageRows = 0;

        for (DateBucket bucket : buckets) {
            long groupRows = bucket.transactionCount();
            if (!pageBucket.isEmpty() && pageRows + groupRows > pageSize) {
                pages.add(pageBucket);
                pageBucket = new ArrayList<>();
                pageRows = 0;
            }
            pageBucket.add(bucket);
            pageRows += groupRows;
        }
        if (!pageBucket.isEmpty()) {
            pages.add(pageBucket);
        }

        int totalPages = pages.size();
        int activePage = Math.min(requestedPage, totalPages - 1);
        List<LocalDate> dates = pages.get(activePage).stream()
                .map(DateBucket::date)
                .toList();
        return new GroupedPaginationPlan(dates, totalRows, totalPages, activePage);
    }

    private Sort.Direction dateDirection(Sort sort) {
        Sort.Order order = sort.getOrderFor("transactionDate");
        return order == null ? Sort.Direction.DESC : order.getDirection();
    }

    private record DateBucket(LocalDate date, long transactionCount) {
    }

    private record GroupedPaginationPlan(
            List<LocalDate> dates,
            long totalElements,
            int totalPages,
            int activePage
    ) {}

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
        return TransactionResponse.from(createTransactionEntity(request, userId));
    }

    @Transactional
    Transaction createTransactionEntity(TransactionCreateRequest request, UUID userId) {
        Wallet wallet = walletService.getWalletEntityByIdForUser(request.walletId(), userId);

        Category category = categoryService.getCategoryEntityByIdForUser(request.categoryId(), userId);

        validateImportance(request.importance(), category.getType());

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .category(category)
                .title(request.title())
                .amount(request.amount())
                .transactionDate(request.transactionDate())
                .notes(request.notes())
                .importance(request.importance())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        walletBalanceService.applyTransaction(wallet.getId(), savedTransaction.getAmount(), category.getType(),
                userId, savedTransaction.getId(), savedTransaction.getTransactionDate());

        log.info("Transaction created: transactionId={}, userId={}, walletId={}, categoryId={}, amount={}",
            savedTransaction.getId(), userId, wallet.getId(), category.getId(), savedTransaction.getAmount());

        return savedTransaction;
    }

    @Transactional
    public TransactionResponse updateTransaction(Long id, TransactionUpdateRequest request, UUID userId) {
        Transaction existingTransaction = getTransactionOrThrowForUser(id, userId);

        BigDecimal oldAmount = existingTransaction.getAmount();
        CategoryType oldType = existingTransaction.getCategory().getType();
        Wallet oldWallet = existingTransaction.getWallet();
        LocalDate oldTransactionDate = existingTransaction.getTransactionDate();

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
                updatedTransaction.getId(), oldTransactionDate, updatedTransaction.getTransactionDate()
        );

        log.info("Transaction updated: transactionId={}, userId={}, walletId={}, categoryId={}, amount={}",
            id, userId, updatedTransaction.getWallet().getId(), updatedTransaction.getCategory().getId(), updatedTransaction.getAmount());

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

        log.info("Transaction deleted: transactionId={}, userId={}, walletId={}, amount={}",
            id, userId, wallet.getId(), transaction.getAmount());

        transactionRepository.delete(transaction);
    }

    private void validateImportance(Importance importance, CategoryType categoryType) {
        if (categoryType == CategoryType.INCOME && importance != null) {
            log.warn("Transaction validation failed: reason=importance_not_allowed_for_income");
            throw new IllegalArgumentException("Importance must be null for INCOME transactions");
        }
        if (categoryType == CategoryType.EXPENSE && importance == null) {
            log.warn("Transaction validation failed: reason=importance_required_for_expense");
            throw new IllegalArgumentException("Importance is required for EXPENSE transactions");
        }
    }

    private Transaction getTransactionOrThrowForUser(Long id, UUID userId) {
        return transactionRepository.findByIdAndWalletUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + id));
    }

    public List<Transaction> getTransactionsForWalletAndPeriod(Integer walletId, UUID userId, PeriodDto period) {
        return transactionRepository
                .findByWalletIdAndWalletUserIdAndTransactionDateBetween(
                        walletId, userId, period.startDate(), period.endDate());
    }

    public BigDecimal sumIncomeByWalletIdAndDateRange(Integer walletId, UUID userId, LocalDate from, LocalDate to) {
        return transactionRepository.sumByWalletUserDateRangeAndType(
                walletId, userId, from, to, CategoryType.INCOME);
    }
}
