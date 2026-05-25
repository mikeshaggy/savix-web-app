package com.mikeshaggy.backend.transaction.repository;

import java.math.BigDecimal;

public interface CategoryBreakdownProjection {

    Integer getCategoryId();

    String getName();

    String getEmoji();

    BigDecimal getAmount();

    Long getTransactionCount();
}
