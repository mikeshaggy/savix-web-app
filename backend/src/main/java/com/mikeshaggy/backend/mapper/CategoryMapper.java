package com.mikeshaggy.backend.mapper;

import com.mikeshaggy.backend.domain.transaction.Category;
import com.mikeshaggy.backend.dto.CategoryDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryMapper INSTANCE = Mappers.getMapper(CategoryMapper.class);

    @Mapping(source = "user.id", target = "userId")
    CategoryDTO toDTO(Category category);

    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "user.username", ignore = true)
    @Mapping(target = "user.password", ignore = true)
    @Mapping(target = "user.createdAt", ignore = true)
    @Mapping(target = "user.categories", ignore = true)
    @Mapping(target = "user.transactions", ignore = true)
    @Mapping(target = "transactions", ignore = true)
    Category toEntity(CategoryDTO categoryDTO);
}
