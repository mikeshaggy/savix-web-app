package com.mikeshaggy.backend.category.repo;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findByUserId(UUID userId);
    
    List<Category> findByUserIdAndType(UUID userId, CategoryType type);
    
    Optional<Category> findByIdAndUserId(Integer id, UUID userId);

    boolean existsByUserIdAndEmoji(UUID userId, String emoji);

    boolean existsByUserIdAndEmojiAndIdNot(UUID userId, String emoji, Integer id);

    Optional<Category> findByUserIdAndIsCycleAnchorTrue(UUID userId);
}
