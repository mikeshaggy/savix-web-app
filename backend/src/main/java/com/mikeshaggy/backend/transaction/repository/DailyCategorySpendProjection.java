package com.mikeshaggy.backend.transaction.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyCategorySpendProjection {

    LocalDate getDate();

    Integer getCategoryId();

    String getName();

    String getEmoji();

    BigDecimal getAmount();

    Long getTransactionCount();
}
