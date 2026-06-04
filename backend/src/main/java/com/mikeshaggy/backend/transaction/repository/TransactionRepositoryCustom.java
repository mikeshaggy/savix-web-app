package com.mikeshaggy.backend.transaction.repository;

import com.mikeshaggy.backend.transaction.domain.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface TransactionRepositoryCustom {

    List<TransactionDateCountProjection> findTransactionDateCounts(Specification<Transaction> specification);
}
