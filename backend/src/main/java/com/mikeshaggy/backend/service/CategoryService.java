package com.mikeshaggy.backend.service;

import com.mikeshaggy.backend.domain.transaction.Category;
import com.mikeshaggy.backend.domain.transaction.Type;
import com.mikeshaggy.backend.domain.user.User;
import com.mikeshaggy.backend.dto.CategoryDTO;
import com.mikeshaggy.backend.mapper.CategoryMapper;
import com.mikeshaggy.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final EntityFetchingService entityFetchingService;

    public List<CategoryDTO> getAllCategories() {
        return mapToDTO(categoryRepository.findAll());
    }

    public List<CategoryDTO> getCategoriesByUserId(Integer userId) {
        entityFetchingService.validateUserExists(userId);
        return mapToDTO(categoryRepository.findByUserId(userId));
    }

    public List<CategoryDTO> getCategoriesByUserIdAndType(Integer userId, Type type) {
        entityFetchingService.validateUserExists(userId);
        return mapToDTO(categoryRepository.findByUserIdAndType(userId, type));
    }

    public CategoryDTO getCategoryById(Integer id) {
        Category category = entityFetchingService.getCategoryOrThrow(id);
        return categoryMapper.toDTO(category);
    }

    @Transactional
    public CategoryDTO createCategory(CategoryDTO categoryDTO) {
        User user = entityFetchingService.getUserOrThrow(categoryDTO.userId());

        Category category = categoryMapper.toEntity(categoryDTO);
        category.setUser(user);
        Category savedCategory = categoryRepository.save(category);
        
        log.info("Created category '{}' with id: {} for user: {}", 
                savedCategory.getName(), savedCategory.getId(), categoryDTO.userId());
        
        return categoryMapper.toDTO(savedCategory);
    }

    @Transactional
    public CategoryDTO updateCategory(Integer id, CategoryDTO categoryDTO) {
        Category category = entityFetchingService.getCategoryOrThrow(id);

        categoryMapper.updateEntityFromDTO(categoryDTO, category);
        
        Category updatedCategory = categoryRepository.save(category);
        
        log.info("Updated category id: {} to name: '{}', type: {}", 
                id, categoryDTO.name(), categoryDTO.type());

        return categoryMapper.toDTO(updatedCategory);
    }

    @Transactional
    public void deleteCategory(Integer id) {
        Category category = entityFetchingService.getCategoryOrThrow(id);
        
        log.info("Deleting category '{}' (id: {}) for user: {}", 
                category.getName(), id, category.getUser().getId());
        
        categoryRepository.delete(category);
    }

    private List<CategoryDTO> mapToDTO(List<Category> categories) {
        return categories.stream()
                .map(categoryMapper::toDTO)
                .toList();
    }
}
