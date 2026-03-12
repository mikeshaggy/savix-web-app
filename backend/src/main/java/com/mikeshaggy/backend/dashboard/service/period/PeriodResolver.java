package com.mikeshaggy.backend.dashboard.service.period;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;

import java.time.LocalDate;
import java.util.UUID;

public interface PeriodResolver {

    PeriodType supports();

    PeriodDto resolve(Integer walletId, UUID userId, LocalDate customStart,
                      LocalDate customEnd, Integer anchorCategoryId);
}
