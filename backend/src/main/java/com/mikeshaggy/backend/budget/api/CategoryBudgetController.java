package com.mikeshaggy.backend.budget.api;

import com.mikeshaggy.backend.budget.dto.*;
import com.mikeshaggy.backend.budget.service.CategoryBudgetService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(CategoryBudgetController.BASE_URL)
@RequiredArgsConstructor
public class CategoryBudgetController {

    public static final String BASE_URL = "/api/category-budgets";

    private final CategoryBudgetService categoryBudgetService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<CategoryBudgetResponse>> getAll(
            @RequestParam Integer walletId,
            @RequestParam(defaultValue = "true") boolean active) {
        List<CategoryBudgetResponse> budgets = categoryBudgetService.getAll(
                walletId, currentUserProvider.getCurrentUserId(), active);
        return ResponseEntity.ok(budgets);
    }

    @PostMapping
    public ResponseEntity<CategoryBudgetResponse> create(
            @Valid @RequestBody CategoryBudgetCreateRequest request) {
        CategoryBudgetResponse created = categoryBudgetService.create(
                request, currentUserProvider.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryBudgetResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody CategoryBudgetUpdateRequest request) {
        CategoryBudgetResponse updated = categoryBudgetService.update(
                id, request, currentUserProvider.getCurrentUserId());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Integer id) {
        categoryBudgetService.deactivate(id, currentUserProvider.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<CategoryBudgetResponse> reactivate(@PathVariable Integer id) {
        CategoryBudgetResponse response = categoryBudgetService.reactivate(
                id, currentUserProvider.getCurrentUserId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        categoryBudgetService.delete(id, currentUserProvider.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/usage")
    public ResponseEntity<BudgetUsageResponse> getUsage(
            @RequestParam Integer walletId,
            @RequestParam(required = false) PeriodType periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        BudgetUsageResponse usage = categoryBudgetService.getUsage(
                walletId, currentUserProvider.getCurrentUserId(), periodType, startDate, endDate);
        return ResponseEntity.ok(usage);
    }
}
