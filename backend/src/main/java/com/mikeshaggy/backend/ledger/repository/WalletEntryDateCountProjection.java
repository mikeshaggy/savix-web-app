package com.mikeshaggy.backend.ledger.repository;

import java.time.LocalDate;

public interface WalletEntryDateCountProjection {
    LocalDate getEntryDate();
    Long getEntryCount();
}
