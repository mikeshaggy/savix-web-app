package com.mikeshaggy.backend.dto;

import com.mikeshaggy.backend.domain.transaction.Type;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDTO {

    private Integer id;

    @NotNull(message = "User ID is required")
    private Integer userId;

    @NotNull(message = "Name is required")
    @Size(max = 50, message = "Name must not exceed 50 characters")
    private String name;

    @NotNull(message = "Type is required")
    private Type type;

    private LocalDateTime createdAt;
}
