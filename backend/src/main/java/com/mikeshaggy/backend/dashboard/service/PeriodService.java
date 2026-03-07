package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.repo.CategoryRepository;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.transaction.repo.TransactionRepository;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeriodService {

    private final CategoryService categoryService;
    private final TransactionService transactionService;

    public PeriodDto getCurrentPeriod(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        PeriodType periodType;

        Optional<Category> anchorOpt = categoryService.findCycleAnchorCategoryForUser(userId);

        if (anchorOpt.isPresent()) {
            Category anchor = anchorOpt.get();
            Optional<LocalDate> maxDateOpt = transactionService.findMaxTransactionDateForCategory(anchor.getId());

            startDate = maxDateOpt.orElseGet(() -> today.withDayOfMonth(1));
        } else {
            startDate = today.withDayOfMonth(1);
        }
        periodType = PeriodType.PAY_CYCLE;

        LocalDate endDate = today;
        LocalDate billingEndDate = startDate.plusMonths(1);

        return new PeriodDto(startDate, endDate, billingEndDate, periodType);
    }
}
