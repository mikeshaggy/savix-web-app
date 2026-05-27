package com.mikeshaggy.backend.common.period;

import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;

import java.time.LocalDate;
import java.util.UUID;

public interface PeriodResolver {

    PeriodType supports();

    PeriodDto resolve(Integer walletId, UUID userId, LocalDate customStart,
                      LocalDate customEnd, Integer anchorCategoryId);
}
