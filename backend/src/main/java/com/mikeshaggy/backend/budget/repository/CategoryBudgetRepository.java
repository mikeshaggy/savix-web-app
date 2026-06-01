package com.mikeshaggy.backend.budget.repository;

import com.mikeshaggy.backend.budget.domain.CategoryBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryBudgetRepository extends JpaRepository<CategoryBudget, Integer> {

    @Query("""
        SELECT cb FROM CategoryBudget cb
        JOIN FETCH cb.category
        WHERE cb.wallet.id = :walletId
        AND cb.wallet.user.id = :userId
        AND cb.active = true
    """)
    List<CategoryBudget> findActiveByWalletIdAndUserId(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId);

    @Query("""
        SELECT cb FROM CategoryBudget cb
        JOIN FETCH cb.category
        WHERE cb.wallet.id = :walletId
        AND cb.wallet.user.id = :userId
        AND cb.active = false
    """)
    List<CategoryBudget> findArchivedByWalletIdAndUserId(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId);

    @Query("""
        SELECT cb FROM CategoryBudget cb
        JOIN FETCH cb.category
        WHERE cb.wallet.id = :walletId
        AND cb.wallet.user.id = :userId
    """)
    List<CategoryBudget> findAllByWalletIdAndUserId(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId);

    @Query("""
        SELECT cb FROM CategoryBudget cb
        JOIN FETCH cb.category
        WHERE cb.id = :id
        AND cb.wallet.user.id = :userId
    """)
    Optional<CategoryBudget> findByIdAndWalletUserId(
            @Param("id") Integer id,
            @Param("userId") UUID userId);

    @Query("""
        SELECT CASE WHEN COUNT(cb) > 0 THEN true ELSE false END
        FROM CategoryBudget cb
        WHERE cb.wallet.id = :walletId
        AND cb.category.id = :categoryId
        AND cb.active = true
    """)
    boolean existsActiveByWalletIdAndCategoryId(
            @Param("walletId") Integer walletId,
            @Param("categoryId") Integer categoryId);

    @Query("""
        SELECT CASE WHEN COUNT(cb) > 0 THEN true ELSE false END
        FROM CategoryBudget cb
        WHERE cb.wallet.id = :walletId
        AND cb.category.id = :categoryId
        AND cb.active = true
        AND cb.id <> :excludeId
    """)
    boolean existsActiveByWalletIdAndCategoryIdExcluding(
            @Param("walletId") Integer walletId,
            @Param("categoryId") Integer categoryId,
            @Param("excludeId") Integer excludeId);
}
