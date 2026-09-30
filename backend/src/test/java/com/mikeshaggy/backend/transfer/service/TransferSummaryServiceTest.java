package com.mikeshaggy.backend.transfer.service;

import static com.mikeshaggy.backend.regression.September2026Fixture.CLOCK;
import static com.mikeshaggy.backend.regression.September2026Fixture.SALARY_WALLET_BALANCE;
import static com.mikeshaggy.backend.regression.September2026Fixture.SALARY_WALLET_ID;
import static com.mikeshaggy.backend.regression.September2026Fixture.SAVINGS_WALLET_BALANCE;
import static com.mikeshaggy.backend.regression.September2026Fixture.SAVINGS_WALLET_ID;
import static com.mikeshaggy.backend.regression.September2026Fixture.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.transfer.domain.Transfer;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CounterWallet;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CounterWalletFlow;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CycleSummary;
import com.mikeshaggy.backend.transfer.repository.TransferRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Stage 6.2 on the September 2026 fixture's audit date. Synthetic transfers between the salary ("daily") and
 * savings wallets reproduce the audited proportions — savings→daily 11,351.00 vs daily→savings 8,350.00 since
 * May — plus a third wallet, boundary-day, same-day and future-dated transfers. Amounts are fabricated.
 */
@ExtendWith(MockitoExtension.class)
class TransferSummaryServiceTest {

    private static final Integer CASH_WALLET_ID = 3;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PayCycleService payCycleService;

    private TransferSummaryService service;
    private Wallet daily;
    private Wallet savings;
    private Wallet cash;
    private long nextId;

    @BeforeEach
    void setUp() {
        service = new TransferSummaryService(transferRepository, walletRepository, payCycleService, CLOCK);
        daily = wallet(SALARY_WALLET_ID, "FX_SALARY_WALLET", SALARY_WALLET_BALANCE);
        savings = wallet(SAVINGS_WALLET_ID, "FX_SAVINGS_WALLET", SAVINGS_WALLET_BALANCE);
        cash = wallet(CASH_WALLET_ID, "FX_CASH_WALLET", new BigDecimal("50"));
    }

    @Test
    void salaryWallet_perCycleAndPerCounterWalletFlows() {
        // given
        givenTransfers(daily, fixtureTransfers());
        givenOpenCycleOnAuditDate(3);

        // when
        TransferSummaryResponse response = service.getSummary(SALARY_WALLET_ID, USER_ID, 3);

        // then
        assertThat(response.walletId()).isEqualTo(SALARY_WALLET_ID);

        // current cycle through today: the Sep 20 transfer is not counted yet
        assertThat(response.currentCycle()).isEqualTo(new CycleSummary(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 8), CycleState.OPEN,
                money("1000.00"), money("500.00"), money("500.00"),
                List.of(flow(savings, "1000.00", "500.00", "500.00"))));

