package com.mikeshaggy.backend.transfer.service;

import com.mikeshaggy.backend.transfer.domain.Transfer;
import com.mikeshaggy.backend.transfer.dto.TransferCreateRequest;
import com.mikeshaggy.backend.transfer.dto.TransferResponse;
import com.mikeshaggy.backend.transfer.dto.TransferUpdateRequest;
import com.mikeshaggy.backend.transfer.repo.TransferRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletBalanceService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferService {

    private final TransferRepository transferRepository;
    private final WalletService walletService;
    private final WalletBalanceService walletBalanceService;

    public List<TransferResponse> getTransfersForUser(UUID userId) {
        return transferRepository.findAllByUserId(userId).stream()
                .map(TransferResponse::from)
                .toList();
    }

    public TransferResponse getTransferByIdForUser(Long id, UUID userId) {
        Transfer transfer = getTransferOrThrowForUser(id, userId);
        return TransferResponse.from(transfer);
    }

    public List<TransferResponse> getTransfersByWalletIdForUser(Integer walletId, UUID userId) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        return transferRepository.findByWalletIdAndUserId(walletId, userId).stream()
                .map(TransferResponse::from)
                .toList();
    }

    @Transactional
    public TransferResponse createTransfer(TransferCreateRequest request, UUID userId) {
        validateNotSelfTransfer(request.fromWalletId(), request.toWalletId());

        Wallet fromWallet = walletService.getWalletEntityByIdForUser(request.fromWalletId(), userId);
        Wallet toWallet = walletService.getWalletEntityByIdForUser(request.toWalletId(), userId);

        Transfer transfer = Transfer.builder()
                .fromWallet(fromWallet)
                .toWallet(toWallet)
                .amount(request.amount())
                .transferDate(request.transferDate())
                .notes(request.notes())
                .build();

        Transfer savedTransfer = transferRepository.save(transfer);

        walletBalanceService.applyTransfer(fromWallet.getId(), toWallet.getId(), request.amount(),
                userId, savedTransfer.getId(), savedTransfer.getTransferDate());

        log.info("Created transfer (id: {}) of {} from wallet {} to wallet {} for user {}",
                savedTransfer.getId(), savedTransfer.getAmount(),
                fromWallet.getId(), toWallet.getId(), userId);

        return TransferResponse.from(savedTransfer);
    }

    @Transactional
    public TransferResponse updateTransfer(Long id, TransferUpdateRequest request, UUID userId) {
        Transfer existingTransfer = getTransferOrThrowForUser(id, userId);

        validateNotSelfTransfer(request.fromWalletId(), request.toWalletId());

        Wallet oldFromWallet = existingTransfer.getFromWallet();
        Wallet oldToWallet = existingTransfer.getToWallet();
        BigDecimal oldAmount = existingTransfer.getAmount();

        Wallet newFromWallet = oldFromWallet;
        if (!request.fromWalletId().equals(oldFromWallet.getId())) {
            newFromWallet = walletService.getWalletEntityByIdForUser(request.fromWalletId(), userId);
        }

        Wallet newToWallet = oldToWallet;
        if (!request.toWalletId().equals(oldToWallet.getId())) {
            newToWallet = walletService.getWalletEntityByIdForUser(request.toWalletId(), userId);
        }

        request.applyTo(existingTransfer);
        existingTransfer.setFromWallet(newFromWallet);
        existingTransfer.setToWallet(newToWallet);

        Transfer updatedTransfer = transferRepository.save(existingTransfer);

        walletBalanceService.adjustForTransferEdit(
                oldFromWallet, oldToWallet, oldAmount,
                newFromWallet, newToWallet, updatedTransfer.getAmount(),
                updatedTransfer.getId(), updatedTransfer.getTransferDate()
        );

        log.info("Updated transfer id: {} to amount: {}, from wallet: {} to wallet: {}",
                id, updatedTransfer.getAmount(),
                newFromWallet.getId(), newToWallet.getId());

        return TransferResponse.from(updatedTransfer);
    }

    @Transactional
    public void deleteTransfer(Long id, UUID userId) {
        Transfer transfer = getTransferOrThrowForUser(id, userId);

        walletBalanceService.reverseTransfer(
                transfer.getFromWallet().getId(),
                transfer.getToWallet().getId(),
                transfer.getAmount(),
                userId,
                transfer.getId(),
                transfer.getTransferDate()
        );

        log.info("Deleting transfer (id: {}) of {} from wallet {} to wallet {}, rolled back balances",
                id, transfer.getAmount(),
                transfer.getFromWallet().getId(), transfer.getToWallet().getId());

        transferRepository.delete(transfer);
    }

    private void validateNotSelfTransfer(Integer fromWalletId, Integer toWalletId) {
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }
    }

    private Transfer getTransferOrThrowForUser(Long id, UUID userId) {
        return transferRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found with id: " + id));
    }
}
