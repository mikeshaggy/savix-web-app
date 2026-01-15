package com.mikeshaggy.backend.wallet.dto;

import com.mikeshaggy.backend.wallet.domain.Wallet;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface WalletMapper {

    WalletMapper INSTANCE = Mappers.getMapper(WalletMapper.class);

    @Mapping(source = "user.id", target = "userId")
    WalletDTO toDTO(Wallet wallet);

    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "user.username", ignore = true)
    @Mapping(target = "user.passwordHash", ignore = true)
    @Mapping(target = "user.createdAt", ignore = true)
    @Mapping(target = "user.categories", ignore = true)
    @Mapping(target = "user.wallets", ignore = true)
    @Mapping(target = "transactions", ignore = true)
    Wallet toEntity(WalletDTO walletDTO);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "transactions", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromDTO(WalletDTO walletDTO, @MappingTarget Wallet wallet);
}
