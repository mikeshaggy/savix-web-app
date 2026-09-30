package com.mikeshaggy.backend.ledger.service;

import static com.mikeshaggy.backend.regression.September2026Fixture.CLOCK;
import static com.mikeshaggy.backend.regression.September2026Fixture.SALARY_WALLET_BALANCE;
import static com.mikeshaggy.backend.regression.September2026Fixture.SALARY_WALLET_ID;
import static com.mikeshaggy.backend.regression.September2026Fixture.SAVINGS_WALLET_ID;
import static com.mikeshaggy.backend.regression.September2026Fixture.TODAY;
import static com.mikeshaggy.backend.regression.September2026Fixture.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.domain.WalletEntry;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.CycleMinimum;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.Point;
import com.mikeshaggy.backend.ledger.repository.WalletEntryRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * Stage 6.1. The real {@link WalletBalanceHistoryQueryService} runs over a synthetic salary-wallet ledger on the
 * September 2026 fixture's audit date (the {@code /wallets} daily closing is the sparkline's source): three
 * salary-to-salary sawteeth, a pre-payday minimum of −93.29 and today's closing equal to the fixture's wallet
 * balance. Amounts and their placement are fabricated: the −93.29 of the real local data sits in the
 * Jul 10 – Aug 9 cycle, here it is deliberately placed in the synthetic Aug 10 – Sep 8 cycle.
 */
