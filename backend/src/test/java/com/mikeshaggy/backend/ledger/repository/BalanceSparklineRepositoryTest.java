package com.mikeshaggy.backend.ledger.repository;

import static com.mikeshaggy.backend.regression.September2026Fixture.CLOCK;
import static com.mikeshaggy.backend.regression.September2026Fixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.domain.WalletEntry;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.CycleMinimum;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.Point;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryChartPointResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryResponse;
import com.mikeshaggy.backend.ledger.service.BalanceSparklineService;
import com.mikeshaggy.backend.ledger.service.WalletBalanceHistoryQueryService;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Pageable;

/**
 * Stage 6.1 against the real repositories (H2): the exact query the sparkline reads
 * ({@code findByWalletIdAndUserIdForHistory(walletId, userId, null, today, unpaged)}) must not return
 * future-dated entries and must answer in ledger order, and the real services on top of it must produce the
 * as-of-today ledger balance even when {@code wallet.balance} includes a future-dated entry (resolved question Q2).
 * The pay-cycle windows are stubbed; amounts are fabricated.
 */
@DataJpaTest
class BalanceSparklineRepositoryTest {

    @Autowired
    private WalletEntryRepository walletEntryRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PayCycleService payCycleService;
    private WalletBalanceHistoryQueryService historyQueryService;
    private BalanceSparklineService service;
    private User user;
    private Wallet wallet;
    private long nextSourceId;

    @BeforeEach
    void setUp() {
        payCycleService = mock(PayCycleService.class);
        historyQueryService = new WalletBalanceHistoryQueryService(walletRepository, walletEntryRepository);
        service = new BalanceSparklineService(historyQueryService, payCycleService, CLOCK);
        user = user("spark@example.com");
        wallet = wallet(user, "Daily", "0.00");
    }

    // --- the repository query the service reads --------------------------------------------------------------------

    @Test
    void historyQueryThroughToday_excludesFutureDatedEntries_andIncludesToday() {
        entry(wallet, "100.00", "100.00", TODAY.minusDays(1));
        WalletEntry onToday = entry(wallet, "50.00", "150.00", TODAY);
        entry(wallet, "25.00", "175.00", TODAY.plusDays(1));
        entry(wallet, "25.00", "200.00", TODAY.plusDays(30));
        flushAndClear();

        List<WalletEntry> entries = walletEntryRepository.findByWalletIdAndUserIdForHistory(
                wallet.getId(), user.getId(), null, TODAY, Pageable.unpaged());

        assertThat(entries).extracting(WalletEntry::getEntryDate).containsExactly(TODAY, TODAY.minusDays(1));
        assertThat(entries.getFirst().getId()).isEqualTo(onToday.getId());
        assertThat(entries).allSatisfy(e -> assertThat(e.getEntryDate()).isBeforeOrEqualTo(TODAY));
    }

    @Test
    void historyQuery_isNewestFirstInLedgerOrder_sameDayLaterEntryFirst() {
        WalletEntry older = entry(wallet, "100.00", "100.00", TODAY.minusDays(3));
        WalletEntry sameDayFirst = entry(wallet, "-800.00", "-700.00", TODAY.minusDays(1));
        WalletEntry sameDayLast = entry(wallet, "27.47", "-672.53", TODAY.minusDays(1));
        WalletEntry newest = entry(wallet, "10.00", "-662.53", TODAY);
        flushAndClear();

        List<WalletEntry> entries = walletEntryRepository.findByWalletIdAndUserIdForHistory(
                wallet.getId(), user.getId(), null, TODAY, Pageable.unpaged());

        assertThat(entries).extracting(WalletEntry::getId).containsExactly(
                newest.getId(), sameDayLast.getId(), sameDayFirst.getId(), older.getId());
    }

    @Test
    void historyQuery_doesNotLeakAnotherUsersEntries() {
        User other = user("other-spark@example.com");
        Wallet otherWallet = wallet(other, "Theirs", "0.00");
        entry(otherWallet, "999.00", "999.00", TODAY.minusDays(1));
        entry(wallet, "1.00", "1.00", TODAY.minusDays(1));
        flushAndClear();

        assertThat(walletEntryRepository.findByWalletIdAndUserIdForHistory(
                otherWallet.getId(), user.getId(), null, TODAY, Pageable.unpaged())).isEmpty();
        assertThat(walletEntryRepository.findByWalletIdAndUserIdForHistory(
                wallet.getId(), user.getId(), null, TODAY, Pageable.unpaged())).hasSize(1);
    }

