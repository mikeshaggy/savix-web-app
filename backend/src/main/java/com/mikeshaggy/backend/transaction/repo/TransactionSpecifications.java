package com.mikeshaggy.backend.transaction.repo;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> belongsToUser(UUID userId) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.equal(root.get("wallet").get("user").get("id"), userId);
        };
    }

    public static Specification<Transaction> belongsToWallet(Integer walletId) {
        if (walletId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("wallet").get("id"), walletId);
    }

    public static Specification<Transaction> hasTypes(List<CategoryType> types) {
        if (types == null || types.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("category").get("type").in(types);
    }

    public static Specification<Transaction> hasCategoryIds(List<Integer> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("category").get("id").in(categoryIds);
    }

    public static Specification<Transaction> hasImportances(List<Importance> importances) {
        if (importances == null || importances.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("importance").in(importances);
    }

    public static Specification<Transaction> transactionDateBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return null;
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Transaction> searchQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            String pattern = "%" + q.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("notes")), pattern)
            );
        };
    }

    public static Specification<Transaction> buildSpecification(
            UUID userId,
            Integer walletId,
            List<CategoryType> types,
            List<Integer> categoryIds,
            List<Importance> importances,
            LocalDate startDate,
            LocalDate endDate,
            String q
    ) {
        return Stream.of(
                        belongsToUser(userId),
                        belongsToWallet(walletId),
                        hasTypes(types),
                        hasCategoryIds(categoryIds),
                        hasImportances(importances),
                        transactionDateBetween(startDate, endDate),
                        searchQuery(q)
                )
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElseThrow();
    }
}