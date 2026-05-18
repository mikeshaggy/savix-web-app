package com.mikeshaggy.backend.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.domain.WalletEntry;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryTimelinePaginationResponse;
import com.mikeshaggy.backend.ledger.repo.WalletEntryRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repo.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletBalanceHistoryQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletEntryRepository walletEntryRepository;

    @InjectMocks
    private WalletBalanceHistoryQueryService service;

    @Test
    void getBalanceHistoryByWalletIdForUser_returnsPreparedResponseWithConsistentSummaryChartAndTimeline() {
        // given
        Wallet wallet = wallet(1, "Main", "120.00");

        WalletEntry e3 =
                entry(
                        12L,
                        wallet,
                        "-30.00",
                        "120.00",
                        "2026-03-10",
                        "2026-03-10T08:03:00Z",
                        SourceType.TRANSACTION,
                        3L);
        WalletEntry e2 =
                entry(
                        11L,
                        wallet,
                        "50.00",
                        "150.00",
                        "2026-03-10",
                        "2026-03-10T08:00:00Z",
                        SourceType.TRANSFER,
                        2L);
        WalletEntry e1 =
                entry(
                        10L,
                        wallet,
                        "100.00",
                        "100.00",
                        "2026-03-08",
                        "2026-03-08T07:00:00Z",
                        SourceType.ADJUSTMENT,
                        1L);

        when(walletRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(wallet));
        when(walletEntryRepository.findByWalletIdAndUserIdForHistory(
                        eq(1),
                        eq(USER_ID),
                        eq(null),
                        eq(null),
                        eq(org.springframework.data.domain.Pageable.unpaged())))
                .thenReturn(List.of(e3, e2, e1));

        // when
        WalletBalanceHistoryResponse response =
                service.getBalanceHistoryByWalletIdForUser(1, USER_ID, null, null, 0, 10);

        // then
        assertThat(response.walletId()).isEqualTo(1);
        assertThat(response.walletName()).isEqualTo("Main");
        assertThat(response.currentBalance()).isEqualByComparingTo("120.00");

        assertThat(response.summary().latestBalance()).isEqualByComparingTo("120.00");
        assertThat(response.summary().highestBalance()).isEqualByComparingTo("150.00");
        assertThat(response.summary().lowestBalance()).isEqualByComparingTo("100.00");
        assertThat(response.summary().entriesCount()).isEqualTo(3);
        assertThat(response.summary().netChange()).isEqualByComparingTo("120.00");

        assertThat(response.chart()).hasSize(2);
        assertThat(response.chart().get(0).date()).isEqualTo(LocalDate.of(2026, 3, 8));
        assertThat(response.chart().get(0).closingBalance()).isEqualByComparingTo("100.00");
        assertThat(response.chart().get(0).netChange()).isEqualByComparingTo("100.00");
        assertThat(response.chart().get(1).date()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(response.chart().get(1).closingBalance()).isEqualByComparingTo("120.00");
        assertThat(response.chart().get(1).netChange()).isEqualByComparingTo("20.00");

        assertThat(response.timeline()).hasSize(2);
        assertThat(response.timeline().get(0).date()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(response.timeline().get(0).entries()).hasSize(2);
        assertThat(response.timeline().get(0).entries().get(0).id()).isEqualTo(12L);
        assertThat(response.timeline().get(0).entries().get(1).id()).isEqualTo(11L);
        assertThat(response.timeline().get(1).date()).isEqualTo(LocalDate.of(2026, 3, 8));
        assertThat(response.timelinePagination())
                .isEqualTo(
                        new WalletBalanceHistoryTimelinePaginationResponse(
                                0, 10, 3, // 3 entry rows: 2 on Mar 10 + 1 on Mar 8
                                1, false, false));
    }

    @Test
    void getBalanceHistoryByWalletIdForUser_paginatesTimelineByEntryRowsNotGroups() {
        // given
        Wallet wallet = wallet(1, "Main", "100.00");

        // Mar 12 → 3 entries
        WalletEntry e12a =
                entry(
                        1L,
                        wallet,
                        "10.00",
                        "100.00",
                        "2026-03-12",
                        "2026-03-12T10:00:00Z",
                        SourceType.TRANSACTION,
                        1L);
        WalletEntry e12b =
                entry(
                        2L,
                        wallet,
                        "10.00",
                        "90.00",
                        "2026-03-12",
                        "2026-03-12T09:00:00Z",
                        SourceType.TRANSACTION,
                        2L);
        WalletEntry e12c =
                entry(
                        3L,
                        wallet,
                        "10.00",
                        "80.00",
                        "2026-03-12",
                        "2026-03-12T08:00:00Z",
                        SourceType.TRANSACTION,
                        3L);
        // Mar 11 → 4 entries
        WalletEntry e11a =
                entry(
                        4L,
                        wallet,
                        "10.00",
                        "70.00",
                        "2026-03-11",
                        "2026-03-11T11:00:00Z",
                        SourceType.TRANSACTION,
                        4L);
        WalletEntry e11b =
                entry(
                        5L,
                        wallet,
                        "10.00",
                        "60.00",
                        "2026-03-11",
                        "2026-03-11T10:00:00Z",
                        SourceType.TRANSACTION,
                        5L);
        WalletEntry e11c =
                entry(
                        6L,
                        wallet,
                        "10.00",
                        "50.00",
                        "2026-03-11",
                        "2026-03-11T09:00:00Z",
                        SourceType.TRANSACTION,
                        6L);
        WalletEntry e11d =
                entry(
                        7L,
                        wallet,
                        "10.00",
                        "40.00",
                        "2026-03-11",
                        "2026-03-11T08:00:00Z",
                        SourceType.TRANSACTION,
                        7L);
        // Mar 10 → 6 entries (would exceed pageSize=10 if added to page 0)
        WalletEntry e10a =
                entry(
                        8L,
                        wallet,
                        "10.00",
                        "30.00",
                        "2026-03-10",
                        "2026-03-10T13:00:00Z",
                        SourceType.TRANSACTION,
                        8L);
        WalletEntry e10b =
                entry(
                        9L,
                        wallet,
                        "10.00",
                        "20.00",
                        "2026-03-10",
                        "2026-03-10T12:00:00Z",
                        SourceType.TRANSACTION,
                        9L);
        WalletEntry e10c =
                entry(
                        10L,
                        wallet,
                        "10.00",
                        "10.00",
                        "2026-03-10",
                        "2026-03-10T11:00:00Z",
                        SourceType.TRANSACTION,
                        10L);
        WalletEntry e10d =
                entry(
                        11L,
                        wallet,
                        "10.00",
                        "0.00",
                        "2026-03-10",
                        "2026-03-10T10:00:00Z",
                        SourceType.TRANSACTION,
                        11L);
        WalletEntry e10e =
                entry(
                        12L,
                        wallet,
                        "10.00",
                        "-10.00",
                        "2026-03-10",
                        "2026-03-10T09:00:00Z",
                        SourceType.TRANSACTION,
                        12L);
        WalletEntry e10f =
                entry(
                        13L,
                        wallet,
                        "10.00",
                        "-20.00",
                        "2026-03-10",
                        "2026-03-10T08:00:00Z",
                        SourceType.TRANSACTION,
                        13L);

        when(walletRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(wallet));
        when(walletEntryRepository.findByWalletIdAndUserIdForHistory(
                        eq(1),
                        eq(USER_ID),
                        eq(null),
                        eq(null),
                        eq(org.springframework.data.domain.Pageable.unpaged())))
                .thenReturn(
                        List.of(e12a, e12b, e12c, e11a, e11b, e11c, e11d, e10a, e10b, e10c, e10d, e10e, e10f));

        // Page 0 with size 10: Mar 12 (3) + Mar 11 (4) = 7 rows fit; Mar 10 (6) would exceed limit →
        // goes to page 1
        // when
        WalletBalanceHistoryResponse page0 =
                service.getBalanceHistoryByWalletIdForUser(1, USER_ID, null, null, 0, 10);
        // then
        assertThat(page0.timeline()).hasSize(2);
        assertThat(page0.timeline().get(0).date()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(page0.timeline().get(0).entries()).hasSize(3);
        assertThat(page0.timeline().get(1).date()).isEqualTo(LocalDate.of(2026, 3, 11));
        assertThat(page0.timeline().get(1).entries()).hasSize(4);
        assertThat(page0.timelinePagination())
                .isEqualTo(new WalletBalanceHistoryTimelinePaginationResponse(0, 10, 13, 2, true, false));

        // Page 1: only Mar 10 (6 rows)
        WalletBalanceHistoryResponse page1 =
                service.getBalanceHistoryByWalletIdForUser(1, USER_ID, null, null, 1, 10);
        assertThat(page1.timeline()).hasSize(1);
        assertThat(page1.timeline().get(0).date()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(page1.timeline().get(0).entries()).hasSize(6);
        assertThat(page1.timelinePagination())
                .isEqualTo(new WalletBalanceHistoryTimelinePaginationResponse(1, 10, 13, 2, false, true));
    }

    @Test
    void getBalanceHistoryByWalletIdForUser_emptyHistory_returnsStableEmptyPayload() {
        // given
        Wallet wallet = wallet(1, "Main", "500.00");

        when(walletRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(wallet));
        when(walletEntryRepository.findByWalletIdAndUserIdForHistory(
                        eq(1),
                        eq(USER_ID),
                        eq(null),
                        eq(null),
                        eq(org.springframework.data.domain.Pageable.unpaged())))
                .thenReturn(List.of());

        // when
        WalletBalanceHistoryResponse response =
                service.getBalanceHistoryByWalletIdForUser(1, USER_ID, null, null, 0, 10);

        // then
        assertThat(response.currentBalance()).isEqualByComparingTo("500.00");
        assertThat(response.summary().latestBalance()).isNull();
        assertThat(response.summary().highestBalance()).isNull();
        assertThat(response.summary().lowestBalance()).isNull();
        assertThat(response.summary().entriesCount()).isEqualTo(0);
        assertThat(response.summary().netChange()).isEqualByComparingTo("0.00");
        assertThat(response.chart()).isEmpty();
        assertThat(response.timeline()).isEmpty();
        assertThat(response.timelinePagination())
                .isEqualTo(new WalletBalanceHistoryTimelinePaginationResponse(0, 10, 0, 0, false, false));
    }

    @Test
    void getBalanceHistoryByWalletIdForUser_fromAfterTo_throwsBadRequest() {
        // given
        Wallet wallet = wallet(1, "Main", "500.00");

        when(walletRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(wallet));

        // when
        // then
        assertThatThrownBy(
                        () ->
                                service.getBalanceHistoryByWalletIdForUser(
                                        1, USER_ID, LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 10), 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from (2026-03-20) must not be after to (2026-03-10)");
    }

    @Test
    void
            getBalanceHistoryByWalletIdForUser_adjustmentOnlyWallet_returnsConsistentSummaryChartAndTimeline() {
        // given
        Wallet wallet = wallet(1, "Main", "950.00");

        WalletEntry e2 =
                entry(
                        21L,
                        wallet,
                        "-50.00",
                        "950.00",
                        "2026-03-11",
                        "2026-03-11T09:00:00Z",
                        SourceType.ADJUSTMENT,
                        3002L);
        WalletEntry e1 =
                entry(
                        20L,
                        wallet,
                        "1000.00",
                        "1000.00",
                        "2026-03-10",
                        "2026-03-10T08:00:00Z",
                        SourceType.ADJUSTMENT,
                        3001L);

        when(walletRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(wallet));
        when(walletEntryRepository.findByWalletIdAndUserIdForHistory(
                        eq(1),
                        eq(USER_ID),
                        eq(null),
                        eq(null),
                        eq(org.springframework.data.domain.Pageable.unpaged())))
                .thenReturn(List.of(e2, e1));

        // when
        WalletBalanceHistoryResponse response =
                service.getBalanceHistoryByWalletIdForUser(1, USER_ID, null, null, 0, 10);

        // then
        assertThat(response.summary().latestBalance()).isEqualByComparingTo("950.00");
        assertThat(response.summary().highestBalance()).isEqualByComparingTo("1000.00");
        assertThat(response.summary().lowestBalance()).isEqualByComparingTo("950.00");
        assertThat(response.summary().entriesCount()).isEqualTo(2);
        assertThat(response.summary().netChange()).isEqualByComparingTo("950.00");

        assertThat(response.chart()).hasSize(2);
        assertThat(response.chart().get(0).date()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(response.chart().get(0).closingBalance()).isEqualByComparingTo("1000.00");
        assertThat(response.chart().get(0).netChange()).isEqualByComparingTo("1000.00");
        assertThat(response.chart().get(1).date()).isEqualTo(LocalDate.of(2026, 3, 11));
        assertThat(response.chart().get(1).closingBalance()).isEqualByComparingTo("950.00");
        assertThat(response.chart().get(1).netChange()).isEqualByComparingTo("-50.00");

        assertThat(response.timeline()).hasSize(2);
        assertThat(response.timeline().get(0).entries().get(0).sourceType())
                .isEqualTo(SourceType.ADJUSTMENT);
        assertThat(response.timeline().get(0).entries().get(0).sourceLabel()).isEqualTo("Adjustment");
        assertThat(response.timelinePagination().totalElements()).isEqualTo(2);
    }

    @Test
    void getBalanceHistoryByWalletIdForUser_walletNotOwnedByUser_throws() {
        // given
        when(walletRepository.findByIdAndUserId(99, USER_ID)).thenReturn(Optional.empty());

        // when
        // then
        assertThatThrownBy(
                        () -> service.getBalanceHistoryByWalletIdForUser(99, USER_ID, null, null, 0, 10))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Wallet not found with id: 99");
    }

    private Wallet wallet(Integer id, String name, String balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setName(name);
        wallet.setBalance(new BigDecimal(balance));
        return wallet;
    }

    private WalletEntry entry(
            Long id,
            Wallet wallet,
            String amountSigned,
            String balanceAfter,
            String entryDate,
            String createdAt,
            SourceType sourceType,
            Long sourceId) {
        WalletEntry entry = new WalletEntry();
        entry.setId(id);
        entry.setWallet(wallet);
        entry.setAmountSigned(new BigDecimal(amountSigned));
        entry.setBalanceAfter(new BigDecimal(balanceAfter));
        entry.setEntryDate(LocalDate.parse(entryDate));
        entry.setCreatedAt(Instant.parse(createdAt));
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        return entry;
    }
}