    // --- Q2: the real services end to end --------------------------------------------------------------------------

    @Test
    void todayPointIsTheAsOfTodayLedgerBalance_notWalletBalance_whenAFutureDatedEntryExists() {
        // wallet.balance = 1000.00 includes the +350.00 entry dated 6 days from today
        wallet.setBalance(new BigDecimal("1000.00"));
        entry(wallet, "500.00", "500.00", TODAY.minusDays(20));
        entry(wallet, "200.00", "700.00", TODAY.minusDays(4));
        entry(wallet, "-50.00", "650.00", TODAY);
        entry(wallet, "350.00", "1000.00", TODAY.plusDays(6));
        flushAndClear();
        when(payCycleService.current(user.getId())).thenReturn(Optional.empty());

        BalanceSparklineResponse response = service.getSparkline(wallet.getId(), user.getId(), 30);

        assertThat(response.to()).isEqualTo(TODAY);
        assertThat(response.points()).hasSize(21); // from the first entry (today - 20) through today
        assertThat(response.points().getLast()).isEqualTo(new Point(TODAY, new BigDecimal("650.00")));
        assertThat(response.points().getLast().balance()).isNotEqualByComparingTo("1000.00");
        assertThat(response.points()).extracting(Point::date).allSatisfy(date -> assertThat(date).isBeforeOrEqualTo(TODAY));
        assertThat(response.points()).extracting(Point::date).doesNotContain(TODAY.plusDays(6));
        // carried forward between entries
        assertThat(balanceOn(response, TODAY.minusDays(5))).isEqualByComparingTo("500.00");
        assertThat(balanceOn(response, TODAY.minusDays(4))).isEqualByComparingTo("700.00");
        assertThat(balanceOn(response, TODAY.minusDays(1))).isEqualByComparingTo("700.00");

        // contrast: the unfiltered /wallets/balance-history reports the stored balance and draws the future day
        WalletBalanceHistoryResponse unfiltered =
                historyQueryService.getBalanceHistoryByWalletIdForUser(wallet.getId(), user.getId());
        assertThat(unfiltered.currentBalance()).isEqualByComparingTo("1000.00");
        assertThat(unfiltered.chart()).extracting(WalletBalanceHistoryChartPointResponse::date)
                .contains(TODAY.plusDays(6));
    }

    @Test
    void onlyFutureDatedEntries_leaveNoPointsAtAll() {
        entry(wallet, "100.00", "100.00", TODAY.plusDays(1));
        flushAndClear();
        when(payCycleService.current(user.getId())).thenReturn(Optional.empty());

        assertThat(service.getSparkline(wallet.getId(), user.getId(), 30).points()).isEmpty();
    }

    @Test
    void sameDayEntries_closeOnTheLastInLedgerOrder_notTheIntradayLow_orTheHigh() {
        entry(wallet, "679.24", "679.24", TODAY.minusDays(3));
        entry(wallet, "-800.00", "-120.76", TODAY.minusDays(2)); // intraday low
        entry(wallet, "27.47", "-93.29", TODAY.minusDays(2));    // closing
        flushAndClear();
        when(payCycleService.current(user.getId())).thenReturn(Optional.empty());

        BalanceSparklineResponse response = service.getSparkline(wallet.getId(), user.getId(), 5);

        assertThat(balanceOn(response, TODAY.minusDays(2))).isEqualByComparingTo("-93.29");
        assertThat(response.points()).extracting(Point::balance).doesNotContain(new BigDecimal("-120.76"));
    }

    @Test
    void entryWithNullBalanceAfter_isSkippedWithoutFailing() {
        entry(wallet, "100.00", null, TODAY.minusDays(2));
        entry(wallet, "50.00", "150.00", TODAY.minusDays(1));
        flushAndClear();
        when(payCycleService.current(user.getId())).thenReturn(Optional.empty());

        BalanceSparklineResponse response = service.getSparkline(wallet.getId(), user.getId(), 5);

        // nothing is invented for the day whose only entry has no stored balance
        assertThat(response.points()).containsExactly(
                new Point(TODAY.minusDays(1), new BigDecimal("150.00")),
                new Point(TODAY, new BigDecimal("150.00")));
    }

