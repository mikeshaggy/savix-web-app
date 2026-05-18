package com.mikeshaggy.backend.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.dto.CategoryCreateRequest;
import com.mikeshaggy.backend.category.dto.CategoryResponse;
import com.mikeshaggy.backend.category.dto.CategoryUpdateRequest;
import com.mikeshaggy.backend.category.repo.CategoryRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private CategoryService categoryService;

    private static final UUID USER_ID = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).email("test@test.com").username("test").build();
    }

    private Category category(Integer id, String name, CategoryType type) {
        return Category.builder().id(id).name(name).type(type).user(user).build();
    }

    @Nested
    class CycleAnchorUniqueness {

        @Test
        void settingNewAnchor_clearsExistingAnchor() {
            // given
            Category existing = category(1, "Salary", CategoryType.INCOME);
            existing.setCycleAnchor(true);

            Category target = category(2, "Freelance", CategoryType.INCOME);
            target.setCycleAnchor(false);

            CategoryUpdateRequest request =
                    new CategoryUpdateRequest("Freelance", CategoryType.INCOME, null, true);

            when(categoryRepository.findByIdAndUserId(2, USER_ID)).thenReturn(Optional.of(target));
            when(categoryRepository.findByUserIdAndIsCycleAnchorTrue(USER_ID))
                    .thenReturn(Optional.of(existing));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            categoryService.updateCategory(2, request, USER_ID);

            // then
            assertThat(existing.isCycleAnchor()).isFalse();
            verify(categoryRepository).save(existing);
        }

        @Test
        void settingAnchorOnSameCategory_doesNotClearItself() {
            // given
            Category target = category(1, "Salary", CategoryType.INCOME);
            target.setCycleAnchor(true);

            CategoryUpdateRequest request =
                    new CategoryUpdateRequest("Salary", CategoryType.INCOME, null, true);

            when(categoryRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(target));
            when(categoryRepository.findByUserIdAndIsCycleAnchorTrue(USER_ID))
                    .thenReturn(Optional.of(target));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            categoryService.updateCategory(1, request, USER_ID);

            // then
            assertThat(target.isCycleAnchor()).isTrue();
            verify(categoryRepository, times(1)).save(any(Category.class));
        }

        @Test
        void notSettingAnchor_doesNotTouchExistingAnchor() {
            // given
            Category target = category(1, "Food", CategoryType.EXPENSE);

            CategoryUpdateRequest request =
                    new CategoryUpdateRequest("Food", CategoryType.EXPENSE, "🍔", null);

            when(categoryRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(target));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            categoryService.updateCategory(1, request, USER_ID);

            // then
            verify(categoryRepository, never()).findByUserIdAndIsCycleAnchorTrue(any());
        }
    }

    @Nested
    class EmojiUniqueness {

        @Test
        void createWithDuplicateEmoji_throws() {
            // given
            CategoryCreateRequest request = new CategoryCreateRequest("Food", CategoryType.EXPENSE, "🍔");

            when(categoryRepository.existsByUserIdAndEmoji(USER_ID, "🍔")).thenReturn(true);

            // when
            // then
            assertThatThrownBy(() -> categoryService.createCategory(request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already used");

            verify(categoryRepository, never()).save(any());
        }

        @Test
        void updateWithDuplicateEmoji_otherCategory_throws() {
            // given
            Category target = category(1, "Food", CategoryType.EXPENSE);
            CategoryUpdateRequest request =
                    new CategoryUpdateRequest("Food", CategoryType.EXPENSE, "🍕", null);

            when(categoryRepository.existsByUserIdAndEmojiAndIdNot(USER_ID, "🍕", 1)).thenReturn(true);

            // when
            // then
            assertThatThrownBy(() -> categoryService.updateCategory(1, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already used");
        }

        @Test
        void updateKeepingSameEmoji_allowed() {
            // given
            Category target = category(1, "Food", CategoryType.EXPENSE);
            target.setEmoji("🍔");

            CategoryUpdateRequest request =
                    new CategoryUpdateRequest("Food Updated", CategoryType.EXPENSE, "🍔", null);

            when(categoryRepository.existsByUserIdAndEmojiAndIdNot(USER_ID, "🍔", 1)).thenReturn(false);
            when(categoryRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(target));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            CategoryResponse result = categoryService.updateCategory(1, request, USER_ID);

            // then
            assertThat(result.name()).isEqualTo("Food Updated");
        }

        @Test
        void blankEmoji_skipsUniquenessCheck() {
            // given
            CategoryCreateRequest request = new CategoryCreateRequest("Food", CategoryType.EXPENSE, "  ");

            when(userService.getUserOrThrow(USER_ID)).thenReturn(user);
            when(categoryRepository.save(any(Category.class)))
                    .thenAnswer(
                            inv -> {
                                Category c = inv.getArgument(0);
                                c.setId(1);
                                return c;
                            });

            // when
            categoryService.createCategory(request, USER_ID);

            // then
            verify(categoryRepository, never()).existsByUserIdAndEmoji(any(), any());
        }
    }

    @Nested
    class CrudBasics {

        @Test
        void createCategory_persistsCorrectFields() {
            // given
            CategoryCreateRequest request =
                    new CategoryCreateRequest("Groceries", CategoryType.EXPENSE, "🛒");

            when(userService.getUserOrThrow(USER_ID)).thenReturn(user);
            when(categoryRepository.save(any(Category.class)))
                    .thenAnswer(
                            inv -> {
                                Category c = inv.getArgument(0);
                                c.setId(1);
                                return c;
                            });

            // when
            CategoryResponse result = categoryService.createCategory(request, USER_ID);

            // then
            assertThat(result.id()).isEqualTo(1);
            assertThat(result.name()).isEqualTo("Groceries");
            assertThat(result.type()).isEqualTo(CategoryType.EXPENSE);
            assertThat(result.emoji()).isEqualTo("🛒");

            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryRepository).save(captor.capture());
            assertThat(captor.getValue().getUser()).isSameAs(user);
        }

        @Test
        void getCategoriesByType_filtersCorrectly() {
            // given
            Category expense = category(1, "Food", CategoryType.EXPENSE);
            when(categoryRepository.findByUserIdAndType(USER_ID, CategoryType.EXPENSE))
                    .thenReturn(List.of(expense));

            // when
            List<CategoryResponse> result =
                    categoryService.getCategoriesForUser(USER_ID, CategoryType.EXPENSE);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).type()).isEqualTo(CategoryType.EXPENSE);
            verify(categoryRepository).findByUserIdAndType(USER_ID, CategoryType.EXPENSE);
            verify(categoryRepository, never()).findByUserId(any());
        }

        @Test
        void getCategoriesNoType_returnsAll() {
            // given
            when(categoryRepository.findByUserId(USER_ID))
                    .thenReturn(
                            List.of(
                                    category(1, "Food", CategoryType.EXPENSE),
                                    category(2, "Salary", CategoryType.INCOME)));

            // when
            List<CategoryResponse> result = categoryService.getCategoriesForUser(USER_ID, null);

            // then
            assertThat(result).hasSize(2);
            verify(categoryRepository).findByUserId(USER_ID);
        }

        @Test
        void deleteCategory_notFound_throws() {
            // given
            when(categoryRepository.findByIdAndUserId(999, USER_ID)).thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(() -> categoryService.deleteCategory(999, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(categoryRepository, never()).delete(any());
        }
    }
}
