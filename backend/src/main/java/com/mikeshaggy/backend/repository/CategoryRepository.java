package com.mikeshaggy.backend.repository;

import com.mikeshaggy.backend.domain.transaction.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findByUserId(Integer userId);
}
