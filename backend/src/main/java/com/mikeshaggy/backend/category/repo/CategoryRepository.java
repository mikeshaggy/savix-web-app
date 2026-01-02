package com.mikeshaggy.backend.category.repo;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.Type;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findByUserId(Integer userId);
    List<Category> findByUserIdAndType(Integer userId, Type type);
}
