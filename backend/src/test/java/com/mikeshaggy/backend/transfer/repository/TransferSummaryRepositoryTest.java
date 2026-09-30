package com.mikeshaggy.backend.transfer.repository;

import static com.mikeshaggy.backend.regression.September2026Fixture.CLOCK;
import static com.mikeshaggy.backend.regression.September2026Fixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.transfer.domain.Transfer;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CounterWalletFlow;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CycleSummary;
import com.mikeshaggy.backend.transfer.service.TransferSummaryService;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Stage 6.2 against the real repositories (H2): {@code TransferRepository.findByWalletIdAndUserId}, the query the
 * summary reads, must return both directions of this wallet's transfers, never a fund movement and never another
 * user's rows; the real {@link TransferSummaryService} on top of it must count each row once, exclude fund
 * transfers and future-dated transfers. The pay-cycle windows are stubbed; amounts are fabricated.
 */
@DataJpaTest
class TransferSummaryRepositoryTest {

    private static final LocalDate CYCLE_START = TODAY.minusDays(5);

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PayCycleService payCycleService;
    private TransferSummaryService service;
    private User user;
    private Wallet daily;
    private Wallet savings;
    private Wallet fund;

    @BeforeEach
    void setUp() {
        payCycleService = mock(PayCycleService.class);
        service = new TransferSummaryService(transferRepository, walletRepository, payCycleService, CLOCK);
        user = user("transfers-summary@example.com");
        daily = wallet(user, "Daily", "100.00", false);
        savings = wallet(user, "Savings", "2500.50", false);
        fund = wallet(user, "Vacation", "300.00", true);
    }

    // --- the repository query the service reads --------------------------------------------------------------------

    @Test
    void query_returnsBothDirections_excludesFundMovementsOnEitherSide_andOtherUsersRows() {
        Transfer out = transfer(daily, savings, "10.00", TODAY.minusDays(1));
        Transfer in = transfer(savings, daily, "20.00", TODAY.minusDays(2));
        transfer(daily, fund, "300.00", TODAY.minusDays(1));          // deposit into a fund
        transfer(fund, daily, "50.00", TODAY.minusDays(2));           // withdrawal from a fund
        transfer(savings, fund, "70.00", TODAY.minusDays(3));         // not this wallet at all
        User other = user("other-transfers@example.com");
        Wallet otherA = wallet(other, "A", "0.00", false);
        Wallet otherB = wallet(other, "B", "0.00", false);
        transfer(otherA, otherB, "999.00", TODAY.minusDays(1));
        flushAndClear();

        List<Transfer> rows = transferRepository.findByWalletIdAndUserId(daily.getId(), user.getId());

        assertThat(rows).extracting(Transfer::getId).containsExactlyInAnyOrder(out.getId(), in.getId());
    }

    @Test
    void query_withAnotherUsersWalletId_returnsNothing() {
        User other = user("other2-transfers@example.com");
        Wallet otherA = wallet(other, "A", "0.00", false);
        Wallet otherB = wallet(other, "B", "0.00", false);
        transfer(otherA, otherB, "999.00", TODAY.minusDays(1));
        flushAndClear();

        assertThat(transferRepository.findByWalletIdAndUserId(otherA.getId(), user.getId())).isEmpty();
    }

    // --- the real service on top ---------------------------------------------------------------------------------------

