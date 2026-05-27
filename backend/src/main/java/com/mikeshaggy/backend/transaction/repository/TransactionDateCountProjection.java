package com.mikeshaggy.backend.transaction.repository;

import java.time.LocalDate;

public interface TransactionDateCountProjection {

    LocalDate getDate();

    Long getTransactionCount();
}
