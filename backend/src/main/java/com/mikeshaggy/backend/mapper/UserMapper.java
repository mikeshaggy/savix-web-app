package com.mikeshaggy.backend.mapper;

import com.mikeshaggy.backend.domain.user.User;
import com.mikeshaggy.backend.dto.UserDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    UserDTO toDTO(User user);

    @Mapping(target = "password", ignore = true)
    @Mapping(target = "wallets", ignore = true)
    @Mapping(target = "categories", ignore = true)
    User toEntity(UserDTO userDTO);
}