    @Test
    void service_countsEachRowOnceFromThisWalletsSide_excludingFundsAndFutureTransfers() {
        transfer(savings, daily, "1000.00", CYCLE_START);             // boundary: first day of the open cycle
        transfer(daily, savings, "400.25", TODAY);                    // boundary: today counts
        transfer(savings, daily, "77.00", TODAY.plusDays(1));         // future: never counted
        transfer(daily, fund, "300.00", TODAY.minusDays(1));          // fund movement: never counted
        transfer(fund, daily, "50.00", TODAY.minusDays(2));           // fund movement: never counted
        transfer(savings, daily, "5.00", CYCLE_START.minusDays(1));   // previous cycle
        flushAndClear();
        givenOpenCycleStarting(CYCLE_START, List.of(
                PayCycle.closed(user.getId(), daily.getId(), CYCLE_START.minusDays(30), CYCLE_START.minusDays(1))));

        TransferSummaryResponse response = service.getSummary(daily.getId(), user.getId(), 1);

        assertThat(response.currentCycle()).extracting(CycleSummary::in, CycleSummary::out, CycleSummary::net)
                .containsExactly(money("1000.00"), money("400.25"), money("599.75"));
        assertThat(response.currentCycle().counterWallets()).containsExactly(new CounterWalletFlow(
                savings.getId(), "Savings", money("1000.00"), money("400.25"), money("599.75")));
        assertThat(response.cycles()).singleElement().satisfies(cycle -> {
            assertThat(cycle.in()).isEqualTo(money("5.00"));
            assertThat(cycle.out()).isEqualTo(money("0.00"));
            assertThat(cycle.net()).isEqualTo(money("5.00"));
        });
        // the fund is not a counter-wallet; the savings wallet reports its stored balance at scale 2
        assertThat(response.counterWallets()).singleElement().satisfies(counter -> {
            assertThat(counter.walletId()).isEqualTo(savings.getId());
            assertThat(counter.balance()).isEqualTo(money("2500.50"));
        });
    }

    @Test
    void service_aCounterWalletThatOnlyHasFutureTransfers_contributesNoFlow() {
        Wallet cash = wallet(user, "Cash", "10.00", false);
        transfer(daily, cash, "40.00", TODAY.plusDays(3));
        flushAndClear();
        givenOpenCycleStarting(CYCLE_START, List.of());

        TransferSummaryResponse response = service.getSummary(daily.getId(), user.getId(), 3);

        assertThat(response.currentCycle().counterWallets()).isEmpty();
        assertThat(response.currentCycle().in()).isEqualTo(money("0.00"));
        assertThat(response.currentCycle().out()).isEqualTo(money("0.00"));
        assertThat(response.currentCycle().net()).isEqualTo(money("0.00"));
    }

    @Test
    void service_nonSalaryWallet_hasNoCycleSemantics_butExcludesFundCounterparts() {
        transfer(daily, savings, "10.00", TODAY.minusDays(1));
        transfer(savings, fund, "70.00", TODAY.minusDays(1));
        flushAndClear();
        givenOpenCycleStarting(CYCLE_START, List.of());

        TransferSummaryResponse response = service.getSummary(savings.getId(), user.getId(), 3);

        assertThat(response.currentCycle()).isNull();
        assertThat(response.cycles()).isEmpty();
        assertThat(response.counterWallets()).singleElement()
                .satisfies(counter -> assertThat(counter.walletId()).isEqualTo(daily.getId()));
    }

    @Test
    void service_anotherUsersWallet_isNotFound() {
        User other = user("intruder-transfers@example.com");
        flushAndClear();

        assertThatThrownBy(() -> service.getSummary(daily.getId(), other.getId(), 3))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // --- helpers -----------------------------------------------------------------------------------------------------

    private void givenOpenCycleStarting(LocalDate start, List<PayCycle> closed) {
        when(payCycleService.current(user.getId())).thenReturn(Optional.of(
                PayCycle.open(user.getId(), daily.getId(), start, start.plusDays(30))));
        when(payCycleService.history(org.mockito.ArgumentMatchers.eq(user.getId()), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(closed);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private User user(String email) {
        return entityManager.persist(User.builder()
                .email(email).username(email).passwordHash("hash").build());
    }

    private Wallet wallet(User owner, String name, String balance, boolean isFund) {
        return entityManager.persist(Wallet.builder()
                .user(owner).name(name).balance(new BigDecimal(balance)).isFund(isFund).build());
    }

    private Transfer transfer(Wallet from, Wallet to, String amount, LocalDate date) {
        return entityManager.persist(Transfer.builder()
                .fromWallet(from).toWallet(to).amount(new BigDecimal(amount)).transferDate(date).build());
    }
}
