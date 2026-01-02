package com.mikeshaggy.backend.mapper;

import com.mikeshaggy.backend.domain.transaction.Transaction;
import com.mikeshaggy.backend.dto.TransactionDTO;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    TransactionMapper INSTANCE = Mappers.getMapper(TransactionMapper.class);

    @Mapping(source = "wallet.id", target = "walletId")
    @Mapping(source = "category.id", target = "categoryId")
    TransactionDTO toDTO(Transaction transaction);

    @Mapping(source = "walletId", target = "wallet.id")
    @Mapping(source = "categoryId", target = "category.id")
    @Mapping(target = "category.user", ignore = true)
    @Mapping(target = "category.name", ignore = true)
    @Mapping(target = "category.type", ignore = true)
    @Mapping(target = "category.createdAt", ignore = true)
    @Mapping(target = "category.transactions", ignore = true)
    @Mapping(target = "wallet.user", ignore = true)
    @Mapping(target = "wallet.name", ignore = true)
    @Mapping(target = "wallet.balance", ignore = true)
    @Mapping(target = "wallet.createdAt", ignore = true)
    @Mapping(target = "wallet.transactions", ignore = true)
    Transaction toEntity(TransactionDTO transactionDTO);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromDTO(TransactionDTO transactionDTO, @MappingTarget Transaction transaction);
}
