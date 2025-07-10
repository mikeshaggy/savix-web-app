package com.mikeshaggy.backend.mapper;

import com.mikeshaggy.backend.domain.transaction.Category;
import com.mikeshaggy.backend.domain.user.User;
import com.mikeshaggy.backend.dto.CategoryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class CategoryMapperTest {

    private CategoryMapper categoryMapper;

    private static final int CATEGORY_ID = 1;
    private static final String CATEGORY_NAME = "Groceries";
    private static final int USER_ID = 2;
    private static final String USER_USERNAME = "johndoe";
    private static final String USER_PASSWORD = "test_password";

    private LocalDateTime createdAt;
    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        categoryMapper = CategoryMapper.INSTANCE;

        createdAt = LocalDateTime.of(2024, 1, 15, 10, 30);

        testUser = User.builder()
                .id(USER_ID)
                .username(USER_USERNAME)
                .password(USER_PASSWORD)
                .categories(new HashSet<>())
                .transactions(new HashSet<>())
                .build();

        testCategory = Category.builder()
                .id(CATEGORY_ID)
                .name(CATEGORY_NAME)
                .createdAt(createdAt)
                .user(testUser)
                .build();
    }

    @Test
    @DisplayName("Converting Category to CategoryDTO")
    void entityToDto() {
        // when
        CategoryDTO categoryDTO = categoryMapper.toDTO(testCategory);

        // then
        assertAll(
                () -> assertThat(categoryDTO.getId()).isEqualTo(CATEGORY_ID),
                () -> assertThat(categoryDTO.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(categoryDTO.getName()).isEqualTo(CATEGORY_NAME),
                () -> assertThat(categoryDTO.getCreatedAt()).isEqualTo(createdAt)
        );
    }

    @Test
    @DisplayName("Converting CategoryDTO to Category")
    void dtoToEntity() {
        // given
        CategoryDTO categoryDTO = CategoryDTO.builder()
                .id(CATEGORY_ID)
                .userId(USER_ID)
                .name(CATEGORY_NAME)
                .createdAt(createdAt)
                .build();

        // when
        Category category = categoryMapper.toEntity(categoryDTO);

        // then
        assertAll(
                () -> assertThat(category.getId()).isEqualTo(CATEGORY_ID),
                () -> assertThat(category.getUser().getId()).isEqualTo(USER_ID),
                () -> assertThat(category.getName()).isEqualTo(CATEGORY_NAME),
                () -> assertThat(category.getCreatedAt()).isEqualTo(createdAt),
                () -> assertThat(category.getUser().getUsername()).isNull(),
                () -> assertThat(category.getUser().getPassword()).isNull(),
                () -> assertThat(category.getTransactions()).isEmpty()
        );
    }

    @Test
    @DisplayName("Converting null Category should return null")
    void entityToDto_WithNullCategory_ShouldReturnNull() {
        // when
        CategoryDTO result = categoryMapper.toDTO(null);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Converting null CategoryDTO should return null")
    void dtoToEntity_WithNullCategoryDTO_ShouldReturnNull() {
        // when
        Category result = categoryMapper.toEntity(null);

        // then
        assertThat(result).isNull();
    }
}