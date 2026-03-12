package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.repo.CategoryRepository;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.dto.ResolvedPeriods;
import com.mikeshaggy.backend.dashboard.service.period.ComparePeriodResolver;
import com.mikeshaggy.backend.dashboard.service.period.PeriodResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PeriodService {

    private final Map<PeriodType, PeriodResolver> periodResolvers;
    private final ComparePeriodResolver comparePeriodResolver;
    private final CategoryRepository categoryRepository;

    public PeriodService(List<PeriodResolver> resolvers, ComparePeriodResolver comparePeriodResolver,
                         CategoryRepository categoryRepository) {
        this.periodResolvers = resolvers.stream()
                .collect(Collectors.toMap(PeriodResolver::supports, Function.identity()));
        this.comparePeriodResolver = comparePeriodResolver;
        this.categoryRepository = categoryRepository;
    }

    public PeriodDto resolve(PeriodType periodType, Integer walletId, UUID userId,
                              LocalDate startDate, LocalDate endDate) {
        Integer anchorCategoryId = lookupAnchorCategoryId(userId);
        PeriodResolver resolver = periodResolvers.get(periodType);
        if (resolver == null) {
            throw new IllegalArgumentException("Unsupported period type: " + periodType);
        }
        return resolver.resolve(walletId, userId, startDate, endDate, anchorCategoryId);
    }

    public ResolvedPeriods resolvePeriods(PeriodType periodType, Integer walletId, UUID userId,
                                          LocalDate startDate, LocalDate endDate) {
        Integer anchorCategoryId = lookupAnchorCategoryId(userId);
        PeriodResolver resolver = periodResolvers.get(periodType);
        if (resolver == null) {
            throw new IllegalArgumentException("Unsupported period type: " + periodType);
        }
        PeriodDto primary = resolver.resolve(walletId, userId, startDate, endDate, anchorCategoryId);
        PeriodDto compare = comparePeriodResolver.resolve(primary, walletId, anchorCategoryId);
        return new ResolvedPeriods(primary, compare);
    }

    public PeriodDto getCurrentPeriod(UUID userId, Integer walletId) {
        return resolve(PeriodType.PAY_CYCLE, walletId, userId, null, null);
    }

    private Integer lookupAnchorCategoryId(UUID userId) {
        return categoryRepository.findByUserIdAndIsCycleAnchorTrue(userId)
                .map(Category::getId)
                .orElse(null);
    }
}
