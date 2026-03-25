package com.mikeshaggy.backend.category.service;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.dto.CategoryCreateRequest;
import com.mikeshaggy.backend.category.dto.CategoryResponse;
import com.mikeshaggy.backend.category.dto.CategoryUpdateRequest;
import com.mikeshaggy.backend.category.repo.CategoryRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserService userService;

    public List<CategoryResponse> getCategoriesForUser(UUID userId, CategoryType type) {
        if (type != null) {
            return categoryRepository.findByUserIdAndType(userId, type).stream()
                    .map(CategoryResponse::from)
                    .toList();
        }

        return categoryRepository.findByUserId(userId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public CategoryResponse getCategoryByIdForUser(Integer id, UUID userId) {
        Category category = getCategoryOrThrowForUser(id, userId);
        return CategoryResponse.from(category);
    }

    public Category getCategoryEntityByIdForUser(Integer id, UUID userId) {
        return getCategoryOrThrowForUser(id, userId);
    }

    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request, UUID userId) {
        validateEmojiUniqueness(request.emoji(), userId, null);

        User user = userService.getUserOrThrow(userId);

        Category category = Category.builder()
                .name(request.name())
                .type(request.type())
                .emoji((request.emoji() == null || request.emoji().isBlank()) ? null : request.emoji().trim())
                .user(user)
                .build();

        Category savedCategory = categoryRepository.save(category);
        
        log.info("Created category '{}' (type: {}) with id: {} for user: {}", 
                savedCategory.getName(), savedCategory.getType(), savedCategory.getId(), userId);
        
        return CategoryResponse.from(savedCategory);
    }

    @Transactional
    public CategoryResponse updateCategory(Integer categoryId, CategoryUpdateRequest request, UUID userId) {
        validateEmojiUniqueness(request.emoji(), userId, categoryId);

        Category category = getCategoryOrThrowForUser(categoryId, userId);

        if (Boolean.TRUE.equals(request.isCycleAnchor())) {
            categoryRepository.findByUserIdAndIsCycleAnchorTrue(userId)
                    .filter(existing -> !existing.getId().equals(categoryId))
                    .ifPresent(existing -> {
                        existing.setCycleAnchor(false);
                        categoryRepository.save(existing);
                        log.info("Cleared cycle anchor from category '{}' (id: {}) for user: {}",
                                existing.getName(), existing.getId(), userId);
                    });
        }

        request.applyTo(category);
        
        Category updatedCategory = categoryRepository.save(category);
        
        log.info("Updated category id: {} to name: '{}', type: {}", 
                categoryId, request.name(), request.type());

        return CategoryResponse.from(updatedCategory);
    }

    @Transactional
    public void deleteCategory(Integer id, UUID userId) {
        Category category = getCategoryOrThrowForUser(id, userId);
        
        log.info("Deleting category '{}' (id: {}) for user: {}", 
                category.getName(), id, userId);
        
        categoryRepository.delete(category);
    }

    private Category getCategoryOrThrowForUser(Integer id, UUID userId) {
        return categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + id));
    }

    private void validateEmojiUniqueness(String emoji, UUID userId, Integer excludeId) {
        if (emoji == null || emoji.isBlank()) {
            return;
        }

        String trimmedEmoji = emoji.trim();

        boolean exists = excludeId == null
                ? categoryRepository.existsByUserIdAndEmoji(userId, trimmedEmoji)
                : categoryRepository.existsByUserIdAndEmojiAndIdNot(userId, trimmedEmoji, excludeId);

        if (exists) {
            throw new IllegalArgumentException("Emoji '" + trimmedEmoji + "' is already used in another category for this user.");
        }
    }
}
