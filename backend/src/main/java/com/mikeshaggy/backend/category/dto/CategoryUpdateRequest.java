package com.mikeshaggy.backend.category.dto;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.Type;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "Name must not exceed 50 characters")
        String name,

        @NotNull(message = "Type is required")
        Type type,

        @Size(max = 16, message = "Emoji must not exceed 16 characters")
        String emoji,

        Boolean isCycleAnchor
) {
        public void applyTo(Category category) {
                category.setName(name);
                category.setType(type);
                category.setEmoji((emoji == null || emoji.isBlank()) ? null : emoji.trim());
                if (isCycleAnchor != null) {
                        category.setCycleAnchor(isCycleAnchor);
                }
        }
}