        // closed cycles newest first
        assertThat(response.cycles()).containsExactly(
                // Aug: two same-day savings→daily transfers on the last day (Sep 8); a second counter-wallet
                new CycleSummary(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8), CycleState.CLOSED,
                        money("2851.00"), money("1890.00"), money("961.00"),
                        List.of(flow(savings, "2851.00", "1850.00", "1001.00"),
                                flow(cash, "0.00", "40.00", "-40.00"))),
                // Jul: a transfer on the first day (Jul 10) belongs here, not to Jun
                new CycleSummary(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9), CycleState.CLOSED,
                        money("2600.00"), money("2500.00"), money("100.00"),
                        List.of(flow(savings, "2500.00", "2500.00", "0.00"),
                                flow(cash, "100.00", "0.00", "100.00"))),
                // Jun: a transfer on the last day (Jul 9) belongs here, not to Jul
                new CycleSummary(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 9), CycleState.CLOSED,
                        money("3000.00"), money("2000.00"), money("1000.00"),
                        List.of(flow(savings, "3000.00", "2000.00", "1000.00"))));

        assertThat(response.counterWallets()).containsExactly(
                new CounterWallet(SAVINGS_WALLET_ID, "FX_SAVINGS_WALLET", money("460.01")),
                new CounterWallet(CASH_WALLET_ID, "FX_CASH_WALLET", money("50.00")));
    }

    @Test
    void sinceMay_savingsFundsDailyMoreThanDailyFundsSavings_eachTransferCountedOnce() {
        // given
        givenTransfers(daily, fixtureTransfers());
        givenOpenCycleOnAuditDate(5);

        // when
        TransferSummaryResponse response = service.getSummary(SALARY_WALLET_ID, USER_ID, 5);

        // then: May 8 onwards = current + 4 closed cycles; the Apr cycle has no transfers
        List<CycleSummary> sinceMay = Stream.concat(Stream.of(response.currentCycle()),
                response.cycles().stream().filter(c -> !c.start().isBefore(LocalDate.of(2026, 5, 8)))).toList();
        List<CounterWalletFlow> savingsFlows = sinceMay.stream()
                .flatMap(cycle -> cycle.counterWallets().stream())
                .filter(flow -> flow.walletId().equals(SAVINGS_WALLET_ID))
                .toList();
        assertThat(savingsFlows.stream().map(CounterWalletFlow::in).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("11351.00");
        assertThat(savingsFlows.stream().map(CounterWalletFlow::out).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("8350.00");

        // May cycle: a transfer on the cycle-start day
        assertThat(response.cycles().get(3)).isEqualTo(new CycleSummary(
                LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 9), CycleState.CLOSED,
                money("2000.00"), money("1500.00"), money("500.00"),
                List.of(flow(savings, "2000.00", "1500.00", "500.00"))));
        // zero-transfer cycle
        assertThat(response.cycles().get(4)).isEqualTo(new CycleSummary(
                LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 7), CycleState.CLOSED,
                money("0.00"), money("0.00"), money("0.00"), List.of()));
    }

    @Test
    void awaitingSalary_countsTransfersAfterTheExpectedEndThroughToday() {
        // given: on Sep 14 the Sep 9 salary has not arrived; the Aug 10 cycle is current and ended "Sep 8"
        givenTransfers(daily, fixtureTransfers());
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(PayCycle.awaitingSalary(
                USER_ID, SALARY_WALLET_ID, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 9))));
        when(payCycleService.history(USER_ID, 1)).thenReturn(List.of(
                closed(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9))));

        // when
        TransferSummaryResponse response = service.getSummary(SALARY_WALLET_ID, USER_ID, 1);

        // then: Aug + the Sep 9 / Sep 12 transfers, still not the future Sep 20 one
        CycleSummary current = response.currentCycle();
        assertThat(current.state()).isEqualTo(CycleState.AWAITING_SALARY);
        assertThat(current.end()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(current.counterWallets()).containsExactly(
                flow(savings, "3851.00", "2350.00", "1501.00"),
                flow(cash, "0.00", "40.00", "-40.00"));
        assertThat(response.cycles()).extracting(CycleSummary::start).containsExactly(LocalDate.of(2026, 7, 10));
    }

    @Test
    void nonSalaryWallet_hasCounterWalletsButNoCycles() {
        // given: the savings wallet's side of the same transfers
        givenTransfers(savings, fixtureTransfers().stream()
                .filter(t -> t.getFromWallet() == savings || t.getToWallet() == savings)
                .toList());
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.open(USER_ID, SALARY_WALLET_ID, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9))));

        // when
        TransferSummaryResponse response = service.getSummary(SAVINGS_WALLET_ID, USER_ID, 3);

        // then
        assertThat(response.currentCycle()).isNull();
        assertThat(response.cycles()).isEmpty();
        assertThat(response.counterWallets()).containsExactly(
                new CounterWallet(SALARY_WALLET_ID, "FX_SALARY_WALLET", money("5896.89")));
        verify(payCycleService, never()).history(any(), anyInt());
    }

    @Test
    void noTransfers_zeroTotalsAndNoCounterWallets() {
        // given
        givenTransfers(daily, List.of());
        givenOpenCycleOnAuditDate(3);

        // when
        TransferSummaryResponse response = service.getSummary(SALARY_WALLET_ID, USER_ID, 3);

        // then
        assertThat(response.currentCycle().net()).isEqualTo(money("0.00"));
        assertThat(response.currentCycle().counterWallets()).isEmpty();
        assertThat(response.cycles()).hasSize(3).allSatisfy(cycle -> {
            assertThat(cycle.in()).isEqualTo(money("0.00"));
            assertThat(cycle.out()).isEqualTo(money("0.00"));
            assertThat(cycle.counterWallets()).isEmpty();
        });
        assertThat(response.counterWallets()).isEmpty();
    }

    @Test
    void transferExactlyOnToday_isCountedInTheCurrentCycle() {
        // given
        givenTransfers(daily, List.of(
                transfer(savings, daily, "250.00", "2026-09-14"),
                transfer(daily, savings, "100.00", "2026-09-15")));
        givenOpenCycleOnAuditDate(1);

        // when
        TransferSummaryResponse response = service.getSummary(SALARY_WALLET_ID, USER_ID, 1);

        // then
        assertThat(response.currentCycle().counterWallets())
                .containsExactly(flow(savings, "250.00", "0.00", "250.00"));
    }

    @Test
    void awaitingSalary_transferOnTheDayAfterTheExpectedEnd_belongsToTheOpenCycle_notToAClosedOne() {
        // given: the Aug 10 cycle is expected to end Sep 8; a transfer on Sep 9 (expected payday, salary not in yet)
        givenTransfers(daily, List.of(transfer(savings, daily, "300.00", "2026-09-09")));
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(PayCycle.awaitingSalary(
                USER_ID, SALARY_WALLET_ID, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 9))));
        when(payCycleService.history(USER_ID, 1)).thenReturn(List.of(
                closed(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9))));

        // when
        TransferSummaryResponse response = service.getSummary(SALARY_WALLET_ID, USER_ID, 1);

        // then
        assertThat(response.currentCycle().in()).isEqualTo(money("300.00"));
        assertThat(response.cycles()).singleElement().extracting(CycleSummary::in).isEqualTo(money("0.00"));
    }

    @Test
    void counterWalletWithOnlyFutureDatedTransfers_hasNoFlowInAnyCycle() {
        // given
        givenTransfers(daily, List.of(transfer(daily, cash, "40.00", "2026-09-20")));
        givenOpenCycleOnAuditDate(3);

        // when
        TransferSummaryResponse response = service.getSummary(SALARY_WALLET_ID, USER_ID, 3);

        // then
        assertThat(response.currentCycle().counterWallets()).isEmpty();
        assertThat(response.currentCycle().net()).isEqualTo(money("0.00"));
        assertThat(response.cycles()).allSatisfy(cycle -> assertThat(cycle.counterWallets()).isEmpty());
    }

    @Test
    void amountsAndBalancesAreReportedAtTwoDecimals() {
        // given: scale-0 amounts and a scale-0 counter-wallet balance
        givenTransfers(daily, List.of(transfer(cash, daily, "100", "2026-09-10")));
        givenOpenCycleOnAuditDate(1);

        // when
        TransferSummaryResponse response = service.getSummary(SALARY_WALLET_ID, USER_ID, 1);

        // then
        assertThat(response.currentCycle().in()).isEqualTo(money("100.00"));
        assertThat(response.currentCycle().out()).isEqualTo(money("0.00"));
        assertThat(response.currentCycle().net()).isEqualTo(money("100.00"));
        assertThat(response.counterWallets()).containsExactly(new CounterWallet(CASH_WALLET_ID, "FX_CASH_WALLET", money("50.00")));
    }

    @Test
    void cyclesOutOfRange_isRejectedBeforeAnyRead() {
        assertThatThrownBy(() -> service.getSummary(SALARY_WALLET_ID, USER_ID, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getSummary(SALARY_WALLET_ID, USER_ID, 25))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transferRepository, walletRepository, payCycleService);
    }

    @Test
    void foreignWallet_isNotFound() {
        // given
        when(walletRepository.findByIdAndUserId(SALARY_WALLET_ID, USER_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.getSummary(SALARY_WALLET_ID, USER_ID, 3))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(transferRepository, payCycleService);
    }

    // --- fixture -------------------------------------------------------------------------------------------------

    private List<Transfer> fixtureTransfers() {
        return List.of(
                // May 8 – Jun 9
                transfer(daily, savings, "1500.00", "2026-05-08"),
                transfer(savings, daily, "2000.00", "2026-05-20"),
                // Jun 10 – Jul 9
                transfer(daily, savings, "2000.00", "2026-06-10"),
                transfer(savings, daily, "3000.00", "2026-07-09"),
                // Jul 10 – Aug 9
                transfer(savings, daily, "2500.00", "2026-07-10"),
                transfer(daily, savings, "2500.00", "2026-07-25"),
                transfer(cash, daily, "100.00", "2026-07-25"),
                // Aug 10 – Sep 8
                transfer(daily, savings, "1850.00", "2026-08-10"),
                transfer(daily, cash, "40.00", "2026-08-12"),
                transfer(savings, daily, "1851.00", "2026-09-08"),
                transfer(savings, daily, "1000.00", "2026-09-08"),
                // Sep 9 – (today Sep 14)
                transfer(daily, savings, "500.00", "2026-09-09"),
                transfer(savings, daily, "1000.00", "2026-09-12"),
                transfer(savings, daily, "999.00", "2026-09-20")); // future-dated
    }

    private void givenTransfers(Wallet wallet, List<Transfer> transfers) {
        when(walletRepository.findByIdAndUserId(wallet.getId(), USER_ID)).thenReturn(Optional.of(wallet));
        when(transferRepository.findByWalletIdAndUserId(wallet.getId(), USER_ID)).thenReturn(transfers);
    }

    private void givenOpenCycleOnAuditDate(int cycles) {
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.open(USER_ID, SALARY_WALLET_ID, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9))));
        List<PayCycle> closed = List.of(
                closed(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8)),
                closed(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9)),
                closed(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 9)),
                closed(LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 9)),
                closed(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 7)));
        when(payCycleService.history(USER_ID, cycles)).thenReturn(closed.subList(0, cycles));
    }

    private static PayCycle closed(LocalDate start, LocalDate end) {
        return PayCycle.closed(USER_ID, SALARY_WALLET_ID, start, end);
    }

    private Transfer transfer(Wallet from, Wallet to, String amount, String date) {
        return Transfer.builder()
                .id(++nextId)
                .fromWallet(from)
                .toWallet(to)
                .amount(new BigDecimal(amount))
                .transferDate(LocalDate.parse(date))
                .build();
    }

    private static CounterWalletFlow flow(Wallet counter, String in, String out, String net) {
        return new CounterWalletFlow(counter.getId(), counter.getName(), money(in), money(out), money(net));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static Wallet wallet(Integer id, String name, BigDecimal balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setName(name);
        wallet.setBalance(balance);
        return wallet;
    }
}
