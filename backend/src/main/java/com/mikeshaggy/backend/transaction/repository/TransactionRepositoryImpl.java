package com.mikeshaggy.backend.transaction.repository;

import com.mikeshaggy.backend.transaction.domain.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
public class TransactionRepositoryImpl implements TransactionRepositoryCustom {

    private static final String DATE_ALIAS = "transactionDate";
    private static final String COUNT_ALIAS = "transactionCount";

    private final EntityManager entityManager;

    @Override
    public List<TransactionDateCountProjection> findTransactionDateCounts(Specification<Transaction> specification) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Transaction> root = query.from(Transaction.class);

        Path<LocalDate> transactionDate = root.get("transactionDate");
        query.multiselect(
                transactionDate.alias(DATE_ALIAS),
                cb.count(root.get("id")).alias(COUNT_ALIAS));

        Predicate predicate = specification.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }

        query.groupBy(transactionDate);
        query.orderBy(cb.desc(transactionDate));

        return entityManager.createQuery(query).getResultList().stream()
                .<TransactionDateCountProjection>map(tuple -> new DateCount(
                        tuple.get(DATE_ALIAS, LocalDate.class),
                        tuple.get(COUNT_ALIAS, Long.class)))
                .toList();
    }

    private record DateCount(LocalDate date, Long transactionCount) implements TransactionDateCountProjection {

        @Override
        public LocalDate getDate() {
            return date;
        }

        @Override
        public Long getTransactionCount() {
            return transactionCount;
        }
    }
}
