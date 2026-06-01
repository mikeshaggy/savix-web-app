package com.mikeshaggy.backend.budget.service;

import com.mikeshaggy.backend.budget.domain.CategoryBudget;
import com.mikeshaggy.backend.budget.dto.BudgetUsageItemDto;
import com.mikeshaggy.backend.budget.dto.BudgetUsageResponse;
import com.mikeshaggy.backend.budget.dto.CategoryBudgetCreateRequest;
import com.mikeshaggy.backend.budget.dto.CategoryBudgetResponse;
import com.mikeshaggy.backend.budget.dto.CategoryBudgetUpdateRequest;
import com.mikeshaggy.backend.budget.repository.CategoryBudgetRepository;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.common.calculation.budget.BudgetUsageCalculator;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import com.mikeshaggy.backend.common.exception.ConflictException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryBudgetService {

    private final CategoryBudgetRepository categoryBudgetRepository;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;
    private final PeriodService periodService;
    private final Clock clock;

    public List<CategoryBudgetResponse> getAll(Integer walletId, UUID userId, boolean active) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        List<CategoryBudget> budgets = active
                ? categoryBudgetRepository.findActiveByWalletIdAndUserId(walletId, userId)
                : categoryBudgetRepository.findArchivedByWalletIdAndUserId(walletId, userId);

        return budgets.stream().map(CategoryBudgetResponse::from).toList();
    }

    public BudgetUsageResponse getUsage(Integer walletId, UUID userId,
                                        PeriodType periodType, LocalDate startDate, LocalDate endDate) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        PeriodType resolved = periodType != null ? periodType : PeriodType.PAY_CYCLE;
        PeriodDto period = periodService.resolve(resolved, walletId, userId, startDate, endDate);
        LocalDate periodStart = period.startDate();
        LocalDate periodEnd   = effectivePeriodEnd(period);

        List<CategoryBudget> budgets =
                categoryBudgetRepository.findActiveByWalletIdAndUserId(walletId, userId);

        Map<Integer, BigDecimal> spendByCategory = transactionRepository
                .findCategorySpendByWalletUserAndDateRange(
                        walletId, userId, periodStart, periodEnd, CategoryType.EXPENSE)
                .stream()
                .collect(Collectors.toMap(p -> p.getCategoryId(), p -> p.getAmount()));

        List<BudgetUsageCalculator.BudgetInput> inputs = budgets.stream()
                .map(b -> new BudgetUsageCalculator.BudgetInput(
                        b.getId(),
                        b.getCategory().getId(),
                        b.getCategory().getName(),
                        b.getCategory().getEmoji(),
                        b.getAmount(),
                        b.getWarningThresholdPercent()))
                .toList();

        List<BudgetUsageCalculator.BudgetItemResult> results =
                BudgetUsageCalculator.calculateItems(
                        inputs, spendByCategory, periodStart, periodEnd, LocalDate.now(clock));

        List<BudgetUsageItemDto> items = results.stream()
                .map(r -> new BudgetUsageItemDto(
                        r.budgetId(),
                        r.categoryId(),
                        r.categoryName(),
                        r.categoryEmoji(),
                        r.budgetAmount(),
                        r.spentAmount(),
                        r.remainingAmount(),
                        r.usagePercent(),
                        r.warningThresholdPercent(),
                        r.status(),
                        r.daysRemaining(),
                        r.projectedSpend()))
                .toList();

        return new BudgetUsageResponse(walletId, periodStart, periodEnd, items);
    }

    @Transactional
    public CategoryBudgetResponse create(CategoryBudgetCreateRequest request, UUID userId) {
        Wallet   wallet   = walletService.getWalletEntityByIdForUser(request.walletId(), userId);
        Category category = categoryService.getCategoryEntityByIdForUser(request.categoryId(), userId);

        if (category.getType() != CategoryType.EXPENSE) {
            throw new IllegalArgumentException("Budgets can only be set for expense categories.");
        }

        if (categoryBudgetRepository.existsActiveByWalletIdAndCategoryId(
                request.walletId(), request.categoryId())) {
            throw new IllegalArgumentException(
                    "An active budget for category '" + category.getName() + "' already exists in this wallet.");
        }

        CategoryBudget budget = CategoryBudget.builder()
                .wallet(wallet)
                .category(category)
                .amount(request.amount())
                .warningThresholdPercent(
                        request.warningThresholdPercent() != null ? request.warningThresholdPercent() : 80)
                .active(true)
                .build();

        CategoryBudget saved = categoryBudgetRepository.save(budget);

        log.info("CategoryBudget created: budgetId={}, walletId={}, categoryId={}, userId={}",
                saved.getId(), request.walletId(), request.categoryId(), userId);

        return CategoryBudgetResponse.from(saved);
    }

    @Transactional
    public CategoryBudgetResponse update(Integer budgetId, CategoryBudgetUpdateRequest request, UUID userId) {
        CategoryBudget budget = getBudgetOrThrowForUser(budgetId, userId);

        budget.setAmount(request.amount());
        if (request.warningThresholdPercent() != null) {
            budget.setWarningThresholdPercent(request.warningThresholdPercent());
        }

        CategoryBudget saved = categoryBudgetRepository.save(budget);

        log.info("CategoryBudget updated: budgetId={}, userId={}", budgetId, userId);

        return CategoryBudgetResponse.from(saved);
    }

    @Transactional
    public void deactivate(Integer budgetId, UUID userId) {
        CategoryBudget budget = getBudgetOrThrowForUser(budgetId, userId);
        budget.setActive(false);
        categoryBudgetRepository.save(budget);
        log.info("CategoryBudget deactivated: budgetId={}, userId={}", budgetId, userId);
    }

    @Transactional
    public CategoryBudgetResponse reactivate(Integer budgetId, UUID userId) {
        CategoryBudget budget = getBudgetOrThrowForUser(budgetId, userId);

        boolean conflict = categoryBudgetRepository.existsActiveByWalletIdAndCategoryIdExcluding(
                budget.getWallet().getId(), budget.getCategory().getId(), budgetId);
        if (conflict) {
            throw new ConflictException(
                    "An active budget for category '" + budget.getCategory().getName() + "' already exists in this wallet.");
        }

        budget.setActive(true);
        CategoryBudget saved = categoryBudgetRepository.save(budget);
        log.info("CategoryBudget reactivated: budgetId={}, userId={}", budgetId, userId);
        return CategoryBudgetResponse.from(saved);
    }

    @Transactional
    public void delete(Integer budgetId, UUID userId) {
        CategoryBudget budget = getBudgetOrThrowForUser(budgetId, userId);
        categoryBudgetRepository.delete(budget);
        log.info("CategoryBudget permanently deleted: budgetId={}, userId={}", budgetId, userId);
    }

    private CategoryBudget getBudgetOrThrowForUser(Integer budgetId, UUID userId) {
        return categoryBudgetRepository.findByIdAndWalletUserId(budgetId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Budget not found with id: " + budgetId));
    }

    private LocalDate effectivePeriodEnd(PeriodDto period) {
        return period.periodType() == PeriodType.PAY_CYCLE
                ? period.billingEndDate().minusDays(1)
                : period.endDate();
    }
}