@ExtendWith(MockitoExtension.class)
class BalanceSparklineServiceTest {

    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 6, 17); // TODAY − 89

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletEntryRepository walletEntryRepository;

    @Mock
    private PayCycleService payCycleService;

    private BalanceSparklineService service;
    private Wallet salaryWallet;

    @BeforeEach
    void setUp() {
        WalletBalanceHistoryQueryService historyQueryService =
                new WalletBalanceHistoryQueryService(walletRepository, walletEntryRepository);
        service = new BalanceSparklineService(historyQueryService, payCycleService, CLOCK);
        salaryWallet = wallet(SALARY_WALLET_ID, "FX_SALARY_WALLET", SALARY_WALLET_BALANCE);
    }

    @Test
    void salaryWallet_dailyClosingPaydaysAndMinimaOfTheLastThreeClosedCycles() {
        // given
        givenLedger(salaryWallet, fixtureLedger(salaryWallet));
        givenOpenCycleOnAuditDate();

        // when
        BalanceSparklineResponse response = service.getSparkline(SALARY_WALLET_ID, USER_ID, 90);

        // then
        assertThat(response.walletId()).isEqualTo(SALARY_WALLET_ID);
        assertThat(response.from()).isEqualTo(WINDOW_FROM);
        assertThat(response.to()).isEqualTo(TODAY);

        List<Point> points = response.points();
        assertThat(points).hasSize(90);
        assertThat(points).extracting(Point::date).startsWith(WINDOW_FROM).endsWith(TODAY).doesNotHaveDuplicates();
        // the balance carried into the window from the Jun 12 entry
        assertThat(balanceOn(points, WINDOW_FROM)).isEqualByComparingTo("889.62");
        // sawtooth: low before each payday, high on it
        assertThat(balanceOn(points, LocalDate.of(2026, 7, 9))).isEqualByComparingTo("89.62");
        assertThat(balanceOn(points, LocalDate.of(2026, 7, 10))).isEqualByComparingTo("9534.43");
        assertThat(balanceOn(points, LocalDate.of(2026, 9, 8))).isEqualByComparingTo("-93.29");
        assertThat(balanceOn(points, LocalDate.of(2026, 9, 9))).isEqualByComparingTo("9351.52");
        // same-day entries: the closing (last in ledger order) — not the intraday low of −120.76
        assertThat(balanceOn(points, LocalDate.of(2026, 9, 5))).isEqualByComparingTo("-93.29");
        // today's point equals the stored balance only because this ledger has no future-dated entries
        assertThat(points.getLast().balance()).isEqualByComparingTo(SALARY_WALLET_BALANCE);

        // actual cycle starts inside the window only; the expected Oct 9 payday is never a marker
        assertThat(response.paydays()).containsExactly(
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 9));

        // oldest first, over the whole cycle (the Jun cycle starts before the window)
        assertThat(response.cycleMinima()).containsExactly(
                new CycleMinimum(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 9),
                        new BigDecimal("89.62"), LocalDate.of(2026, 7, 8)),
                new CycleMinimum(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9),
                        new BigDecimal("234.43"), LocalDate.of(2026, 8, 5)),
                new CycleMinimum(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8),
                        new BigDecimal("-93.29"), LocalDate.of(2026, 9, 5)));
    }

    @Test
    void ledgerIsRequestedThroughToday() { // the query's exclusion itself is proven in BalanceSparklineRepositoryTest
        // given
        givenLedger(salaryWallet, fixtureLedger(salaryWallet));
        givenOpenCycleOnAuditDate();

        // when
        service.getSparkline(SALARY_WALLET_ID, USER_ID, 90);

        // then
        verify(walletEntryRepository).findByWalletIdAndUserIdForHistory(
                SALARY_WALLET_ID, USER_ID, null, TODAY, Pageable.unpaged());
    }

    @Test
    void awaitingSalary_theOpenCycleIsNotAClosedMinimum() {
        // given: on Sep 14 the Sep 9 salary has not arrived yet; the cycle that started Aug 10 is still current
        List<WalletEntry> entries = fixtureLedger(salaryWallet).stream()
                .filter(entry -> entry.getEntryDate().isBefore(LocalDate.of(2026, 9, 9)))
                .toList();
        givenLedger(salaryWallet, entries);
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(PayCycle.awaitingSalary(
                USER_ID, SALARY_WALLET_ID, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 9))));
        when(payCycleService.history(USER_ID, 90)).thenReturn(List.of(
                closed(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9)),
                closed(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 9)),
                closed(LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 9))));

        // when
        BalanceSparklineResponse response = service.getSparkline(SALARY_WALLET_ID, USER_ID, 90);

        // then
        assertThat(response.paydays()).containsExactly(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10));
        assertThat(response.cycleMinima()).extracting(CycleMinimum::cycleStart).containsExactly(
                LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 10));
        assertThat(response.points().getLast()).isEqualTo(new Point(TODAY, new BigDecimal("-93.29")));
    }

    @Test
    void nonSalaryWallet_hasBalancePointsButNoCycleSemantics() {
        // given
        Wallet savings = wallet(SAVINGS_WALLET_ID, "FX_SAVINGS_WALLET", new BigDecimal("460.01"));
        givenLedger(savings, List.of(
                entry(1L, savings, "500.00", "500.00", "2026-08-01"),
                entry(2L, savings, "-39.99", "460.01", "2026-09-02")));
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.open(USER_ID, SALARY_WALLET_ID, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9))));

        // when
        BalanceSparklineResponse response = service.getSparkline(SAVINGS_WALLET_ID, USER_ID, 90);

        // then: sparse history starts at the first entry, nothing before it is invented
        assertThat(response.points()).first().isEqualTo(new Point(LocalDate.of(2026, 8, 1), new BigDecimal("500.00")));
        assertThat(response.points()).hasSize(45);
        assertThat(response.points().getLast().balance()).isEqualByComparingTo("460.01");
        assertThat(response.paydays()).isEmpty();
        assertThat(response.cycleMinima()).isEmpty();
        verify(payCycleService, never()).history(any(), anyInt());
    }

    @Test
    void noPayCycle_hasBalancePointsOnly() {
        // given
        givenLedger(salaryWallet, fixtureLedger(salaryWallet));
        when(payCycleService.current(USER_ID)).thenReturn(Optional.empty());

        // when
        BalanceSparklineResponse response = service.getSparkline(SALARY_WALLET_ID, USER_ID, 90);

        // then
        assertThat(response.points()).hasSize(90);
        assertThat(response.paydays()).isEmpty();
        assertThat(response.cycleMinima()).isEmpty();
    }

    @Test
    void emptyLedger_hasNoPointsAndNoMinima() {
        // given
        givenLedger(salaryWallet, List.of());
        givenOpenCycleOnAuditDate();

        // when
        BalanceSparklineResponse response = service.getSparkline(SALARY_WALLET_ID, USER_ID, 90);

        // then
        assertThat(response.points()).isEmpty();
        assertThat(response.cycleMinima()).isEmpty();
    }

    @Test
    void oneDay_isTodaysClosingOnly() {
        // given
        givenLedger(salaryWallet, fixtureLedger(salaryWallet));
        givenOpenCycleOnAuditDate(1);

        // when
        BalanceSparklineResponse response = service.getSparkline(SALARY_WALLET_ID, USER_ID, 1);

        // then: the minima still cover the last three closed cycles
        assertThat(response.from()).isEqualTo(TODAY);
        assertThat(response.points()).containsExactly(new Point(TODAY, new BigDecimal("5896.89")));
        assertThat(response.paydays()).isEmpty();
        assertThat(response.cycleMinima()).hasSize(3);
    }

    @Test
    void cycleMinimum_aTiedLowIsReportedOnItsEarliestDay() {
        // given: −50.00 is reached on Sep 1 and stays the closing through Sep 3; 10.00 on the last day
        givenLedger(salaryWallet, List.of(
                entry(1L, salaryWallet, "500.00", "500.00", "2026-08-01"),
                entry(2L, salaryWallet, "-550.00", "-50.00", "2026-09-01"),
                entry(3L, salaryWallet, "60.00", "10.00", "2026-09-03")));
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.open(USER_ID, SALARY_WALLET_ID, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9))));
        when(payCycleService.history(USER_ID, 90)).thenReturn(List.of(
                closed(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8))));

        // when
        BalanceSparklineResponse response = service.getSparkline(SALARY_WALLET_ID, USER_ID, 90);

        // then
        assertThat(response.cycleMinima()).containsExactly(new CycleMinimum(
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8), new BigDecimal("-50.00"), LocalDate.of(2026, 9, 1)));
    }

    @Test
    void paydays_windowBoundsAreInclusive_andAPaydayJustBeforeTheWindowIsExcluded() {
        // given: days = 30 → window Aug 16 – Sep 14; cycle starts on Aug 15 (before), Aug 16 (first day), Sep 14 (today)
        givenLedger(salaryWallet, fixtureLedger(salaryWallet));
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.open(USER_ID, SALARY_WALLET_ID, TODAY, LocalDate.of(2026, 10, 14))));
        when(payCycleService.history(USER_ID, 30)).thenReturn(List.of(
                closed(LocalDate.of(2026, 8, 16), TODAY.minusDays(1)),
                closed(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15))));

        // when
        BalanceSparklineResponse response = service.getSparkline(SALARY_WALLET_ID, USER_ID, 30);

        // then
        assertThat(response.from()).isEqualTo(LocalDate.of(2026, 8, 16));
        assertThat(response.paydays()).containsExactly(LocalDate.of(2026, 8, 16), TODAY);
    }

    @Test
    void daysBounds_oneAndMaximumAreAccepted() {
        // given
        givenLedger(salaryWallet, fixtureLedger(salaryWallet));
        when(payCycleService.current(USER_ID)).thenReturn(Optional.empty());

        // when
        BalanceSparklineResponse max = service.getSparkline(SALARY_WALLET_ID, USER_ID, 366);
        BalanceSparklineResponse one = service.getSparkline(SALARY_WALLET_ID, USER_ID, 1);

        // then: a 366-day window reaches back to the first entry (May 1) and stops there — nothing is invented
        assertThat(max.from()).isEqualTo(TODAY.minusDays(365));
        assertThat(max.points()).first().isEqualTo(new Point(LocalDate.of(2026, 5, 1), new BigDecimal("500.00")));
        assertThat(max.points()).hasSize((int) (TODAY.toEpochDay() - LocalDate.of(2026, 5, 1).toEpochDay() + 1));
        assertThat(one.points()).hasSize(1);
    }

    @Test
    void chartPointWithoutClosingBalance_isNotInventedAndDoesNotFail() {
        // given: the only entry has no stored balance_after
        givenLedger(salaryWallet, List.of(entryWithoutBalance(1L, salaryWallet, "100.00", "2026-09-10")));
        when(payCycleService.current(USER_ID)).thenReturn(Optional.empty());

        // when
        BalanceSparklineResponse response = service.getSparkline(SALARY_WALLET_ID, USER_ID, 30);

        // then
        assertThat(response.points()).isEmpty();
    }

    @Test
    void daysOutOfRange_isRejectedBeforeAnyRead() {
        assertThatThrownBy(() -> service.getSparkline(SALARY_WALLET_ID, USER_ID, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getSparkline(SALARY_WALLET_ID, USER_ID, 367))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(walletRepository, walletEntryRepository, payCycleService);
    }

    @Test
    void foreignWallet_isNotFound() {
        // given
        when(walletRepository.findByIdAndUserId(SALARY_WALLET_ID, USER_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.getSparkline(SALARY_WALLET_ID, USER_ID, 90))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(payCycleService);
    }

    // --- fixture -------------------------------------------------------------------------------------------------

    /** Synthetic salary-wallet ledger, ledger order; each balance_after is the running balance. */
    private static List<WalletEntry> fixtureLedger(Wallet wallet) {
        String[][] rows = {
                {"2026-05-01", "500.00", "500.00"},     // opening adjustment
                {"2026-05-08", "9444.81", "9944.81"},   // salary
                {"2026-06-05", "-9500.00", "444.81"},
                {"2026-06-10", "9444.81", "9889.62"},   // salary
                {"2026-06-12", "-9000.00", "889.62"},
                {"2026-07-08", "-800.00", "89.62"},     // Jun cycle low
                {"2026-07-10", "9444.81", "9534.43"},   // salary
                {"2026-07-20", "-9000.00", "534.43"},   // transfer out to savings
                {"2026-08-05", "-300.00", "234.43"},    // Jul cycle low
                {"2026-08-10", "9444.81", "9679.24"},   // salary
                {"2026-08-20", "-9000.00", "679.24"},
                {"2026-09-05", "-800.00", "-120.76"},   // intraday low
                {"2026-09-05", "27.47", "-93.29"},      // same-day transfer in from savings → synthetic Aug 10 – Sep 8 cycle low (closing)
                {"2026-09-09", "9444.81", "9351.52"},   // salary
                {"2026-09-14", "-3454.63", "5896.89"},  // today == SALARY_WALLET_BALANCE
        };
        List<WalletEntry> entries = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            entries.add(entry((long) i + 1, wallet, rows[i][1], rows[i][2], rows[i][0]));
        }
        return entries;
    }

    private void givenLedger(Wallet wallet, List<WalletEntry> ledgerOrder) {
        when(walletRepository.findByIdAndUserId(wallet.getId(), USER_ID)).thenReturn(Optional.of(wallet));
        // the repository answers newest first
        when(walletEntryRepository.findByWalletIdAndUserIdForHistory(
                wallet.getId(), USER_ID, null, TODAY, Pageable.unpaged()))
                .thenReturn(ledgerOrder.reversed());
    }

    private void givenOpenCycleOnAuditDate() {
        givenOpenCycleOnAuditDate(90);
    }

    private void givenOpenCycleOnAuditDate(int historyRequested) {
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.open(USER_ID, SALARY_WALLET_ID, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9))));
        List<PayCycle> closed = List.of(
                closed(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8)),
                closed(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9)),
                closed(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 9)),
                closed(LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 9)));
        when(payCycleService.history(USER_ID, Math.max(historyRequested, 3))).thenReturn(closed);
    }

    private static PayCycle closed(LocalDate start, LocalDate end) {
        return PayCycle.closed(USER_ID, SALARY_WALLET_ID, start, end);
    }

    private static BigDecimal balanceOn(List<Point> points, LocalDate date) {
        return points.stream().filter(point -> point.date().equals(date)).findFirst().orElseThrow().balance();
    }

    private static Wallet wallet(Integer id, String name, BigDecimal balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setName(name);
        wallet.setBalance(balance);
        return wallet;
    }

    private static WalletEntry entryWithoutBalance(Long id, Wallet wallet, String amountSigned, String date) {
        WalletEntry entry = entry(id, wallet, amountSigned, "0.00", date);
        entry.setBalanceAfter(null);
        return entry;
    }

    private static WalletEntry entry(Long id, Wallet wallet, String amountSigned, String balanceAfter, String date) {
        WalletEntry entry = new WalletEntry();
        entry.setId(id);
        entry.setWallet(wallet);
        entry.setAmountSigned(new BigDecimal(amountSigned));
        entry.setBalanceAfter(new BigDecimal(balanceAfter));
        entry.setEntryDate(LocalDate.parse(date));
        entry.setCreatedAt(LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(id));
        entry.setSourceType(SourceType.TRANSACTION);
        entry.setSourceId(id);
        return entry;
    }
}
