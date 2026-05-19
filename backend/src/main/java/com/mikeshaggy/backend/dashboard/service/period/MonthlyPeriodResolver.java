package com.mikeshaggy.backend.dashboard.service.period;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MonthlyPeriodResolver implements PeriodResolver {

    private final Clock clock;

    @Override
    public PeriodType supports() {
        return PeriodType.MONTHLY;
    }

    @Override
    public PeriodDto resolve(Integer walletId, UUID userId, LocalDate customStart, LocalDate customEnd, Integer anchorCategoryId) {
        YearMonth month = resolveMonth(customStart, customEnd);
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

        return new PeriodDto(startDate, endDate, month.plusMonths(1).atDay(1), PeriodType.MONTHLY);
    }

    private YearMonth resolveMonth(LocalDate customStart, LocalDate customEnd) {
        if (customStart != null) {
            return YearMonth.from(customStart);
        }

        if (customEnd != null) {
            return YearMonth.from(customEnd);
        }

        return YearMonth.from(LocalDate.now(clock));
    }
}
