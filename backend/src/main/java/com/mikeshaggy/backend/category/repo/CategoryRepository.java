package com.mikeshaggy.backend.category.repo;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.Type;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findByUserId(UUID userId);
    
    List<Category> findByUserIdAndType(UUID userId, Type type);
    
    Optional<Category> findByIdAndUserId(Integer id, UUID userId);
}
