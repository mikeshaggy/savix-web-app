package com.mikeshaggy.backend.dashboard.service.period;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class CustomPeriodResolver implements PeriodResolver {

    @Override
    public PeriodType supports() {
        return PeriodType.CUSTOM;
    }

    @Override
    public PeriodDto resolve(Integer walletId, UUID userId, LocalDate customStart, LocalDate customEnd, Integer anchorCategoryId) {
        if (customStart == null || customEnd == null) {
            throw new IllegalArgumentException("Both startDate and endDate are required for CUSTOM period type");
        }

        if (customStart.isAfter(customEnd)) {
            throw new IllegalArgumentException(
                    "startDate (%s) must not be after endDate (%s)".formatted(customStart, customEnd));
        }

        return new PeriodDto(customStart, customEnd, customStart.plusMonths(1), PeriodType.CUSTOM);
    }
}
