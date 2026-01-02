package com.mikeshaggy.backend.service;

import com.mikeshaggy.backend.domain.transaction.Type;
import com.mikeshaggy.backend.domain.transaction.Wallet;
import com.mikeshaggy.backend.domain.user.User;
import com.mikeshaggy.backend.dto.WalletDTO;
import com.mikeshaggy.backend.mapper.WalletMapper;
import com.mikeshaggy.backend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;
    private final EntityFetchingService entityFetchingService;

    public List<WalletDTO> getAllWallets() {
        return mapToDTO(walletRepository.findAll());
    }

    public List<WalletDTO> getWalletsByUserId(Integer userId) {
        entityFetchingService.validateUserExists(userId);
        return mapToDTO(walletRepository.findByUserId(userId));
    }

    public Wallet getWalletById(Integer id) {
        return entityFetchingService.getWalletOrThrow(id);
    }

    public WalletDTO getWalletDTOById(Integer id) {
        Wallet wallet = entityFetchingService.getWalletOrThrow(id);
        return walletMapper.toDTO(wallet);
    }

    @Transactional
    public WalletDTO createWallet(WalletDTO walletDTO) {
        User user = entityFetchingService.getUserOrThrow(walletDTO.userId());

        if (walletRepository.existsByUserIdAndName(walletDTO.userId(), walletDTO.name())) {
            throw new IllegalArgumentException("Wallet with name '" + walletDTO.name() + "' already exists for this user");
        }

        Wallet wallet = walletMapper.toEntity(walletDTO);
        wallet.setUser(user);
        
        if (wallet.getBalance() == null) {
            wallet.setBalance(BigDecimal.ZERO);
        }

        Wallet savedWallet = walletRepository.save(wallet);
        
        log.info("Created wallet '{}' with id: {} for user: {}",
                savedWallet.getName(), savedWallet.getId(), user.getId());
        
        return walletMapper.toDTO(savedWallet);
    }

    @Transactional
    public WalletDTO updateWallet(Integer id, WalletDTO walletDTO) {
        Wallet existingWallet = entityFetchingService.getWalletOrThrow(id);

        if (!existingWallet.getName().equals(walletDTO.name()) &&
            walletRepository.existsByUserIdAndName(existingWallet.getUser().getId(), walletDTO.name())) {
            throw new IllegalArgumentException("Wallet with name '" + walletDTO.name() + "' already exists for this user");
        }

        walletMapper.updateEntityFromDTO(walletDTO, existingWallet);

        Wallet updatedWallet = walletRepository.save(existingWallet);
        
        log.info("Updated wallet id: {} to name: '{}', balance: {}",
                id, updatedWallet.getName(), updatedWallet.getBalance());
        
        return walletMapper.toDTO(updatedWallet);
    }

    @Transactional
    public void deleteWallet(Integer id) {
        Wallet wallet = getWalletById(id);
        
        log.info("Deleting wallet '{}' (id: {}) for user: {}",
                wallet.getName(), id, wallet.getUser().getId());
        
        walletRepository.delete(wallet);
    }

    @Transactional
    public WalletDTO updateWalletBalance(Integer id, BigDecimal newBalance) {
        Wallet wallet = entityFetchingService.getWalletOrThrow(id);
        
        wallet.setBalance(newBalance);
        Wallet updatedWallet = walletRepository.save(wallet);
        return walletMapper.toDTO(updatedWallet);
    }

    @Transactional
    public void applyTransaction(Integer walletId, BigDecimal amount, Type type) {
        Wallet wallet = getWalletById(walletId);
        BigDecimal oldBalance = wallet.getBalance();
        BigDecimal newBalance = calculateBalanceAfterTransaction(oldBalance, amount, type);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);
        
        log.debug("Applied {} transaction of {} to wallet {}: {} -> {}",
                type, amount, walletId, oldBalance, newBalance);
    }

    @Transactional
    public void reverseTransaction(Integer walletId, BigDecimal amount, Type type) {
        Wallet wallet = getWalletById(walletId);
        BigDecimal oldBalance = wallet.getBalance();
        BigDecimal newBalance = calculateBalanceAfterReversal(oldBalance, amount, type);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);
        
        log.debug("Reversed {} transaction of {} from wallet {}: {} -> {}",
                type, amount, walletId, oldBalance, newBalance);
    }

    private BigDecimal calculateBalanceAfterTransaction(BigDecimal currentBalance, BigDecimal amount, Type type) {
        return type.equals(Type.EXPENSE)
                ? currentBalance.subtract(amount)
                : currentBalance.add(amount);
    }

    private BigDecimal calculateBalanceAfterReversal(BigDecimal currentBalance, BigDecimal amount, Type type) {
        return type.equals(Type.EXPENSE)
                ? currentBalance.add(amount)
                : currentBalance.subtract(amount);
    }

    private List<WalletDTO> mapToDTO(List<Wallet> wallets) {
        return wallets.stream()
                .map(walletMapper::toDTO)
                .toList();
    }
}
