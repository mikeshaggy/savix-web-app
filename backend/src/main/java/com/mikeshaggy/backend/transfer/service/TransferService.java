package com.mikeshaggy.backend.transfer.service;

import com.mikeshaggy.backend.transfer.domain.Transfer;
import com.mikeshaggy.backend.transfer.dto.TransferCreateRequest;
import com.mikeshaggy.backend.transfer.dto.TransferResponse;
import com.mikeshaggy.backend.transfer.dto.TransferUpdateRequest;
import com.mikeshaggy.backend.transfer.repository.TransferRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletBalanceService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

        validateNotFundWallets(fromWallet, toWallet);

        Transfer savedTransfer = persistTransferAndApplyBalance(
                fromWallet, toWallet, request.amount(), userId, request.transferDate(), request.notes());

        return TransferResponse.from(savedTransfer);
    }

    @Transactional
    public Transfer createFundTransfer(
            Integer fromWalletId,
            Integer toWalletId,
            BigDecimal amount,
            UUID userId,
            LocalDate date,
            String notes) {

        validateNotSelfTransfer(fromWalletId, toWalletId);

        Wallet fromWallet = walletService.getWalletEntityByIdInternal(fromWalletId);
        Wallet toWallet = walletService.getWalletEntityByIdInternal(toWalletId);

        return persistTransferAndApplyBalance(fromWallet, toWallet, amount, userId, date, notes);
    }

    @Transactional
    public TransferResponse updateTransfer(Long id, TransferUpdateRequest request, UUID userId) {
        Transfer existingTransfer = getTransferOrThrowForUser(id, userId);

        validateNotFundWallets(existingTransfer.getFromWallet(), existingTransfer.getToWallet());
        validateNotSelfTransfer(request.fromWalletId(), request.toWalletId());

        Wallet oldFromWallet = existingTransfer.getFromWallet();
        Wallet oldToWallet = existingTransfer.getToWallet();
        BigDecimal oldAmount = existingTransfer.getAmount();
        LocalDate oldTransferDate = existingTransfer.getTransferDate();

        Wallet newFromWallet = oldFromWallet;
        if (!request.fromWalletId().equals(oldFromWallet.getId())) {
            newFromWallet = walletService.getWalletEntityByIdForUser(request.fromWalletId(), userId);
        }

        Wallet newToWallet = oldToWallet;
        if (!request.toWalletId().equals(oldToWallet.getId())) {
            newToWallet = walletService.getWalletEntityByIdForUser(request.toWalletId(), userId);
        }

        validateNotFundWallets(newFromWallet, newToWallet);

        request.applyTo(existingTransfer);
        existingTransfer.setFromWallet(newFromWallet);
        existingTransfer.setToWallet(newToWallet);

        Transfer updatedTransfer = transferRepository.save(existingTransfer);

        walletBalanceService.adjustForTransferEdit(
                oldFromWallet, oldToWallet, oldAmount,
                newFromWallet, newToWallet, updatedTransfer.getAmount(),
                updatedTransfer.getId(), oldTransferDate, updatedTransfer.getTransferDate()
        );

        log.info("Transfer updated: transferId={}, userId={}, sourceWalletId={}, targetWalletId={}, amount={}",
            id, userId, newFromWallet.getId(), newToWallet.getId(), updatedTransfer.getAmount());

        return TransferResponse.from(updatedTransfer);
    }

    @Transactional
    public void deleteTransfer(Long id, UUID userId) {
        Transfer transfer = getTransferOrThrowForUser(id, userId);

        validateNotFundWallets(transfer.getFromWallet(), transfer.getToWallet());

        walletBalanceService.reverseTransfer(
                transfer.getFromWallet().getId(),
                transfer.getToWallet().getId(),
                transfer.getAmount(),
                userId,
                transfer.getId(),
                transfer.getTransferDate()
        );

        log.info("Transfer deleted: transferId={}, userId={}, sourceWalletId={}, targetWalletId={}, amount={}",
            id, userId, transfer.getFromWallet().getId(), transfer.getToWallet().getId(), transfer.getAmount());

        transferRepository.delete(transfer);
    }

    private Transfer persistTransferAndApplyBalance(
            Wallet fromWallet,
            Wallet toWallet,
            BigDecimal amount,
            UUID userId,
            LocalDate date,
            String notes) {

        Transfer transfer = Transfer.builder()
                .fromWallet(fromWallet)
                .toWallet(toWallet)
                .amount(amount)
                .transferDate(date)
                .notes(notes)
                .build();

        Transfer savedTransfer = transferRepository.save(transfer);

        walletBalanceService.applyTransfer(
                fromWallet.getId(), toWallet.getId(), amount,
                userId, savedTransfer.getId(), savedTransfer.getTransferDate());

        log.info("Transfer created: transferId={}, userId={}, sourceWalletId={}, targetWalletId={}, amount={}",
                savedTransfer.getId(), userId, fromWallet.getId(), toWallet.getId(), savedTransfer.getAmount());

        return savedTransfer;
    }

    private void validateNotSelfTransfer(Integer fromWalletId, Integer toWalletId) {
        if (fromWalletId.equals(toWalletId)) {
            log.warn("Transfer validation failed: reason=self_transfer, walletId={}", fromWalletId);
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }
    }

    private void validateNotFundWallets(Wallet fromWallet, Wallet toWallet) {
        if (fromWallet.isFund() || toWallet.isFund()) {
            log.warn("Transfer validation failed: reason=fund_wallet_transfer, fromWalletId={}, toWalletId={}",
                    fromWallet.getId(), toWallet.getId());
            throw new IllegalArgumentException(
                    "Transfers to or from fund wallets are not permitted. " +
                    "Use fund deposit or withdrawal operations instead.");
        }
    }

    private Transfer getTransferOrThrowForUser(Long id, UUID userId) {
        return transferRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found with id: " + id));
    }
}
