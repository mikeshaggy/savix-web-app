package com.mikeshaggy.backend.wallet.service;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.service.WalletEntryService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.MANDATORY)
public class WalletBalanceService {

    private final WalletEntryService walletEntryService;
    private final WalletService walletService;

    public void applyTransaction(Integer walletId, BigDecimal amount, CategoryType type, UUID userId,
                                 Long transactionId, LocalDate transactionDate) {
        Wallet wallet = walletService.getWalletEntityByIdForUser(walletId, userId);
        BigDecimal amountSigned = resolveSignedAmount(amount, type);

        walletEntryService.createEntry(wallet, amountSigned, transactionDate, SourceType.TRANSACTION, transactionId);
        adjustBalance(wallet, amountSigned);

        log.debug("Applied {} transaction #{} of {} to wallet {}: balance now {}",
                type, transactionId, amount, walletId, wallet.getBalance());
    }

    public void adjustForTransactionEdit(Wallet oldWallet, BigDecimal oldAmount, CategoryType oldType,
                                         Wallet newWallet, BigDecimal newAmount, CategoryType newType,
                                         Long transactionId, LocalDate newTransactionDate) {
        BigDecimal oldSigned = resolveSignedAmount(oldAmount, oldType);
        BigDecimal newSigned = resolveSignedAmount(newAmount, newType);

        boolean walletChanged = !oldWallet.getId().equals(newWallet.getId());

        if (walletChanged) {
            walletEntryService.createEntry(oldWallet, oldSigned.negate(), newTransactionDate,
                    SourceType.ADJUSTMENT, transactionId);
            adjustBalance(oldWallet, oldSigned.negate());

            walletEntryService.createEntry(newWallet, newSigned, newTransactionDate,
                    SourceType.ADJUSTMENT, transactionId);
            adjustBalance(newWallet, newSigned);

            log.debug("Transaction #{} moved from wallet {} to wallet {}: reverted {} on old, applied {} on new",
                    transactionId, oldWallet.getId(), newWallet.getId(), oldSigned.negate(), newSigned);
        } else {
            BigDecimal delta = newSigned.subtract(oldSigned);
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                walletEntryService.createEntry(oldWallet, delta, newTransactionDate,
                        SourceType.ADJUSTMENT, transactionId);
                adjustBalance(oldWallet, delta);

                log.debug("Transaction #{} adjusted on wallet {}: delta {}",
                        transactionId, oldWallet.getId(), delta);
            } else {
                log.debug("Transaction #{} edited with no balance effect, skipping adjustment",
                        transactionId);
            }
        }
    }

    public void reverseTransaction(Integer walletId, BigDecimal amount, CategoryType type, UUID userId,
                                   Long transactionId, LocalDate transactionDate) {
        Wallet wallet = walletService.getWalletEntityByIdForUser(walletId, userId);
        BigDecimal amountSigned = resolveSignedAmount(amount, type);

        walletEntryService.createEntry(wallet, amountSigned.negate(), transactionDate,
                SourceType.ADJUSTMENT, transactionId);
        adjustBalance(wallet, amountSigned.negate());

        log.debug("Reversed {} transaction #{} of {} from wallet {}: balance now {}",
                type, transactionId, amount, walletId, wallet.getBalance());
    }

    public void applyTransfer(Integer fromWalletId, Integer toWalletId, BigDecimal amount, UUID userId,
                              Long transferId, LocalDate transferDate) {
        Wallet fromWallet = walletService.getWalletEntityByIdForUser(fromWalletId, userId);
        Wallet toWallet = walletService.getWalletEntityByIdForUser(toWalletId, userId);

        walletEntryService.createEntry(fromWallet, amount.negate(), transferDate, SourceType.TRANSFER, transferId);
        walletEntryService.createEntry(toWallet, amount, transferDate, SourceType.TRANSFER, transferId);

        adjustBalance(fromWallet, amount.negate());
        adjustBalance(toWallet, amount);

        log.debug("Applied transfer #{} of {} from wallet {} to wallet {}", transferId, amount,
                fromWalletId, toWalletId);
    }

    public void adjustForTransferEdit(Wallet oldFromWallet, Wallet oldToWallet, BigDecimal oldAmount,
                                      Wallet newFromWallet, Wallet newToWallet, BigDecimal newAmount,
                                      Long transferId, LocalDate newTransferDate) {
        boolean fromChanged = !oldFromWallet.getId().equals(newFromWallet.getId());
        boolean toChanged = !oldToWallet.getId().equals(newToWallet.getId());

        if (fromChanged) {
            createAdjustmentEntry(oldFromWallet, oldAmount, newTransferDate, transferId);
            adjustBalance(oldFromWallet, oldAmount);
            createAdjustmentEntry(newFromWallet, newAmount.negate(), newTransferDate, transferId);
            adjustBalance(newFromWallet, newAmount.negate());
        } else {
            BigDecimal fromDelta = oldAmount.subtract(newAmount);
            if (fromDelta.compareTo(BigDecimal.ZERO) != 0) {
                createAdjustmentEntry(oldFromWallet, fromDelta, newTransferDate, transferId);
                adjustBalance(oldFromWallet, fromDelta);
            }
        }

        if (toChanged) {
            createAdjustmentEntry(oldToWallet, oldAmount.negate(), newTransferDate, transferId);
            adjustBalance(oldToWallet, oldAmount.negate());
            createAdjustmentEntry(newToWallet, newAmount, newTransferDate, transferId);
            adjustBalance(newToWallet, newAmount);
        } else {
            BigDecimal toDelta = newAmount.subtract(oldAmount);
            if (toDelta.compareTo(BigDecimal.ZERO) != 0) {
                createAdjustmentEntry(oldToWallet, toDelta, newTransferDate, transferId);
                adjustBalance(oldToWallet, toDelta);
            }
        }

        log.debug("Adjusted transfer #{}: old({}->{}, {}), new({}->{}, {})",
                transferId, oldFromWallet.getId(), oldToWallet.getId(), oldAmount,
                newFromWallet.getId(), newToWallet.getId(), newAmount);
    }

    public void reverseTransfer(Integer fromWalletId, Integer toWalletId, BigDecimal amount, UUID userId,
                                Long transferId, LocalDate transferDate) {
        Wallet fromWallet = walletService.getWalletEntityByIdForUser(fromWalletId, userId);
        Wallet toWallet = walletService.getWalletEntityByIdForUser(toWalletId, userId);

        walletEntryService.createEntry(fromWallet, amount, transferDate, SourceType.ADJUSTMENT, transferId);
        walletEntryService.createEntry(toWallet, amount.negate(), transferDate, SourceType.ADJUSTMENT, transferId);

        adjustBalance(fromWallet, amount);
        adjustBalance(toWallet, amount.negate());

        log.debug("Reversed transfer #{} of {} from wallet {} to wallet {}", transferId, amount,
                fromWalletId, toWalletId);
    }

    private void createAdjustmentEntry(Wallet wallet, BigDecimal amountSigned, LocalDate entryDate, Long sourceId) {
        walletEntryService.createEntry(wallet, amountSigned, entryDate, SourceType.ADJUSTMENT, sourceId);
    }

    void adjustBalance(Wallet wallet, BigDecimal delta) {
        wallet.setBalance(wallet.getBalance().add(delta));
        walletService.saveWallet(wallet);
    }

    BigDecimal resolveSignedAmount(BigDecimal amount, CategoryType type) {
        return type.equals(CategoryType.EXPENSE) ? amount.negate() : amount;
    }
}
