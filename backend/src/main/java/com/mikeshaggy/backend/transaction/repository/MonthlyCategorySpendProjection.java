package com.mikeshaggy.backend.transaction.repository;

import java.math.BigDecimal;

public interface MonthlyCategorySpendProjection {
    Integer getYear();

    Integer getMonth();

    Integer getCategoryId();

    String getName();

    String getEmoji();

    BigDecimal getAmount();
}
