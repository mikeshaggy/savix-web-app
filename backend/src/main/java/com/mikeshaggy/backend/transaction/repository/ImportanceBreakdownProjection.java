package com.mikeshaggy.backend.transaction.repository;

import com.mikeshaggy.backend.transaction.domain.Importance;

import java.math.BigDecimal;

public interface ImportanceBreakdownProjection {

    Importance getImportance();

    BigDecimal getAmount();

    Long getCount();
}
