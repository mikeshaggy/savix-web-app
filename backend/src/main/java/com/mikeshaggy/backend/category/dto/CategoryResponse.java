package com.mikeshaggy.backend.category.dto;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.Type;

import java.time.Instant;

public record CategoryResponse(
        Integer id,
        String name,
        Type type,
        String emoji,
        boolean isCycleAnchor,
        Instant createdAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getEmoji(),
                category.isCycleAnchor(),
                category.getCreatedAt()
        );
    }
}
