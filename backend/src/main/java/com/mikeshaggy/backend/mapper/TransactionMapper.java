package com.mikeshaggy.backend.mapper;

import com.mikeshaggy.backend.domain.transaction.Transaction;
import com.mikeshaggy.backend.dto.TransactionDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    TransactionMapper INSTANCE = Mappers.getMapper(TransactionMapper.class);

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    TransactionDTO toDTO(Transaction transaction);

    @Mapping(source = "userId", target = "user.id")
    @Mapping(source = "categoryId", target = "category.id")
    @Mapping(target = "category.name", ignore = true)
    @Mapping(target = "category.user", ignore = true)
    @Mapping(target = "category.createdAt", ignore = true)
    @Mapping(target = "category.transactions", ignore = true)
    @Mapping(target = "user.username", ignore = true)
    @Mapping(target = "user.password", ignore = true)
    @Mapping(target = "user.createdAt", ignore = true)
    @Mapping(target = "user.categories", ignore = true)
    @Mapping(target = "user.transactions", ignore = true)
    Transaction toEntity(TransactionDTO transactionDTO);
}
