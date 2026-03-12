package com.mikeshaggy.backend.category.api;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.dto.CategoryCreateRequest;
import com.mikeshaggy.backend.category.dto.CategoryResponse;
import com.mikeshaggy.backend.category.dto.CategoryUpdateRequest;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(CategoryController.BASE_URL)
@RequiredArgsConstructor
public class CategoryController {

    public static final String BASE_URL = "/api/categories";

    private final CategoryService categoryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories(
            @RequestParam(required = false) CategoryType type) {
        List<CategoryResponse> categories = categoryService.getCategoriesForUser(
                currentUserProvider.getCurrentUserId(),
                type
        );
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Integer id) {
        CategoryResponse category = categoryService.getCategoryByIdForUser(
                id, 
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(category);
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse createdCategory = categoryService.createCategory(
                request, 
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCategory);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Integer id, 
            @Valid @RequestBody CategoryUpdateRequest request) {
        CategoryResponse updatedCategory = categoryService.updateCategory(
                id, 
                request, 
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(updatedCategory);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Integer id) {
        categoryService.deleteCategory(id, currentUserProvider.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
