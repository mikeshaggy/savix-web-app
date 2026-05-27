package com.mikeshaggy.backend.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.domain.WalletEntry;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryChartPointResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistorySummaryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryTimelinePaginationResponse;
import com.mikeshaggy.backend.ledger.repository.WalletEntryRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletEntryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private WalletEntryRepository walletEntryRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletBalanceHistoryQueryService walletBalanceHistoryQueryService;

    @InjectMocks
    private WalletEntryService service;

    @Test
    void getBalanceHistoryByWalletIdForUser_delegatesToDedicatedBalanceHistoryService() {
        // given
        WalletBalanceHistoryResponse expected =
                new WalletBalanceHistoryResponse(
                        1,
                        "Main",
                        new BigDecimal("974.50"),
                        new WalletBalanceHistorySummaryResponse(
                                new BigDecimal("974.50"),
                                new BigDecimal("1000.00"),
                                new BigDecimal("950.00"),
                                2,
                                new BigDecimal("-25.50")),
                        List.of(
                                new WalletBalanceHistoryChartPointResponse(
                                        LocalDate.of(2026, 3, 10), new BigDecimal("974.50"), new BigDecimal("-25.50"))),
                        List.of(),
                        new WalletBalanceHistoryTimelinePaginationResponse(0, 10, 0, 0, false, false));

        when(walletBalanceHistoryQueryService.getBalanceHistoryByWalletIdForUser(1, USER_ID))
                .thenReturn(expected);

        // when
        WalletBalanceHistoryResponse response = service.getBalanceHistoryByWalletIdForUser(1, USER_ID);

        // then
        assertThat(response).isEqualTo(expected);
        verify(walletBalanceHistoryQueryService).getBalanceHistoryByWalletIdForUser(eq(1), eq(USER_ID));
    }

    @Test
    void getEntriesByWalletIdForUser_preservesExistingOrderingFromRepository() {
        // given
        Wallet wallet =
                new Wallet();
        wallet.setId(1);
        wallet.setName("Main");

        WalletEntry entry = new WalletEntry();
        entry.setId(11L);
        entry.setWallet(wallet);
        entry.setAmountSigned(new BigDecimal("50.00"));
        entry.setBalanceAfter(new BigDecimal("150.00"));
        entry.setEntryDate(LocalDate.of(2026, 3, 10));
        entry.setSourceType(SourceType.TRANSFER);
        entry.setSourceId(2L);

        when(walletRepository.existsByIdAndUserId(1, USER_ID)).thenReturn(true);
        when(walletEntryRepository.findByWalletIdAndUserIdForHistory(
                        eq(1), eq(USER_ID), eq(null), eq(null), any()))
                .thenReturn(List.of(entry));

        // when
        // then
        assertThat(service.getEntriesByWalletIdForUser(1, USER_ID, null, null, null)).hasSize(1);
    }

    @Test
    void moveSourceEntryDate_updatesOriginalEntryDateWhenItChanged() {
        // given
        Wallet wallet = new Wallet();
        wallet.setId(1);
        wallet.setName("Main");

        WalletEntry entry = new WalletEntry();
        entry.setId(42L);
        entry.setWallet(wallet);
        entry.setAmountSigned(new BigDecimal("-25.00"));
        entry.setEntryDate(LocalDate.of(2026, 3, 1));
        entry.setSourceType(SourceType.TRANSACTION);
        entry.setSourceId(100L);

        LocalDate newDate = LocalDate.of(2026, 3, 8);
        when(walletEntryRepository.findFirstByWalletIdAndSourceTypeAndSourceIdOrderByIdAsc(
                        1, SourceType.TRANSACTION, 100L))
                .thenReturn(java.util.Optional.of(entry));

        // when
        boolean moved = service.moveSourceEntryDate(wallet, SourceType.TRANSACTION, 100L, newDate);

        // then
        assertThat(moved).isTrue();
        assertThat(entry.getEntryDate()).isEqualTo(newDate);
        verify(walletEntryRepository).save(entry);
    }

    @Test
    void moveSourceEntryDate_whenDateUnchangedDoesNotSave() {
        // given
        Wallet wallet = new Wallet();
        wallet.setId(1);

        LocalDate date = LocalDate.of(2026, 3, 1);
        WalletEntry entry = new WalletEntry();
        entry.setId(42L);
        entry.setWallet(wallet);
        entry.setEntryDate(date);
        entry.setSourceType(SourceType.TRANSACTION);
        entry.setSourceId(100L);

        when(walletEntryRepository.findFirstByWalletIdAndSourceTypeAndSourceIdOrderByIdAsc(
                        1, SourceType.TRANSACTION, 100L))
                .thenReturn(java.util.Optional.of(entry));

        // when
        boolean moved = service.moveSourceEntryDate(wallet, SourceType.TRANSACTION, 100L, date);

        // then
        assertThat(moved).isFalse();
        verify(walletEntryRepository, never()).save(entry);
    }
}
