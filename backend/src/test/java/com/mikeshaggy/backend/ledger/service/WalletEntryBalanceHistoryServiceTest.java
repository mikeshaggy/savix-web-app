package com.mikeshaggy.backend.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.ledger.domain.WalletEntry;
import com.mikeshaggy.backend.ledger.repository.WalletEntryRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletEntryBalanceHistoryServiceTest {

    @Mock
    private WalletEntryRepository walletEntryRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletEntryBalanceHistoryService service;

    private Wallet wallet(int id, String balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setName("Test Wallet");
        wallet.setBalance(new BigDecimal(balance));
        return wallet;
    }

    private WalletEntry entry(long id, Wallet wallet, String amount, String date) {
        WalletEntry entry = new WalletEntry();
        entry.setId(id);
        entry.setWallet(wallet);
        entry.setAmountSigned(new BigDecimal(amount));
        entry.setEntryDate(LocalDate.parse(date));
        return entry;
    }

    @Test
    void recalculateWalletLedger_setsRunningBalancesInRepositoryOrder_andUpdatesWalletBalance() {
        // given
        Wallet wallet = wallet(1, "0.00");
        WalletEntry e1 = entry(10L, wallet, "100.00", "2026-03-01");
        WalletEntry e2 = entry(11L, wallet, "-30.00", "2026-03-02");
        WalletEntry e3 = entry(12L, wallet, "10.00", "2026-03-03");

        when(walletRepository.findById(1)).thenReturn(Optional.of(wallet));
        when(walletEntryRepository.findByWalletIdOrderByLedgerOrder(1)).thenReturn(List.of(e1, e2, e3));

        service.recalculateWalletLedger(1L);

        // when
        assertThat(e1.getBalanceAfter()).isEqualByComparingTo("100.00");
        // then
        assertThat(e2.getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(e3.getBalanceAfter()).isEqualByComparingTo("80.00");
        assertThat(wallet.getBalance()).isEqualByComparingTo("80.00");

        verify(walletEntryRepository).saveAll(List.of(e1, e2, e3));
        verify(walletRepository).save(wallet);
    }

    @Test
    void recalculateWalletLedger_recalculatesFromBackdatedEntry_andKeepsDeterministicBalances() {
        // given
        Wallet wallet = wallet(7, "0.00");
        WalletEntry backdatedAdjustment = entry(100L, wallet, "25.00", "2026-02-28");
        WalletEntry expense = entry(101L, wallet, "-10.00", "2026-03-01");
        WalletEntry income = entry(102L, wallet, "50.00", "2026-03-02");

        when(walletRepository.findById(7)).thenReturn(Optional.of(wallet));
        when(walletEntryRepository.findByWalletIdOrderByLedgerOrder(7))
                .thenReturn(List.of(backdatedAdjustment, expense, income));

        service.recalculateWalletLedger(7L);

        // when
        assertThat(backdatedAdjustment.getBalanceAfter()).isEqualByComparingTo("25.00");
        // then
        assertThat(expense.getBalanceAfter()).isEqualByComparingTo("15.00");
        assertThat(income.getBalanceAfter()).isEqualByComparingTo("65.00");
        assertThat(wallet.getBalance()).isEqualByComparingTo("65.00");
    }

    @Test
    void recalculateWalletLedger_whenNoEntries_setsWalletBalanceToZero_andSkipsSaveAll() {
        // given
        Wallet wallet = wallet(2, "999.99");
        when(walletRepository.findById(2)).thenReturn(Optional.of(wallet));
        when(walletEntryRepository.findByWalletIdOrderByLedgerOrder(2)).thenReturn(List.of());

        service.recalculateWalletLedger(2L);

        // when
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
        // then
        verify(walletEntryRepository, never()).saveAll(anyList());
        verify(walletRepository).save(wallet);
    }

    @Test
    void recalculateWalletLedger_walletNotFound_throws() {
        // given
        when(walletRepository.findById(99)).thenReturn(Optional.empty());

        // when
        // then
        assertThatThrownBy(() -> service.recalculateWalletLedger(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Wallet not found with id: 99");

        verify(walletEntryRepository, never()).findByWalletIdOrderByLedgerOrder(anyInt());
    }

    @Test
    void recalculateWalletLedger_invalidWalletId_throws() {
        // given
        // when
        // then
        assertThatThrownBy(() -> service.recalculateWalletLedger(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("walletId cannot be null");

        assertThatThrownBy(() -> service.recalculateWalletLedger(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("walletId out of supported range");
    }
}
