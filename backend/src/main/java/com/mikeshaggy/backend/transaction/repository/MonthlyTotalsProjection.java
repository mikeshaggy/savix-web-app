package com.mikeshaggy.backend.transaction.repository;

import com.mikeshaggy.backend.category.domain.CategoryType;

import java.math.BigDecimal;

public interface MonthlyTotalsProjection {
    Integer getYear();

    Integer getMonth();

    CategoryType getType();

    BigDecimal getAmount();
}
