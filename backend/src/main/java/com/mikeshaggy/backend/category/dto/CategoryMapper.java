package com.mikeshaggy.backend.category.dto;

import com.mikeshaggy.backend.category.domain.Category;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
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
    @Mapping(target = "user.wallets", ignore = true)
    @Mapping(target = "transactions", ignore = true)
    Category toEntity(CategoryDTO categoryDTO);

    @BeanMapping(nullValuePropertyMappingStrategy = org.mapstruct.NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromDTO(CategoryDTO categoryDTO, @MappingTarget Category category);
}
