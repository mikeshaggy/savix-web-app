package com.mikeshaggy.backend.dashboard.service.period;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;

import java.time.LocalDate;

public interface PeriodResolver {

    PeriodType supports();

    PeriodDto resolve(Integer walletId, LocalDate customStart, LocalDate customEnd);
}