    @Test
    void anotherUsersWallet_isNotFound() {
        User other = user("intruder@example.com");
        entry(wallet, "100.00", "100.00", TODAY.minusDays(1));
        flushAndClear();

        assertThatThrownBy(() -> service.getSparkline(wallet.getId(), other.getId(), 30))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // --- AC-5: sparkline minima agree with /wallets/balance-history -------------------------------------------------

    @Test
    void cycleMinima_agreeWithTheBalanceHistoryChart_andIncludeTheNegativePreSalaryLow() {
        UUID userId = user.getId();
        LocalDate c1Start = TODAY.minusDays(75);
        LocalDate c2Start = TODAY.minusDays(45);
        LocalDate c3Start = TODAY.minusDays(15);
        // cycle 1 (75..46 days ago): low 89.62 on the day before its end
        entry(wallet, "9444.81", "9444.81", c1Start);
        entry(wallet, "-9355.19", "89.62", c2Start.minusDays(1));
        // cycle 2: low -93.29 (closing; the intraday low -120.76 is not the daily closing)
        entry(wallet, "9444.81", "9534.43", c2Start);
        entry(wallet, "-9655.19", "-120.76", c3Start.minusDays(2));
        entry(wallet, "27.47", "-93.29", c3Start.minusDays(2));
        // cycle 3 is open: salary arrives
        entry(wallet, "9444.81", "9351.52", c3Start);
        flushAndClear();

        when(payCycleService.current(userId)).thenReturn(Optional.of(
                PayCycle.open(userId, wallet.getId(), c3Start, c3Start.plusDays(30))));
        when(payCycleService.history(userId, 90)).thenReturn(List.of(
                PayCycle.closed(userId, wallet.getId(), c2Start, c3Start.minusDays(1)),
                PayCycle.closed(userId, wallet.getId(), c1Start, c2Start.minusDays(1))));

        BalanceSparklineResponse response = service.getSparkline(wallet.getId(), userId, 90);

        assertThat(response.cycleMinima()).containsExactly(
                new CycleMinimum(c1Start, c2Start.minusDays(1), new BigDecimal("89.62"), c2Start.minusDays(1)),
                new CycleMinimum(c2Start, c3Start.minusDays(1), new BigDecimal("-93.29"), c3Start.minusDays(2)));
        assertThat(response.paydays()).containsExactly(c1Start, c2Start, c3Start);

        // independent derivation from the balance-history chart the /wallets page draws
        List<WalletBalanceHistoryChartPointResponse> chart = historyQueryService
                .getBalanceHistoryByWalletIdForUser(wallet.getId(), userId, null, TODAY, 0, null).chart();
        for (CycleMinimum minimum : response.cycleMinima()) {
            BigDecimal chartMin = chart.stream()
                    .filter(p -> !p.date().isBefore(minimum.cycleStart()) && !p.date().isAfter(minimum.cycleEnd()))
                    .map(WalletBalanceHistoryChartPointResponse::closingBalance)
                    .min(BigDecimal::compareTo)
                    .orElseThrow();
            assertThat(minimum.minBalance()).isEqualByComparingTo(chartMin);
        }
    }

    // --- helpers -----------------------------------------------------------------------------------------------------

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static BigDecimal balanceOn(BalanceSparklineResponse response, LocalDate date) {
        return response.points().stream().filter(p -> p.date().equals(date)).findFirst().orElseThrow().balance();
    }

    private User user(String email) {
        return entityManager.persist(User.builder()
                .email(email).username(email).passwordHash("hash").build());
    }

    private Wallet wallet(User owner, String name, String balance) {
        return entityManager.persist(Wallet.builder()
                .user(owner).name(name).balance(new BigDecimal(balance)).isFund(false).build());
    }

    private WalletEntry entry(Wallet owner, String amountSigned, String balanceAfter, LocalDate date) {
        return entityManager.persist(WalletEntry.builder()
                .wallet(owner)
                .amountSigned(new BigDecimal(amountSigned))
                .balanceAfter(balanceAfter == null ? null : new BigDecimal(balanceAfter))
                .entryDate(date)
                .sourceType(SourceType.TRANSACTION)
                .sourceId(++nextSourceId)
                .build());
    }
}
