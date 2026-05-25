package com.mikeshaggy.backend.transaction.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface HeatmapProjection {

    LocalDate getDate();

    Integer getCategoryId();

    String getCategoryName();

    String getEmoji();

    BigDecimal getAmount();

    Long getTransactions();
}
