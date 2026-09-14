package com.mikeshaggy.backend.common.paycycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.repository.CategoryRepository;
import com.mikeshaggy.backend.regression.September2026Fixture;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.domain.UserPaydayRule;
import com.mikeshaggy.backend.user.repository.UserPaydayRuleRepository;
import com.mikeshaggy.backend.user.repository.UserRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PayCycleServiceTest {

    private static final UUID USER_ID = September2026Fixture.USER_ID;
    private static final Integer SALARY_WALLET_ID = September2026Fixture.SALARY_WALLET_ID;
    private static final Integer SAVINGS_WALLET_ID = September2026Fixture.SAVINGS_WALLET_ID;
    private static final Integer ANCHOR_CATEGORY_ID = September2026Fixture.ANCHOR_CATEGORY_ID;
    private static final LocalDate TODAY = September2026Fixture.TODAY; // 2026-09-14
    private static final LocalDate EXPECTED_PAYDAY = LocalDate.of(2026, 10, 9);
    private static final PayCycleProperties PROPERTIES = new PayCycleProperties(20);

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPaydayRuleRepository paydayRuleRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TransactionRepository transactionRepository;

    private PayCycleService serviceAt(LocalDate today) {
        Clock clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        return new PayCycleService(userRepository, paydayRuleRepository, categoryRepository, transactionRepository,
                new ExpectedPaydayResolver(), PROPERTIES, clock);
    }

    // --- stubbing helpers -------------------------------------------------------------------------------------

    private void givenAnchorCategory() {
        when(categoryRepository.findByUserIdAndIsCycleAnchorTrue(USER_ID))
                .thenReturn(Optional.of(Category.builder().id(ANCHOR_CATEGORY_ID).build()));
    }

    private void givenConfiguredSalaryWallet(Integer walletId) {
        User user = User.builder().id(USER_ID)
                .salaryWallet(walletId == null ? null : Wallet.builder().id(walletId).build())
                .build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void givenNoConfiguredRule() {
        when(paydayRuleRepository.findById(USER_ID)).thenReturn(Optional.empty());
    }

    /** The salary wallet answers with the given anchors (any order), honouring the {@code upTo} argument. */
    private void givenSalaryWalletAnchors(List<LocalDate> anchors) {
        when(transactionRepository.findAnchorDates(eq(SALARY_WALLET_ID), eq(USER_ID), eq(ANCHOR_CATEGORY_ID),
                any(LocalDate.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    LocalDate upTo = invocation.getArgument(3);
                    List<LocalDate> desc = new ArrayList<>(anchors.stream()
                            .filter(date -> !date.isAfter(upTo)).distinct().sorted().toList());
                    Collections.reverse(desc);
                    return desc;
                });
    }

    private void givenFixtureUser() {
        givenAnchorCategory();
        givenConfiguredSalaryWallet(SALARY_WALLET_ID);
        givenSalaryWalletAnchors(September2026Fixture.ANCHOR_DATES);
    }

    private static List<LocalDate> plus(List<LocalDate> anchors, LocalDate... extra) {
        List<LocalDate> all = new ArrayList<>(anchors);
        Collections.addAll(all, extra);
        return all;
    }

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    // --- September fixture -------------------------------------------------------------------------------------

    @Nested
    @DisplayName("September 2026 fixture, today = 2026-09-14")
    class Fixture {

        @Test
        void currentCycleIsOpenFromSep9ToOct8WithExpectedPaydayOct9() {
            givenFixtureUser();
            givenNoConfiguredRule();

            Optional<PayCycle> current = serviceAt(TODAY).current(USER_ID);

            assertThat(current).hasValueSatisfying(cycle -> {
                assertThat(cycle.userId()).isEqualTo(USER_ID);
                assertThat(cycle.salaryWalletId()).isEqualTo(SALARY_WALLET_ID);
                assertThat(cycle.start()).isEqualTo(d(2026, 9, 9));
                assertThat(cycle.end()).isEqualTo(d(2026, 10, 8));
                assertThat(cycle.expectedNextAnchor()).isEqualTo(EXPECTED_PAYDAY);
                assertThat(cycle.state()).isEqualTo(CycleState.OPEN);
                assertThat(cycle.lengthDays()).isEqualTo(30);
                assertThat(cycle.dayIndex(TODAY)).isEqualTo(6);
            });
        }

        @Test
        void lastCycleIsAug10ToSep8Closed() {
            givenFixtureUser();

            Optional<PayCycle> last = serviceAt(TODAY).last(USER_ID);

            assertThat(last).hasValueSatisfying(cycle -> {
                assertThat(cycle.start()).isEqualTo(d(2026, 8, 10));
                assertThat(cycle.end()).isEqualTo(d(2026, 9, 8));
                assertThat(cycle.state()).isEqualTo(CycleState.CLOSED);
                assertThat(cycle.expectedNextAnchor()).isNull();
            });
        }

        @Test
        void historyOfSixReturnsTheSixNewestClosedCyclesWithActualBoundaries() {
            givenFixtureUser();

            List<PayCycle> history = serviceAt(TODAY).history(USER_ID, 6);

            assertThat(history).extracting(PayCycle::start, PayCycle::end).containsExactly(
                    org.assertj.core.groups.Tuple.tuple(d(2026, 8, 10), d(2026, 9, 8)),
                    org.assertj.core.groups.Tuple.tuple(d(2026, 7, 10), d(2026, 8, 9)),
                    org.assertj.core.groups.Tuple.tuple(d(2026, 6, 10), d(2026, 7, 9)),
                    org.assertj.core.groups.Tuple.tuple(d(2026, 5, 8), d(2026, 6, 9)),
                    org.assertj.core.groups.Tuple.tuple(d(2026, 4, 10), d(2026, 5, 7)),
                    org.assertj.core.groups.Tuple.tuple(d(2026, 3, 10), d(2026, 4, 9)));
            assertThat(history).allSatisfy(cycle -> {
                assertThat(cycle.state()).isEqualTo(CycleState.CLOSED);
                assertThat(cycle.lengthDays()).isGreaterThanOrEqualTo(20);
                assertThat(cycle.salaryWalletId()).isEqualTo(SALARY_WALLET_ID);
            });
        }

        @Test
        void fullHistoryHasTenClosedCyclesBackToNov10AndNeverMoreThanAvailable() {
            givenFixtureUser();

            List<PayCycle> history = serviceAt(TODAY).history(USER_ID, 50);

            assertThat(history).hasSize(10);
            assertThat(history.getLast().start()).isEqualTo(d(2025, 11, 10));
            assertThat(history.getLast().end()).isEqualTo(d(2025, 12, 9));
            // consecutive cycles are gap-free: each start is the day after the older cycle's end
            for (int i = 0; i < history.size() - 1; i++) {
                assertThat(history.get(i).start()).isEqualTo(history.get(i + 1).end().plusDays(1));
            }
        }

        @Test
        void hypotheticalSecondSalaryOnSep12DoesNotStartANewCycle() {
            givenAnchorCategory();
            givenConfiguredSalaryWallet(SALARY_WALLET_ID);
            givenSalaryWalletAnchors(plus(September2026Fixture.ANCHOR_DATES, d(2026, 9, 12)));
            givenNoConfiguredRule();
            PayCycleService service = serviceAt(TODAY);

            assertThat(service.anchorDates(USER_ID, TODAY, 3))
                    .containsExactly(d(2026, 9, 9), d(2026, 8, 10), d(2026, 7, 10));
            assertThat(service.current(USER_ID)).hasValueSatisfying(cycle -> {
                assertThat(cycle.start()).isEqualTo(d(2026, 9, 9));
                assertThat(cycle.end()).isEqualTo(d(2026, 10, 8));
                assertThat(cycle.expectedNextAnchor()).isEqualTo(EXPECTED_PAYDAY);
            });
            assertThat(service.last(USER_ID)).map(PayCycle::start).hasValue(d(2026, 8, 10));
        }

        @Test
        void anchorDatedAfterTodayIsIgnoredByCurrent() {
            givenAnchorCategory();
            givenConfiguredSalaryWallet(SALARY_WALLET_ID);
            givenSalaryWalletAnchors(plus(September2026Fixture.ANCHOR_DATES, EXPECTED_PAYDAY)); // pre-booked Oct 9
            givenNoConfiguredRule();

            Optional<PayCycle> current = serviceAt(TODAY).current(USER_ID);

            assertThat(current).map(PayCycle::start).hasValue(d(2026, 9, 9));
            verify(transactionRepository).findAnchorDates(eq(SALARY_WALLET_ID), eq(USER_ID), eq(ANCHOR_CATEGORY_ID),
                    eq(TODAY), any(Pageable.class));
        }

        @Test
        void historyAsOfExcludesTheCycleOpenOnThatDate() {
            givenFixtureUser();

            List<PayCycle> history = serviceAt(TODAY).historyAsOf(USER_ID, d(2026, 6, 15), 2);

            assertThat(history).extracting(PayCycle::start, PayCycle::end).containsExactly(
                    org.assertj.core.groups.Tuple.tuple(d(2026, 5, 8), d(2026, 6, 9)),
                    org.assertj.core.groups.Tuple.tuple(d(2026, 4, 10), d(2026, 5, 7)));
        }

        @Test
        void configuredRuleOverridesTheLearnedRule() {
            givenFixtureUser();
            when(paydayRuleRepository.findById(USER_ID)).thenReturn(Optional.of(UserPaydayRule.builder()
                    .userId(USER_ID).dayOfMonth(25).weekendShift(WeekendShift.NONE).build()));

            Optional<PayCycle> current = serviceAt(TODAY).current(USER_ID);

            // first 25th strictly after Sep 9 + 20 days = Oct 25 (no weekend shift configured)
            assertThat(current).hasValueSatisfying(cycle -> {
                assertThat(cycle.expectedNextAnchor()).isEqualTo(d(2026, 10, 25));
                assertThat(cycle.end()).isEqualTo(d(2026, 10, 24));
                assertThat(cycle.state()).isEqualTo(CycleState.OPEN);
            });
        }

        @Test
        void configuredRuleDayOfMonth31ClampsToTheLastDayOfAShorterMonth() {
            givenFixtureUser();
            when(paydayRuleRepository.findById(USER_ID)).thenReturn(Optional.of(UserPaydayRule.builder()
                    .userId(USER_ID).dayOfMonth(31).weekendShift(WeekendShift.NONE).build()));

            // lastAnchor Sep 9 + 20 days = Sep 29; September has 30 days, so day 31 clamps to Sep 30.
            Optional<PayCycle> current = serviceAt(TODAY).current(USER_ID);

            assertThat(current).hasValueSatisfying(cycle -> {
                assertThat(cycle.expectedNextAnchor()).isEqualTo(d(2026, 9, 30));
                assertThat(cycle.end()).isEqualTo(d(2026, 9, 29));
                assertThat(cycle.state()).isEqualTo(CycleState.OPEN);
            });
        }

        @Test
        void historyAsOfBeforeTheFirstAnchorIsEmptyWithoutError() {
            givenFixtureUser();

            List<PayCycle> history = serviceAt(TODAY).historyAsOf(USER_ID, d(2025, 10, 1), 5);

            assertThat(history).isEmpty();
        }
    }

    // --- cycle state transitions -------------------------------------------------------------------------------

    @Nested
    @DisplayName("OPEN → AWAITING_SALARY → CLOSED")
    class StateTransitions {

        @Test
        void dayBeforeExpectedPaydayIsStillOpen() {
            givenFixtureUser();
            givenNoConfiguredRule();

            Optional<PayCycle> current = serviceAt(d(2026, 10, 8)).current(USER_ID);

            assertThat(current).map(PayCycle::state).hasValue(CycleState.OPEN);
            assertThat(current).map(PayCycle::end).hasValue(d(2026, 10, 8));
        }

        @Test
        void onExpectedPaydayWithoutSalaryTheCycleIsAwaitingSalaryAndTheEndIsNotMoved() {
            givenFixtureUser();
            givenNoConfiguredRule();

            Optional<PayCycle> current = serviceAt(EXPECTED_PAYDAY).current(USER_ID);

            assertThat(current).hasValueSatisfying(cycle -> {
                assertThat(cycle.state()).isEqualTo(CycleState.AWAITING_SALARY);
                assertThat(cycle.start()).isEqualTo(d(2026, 9, 9));
                assertThat(cycle.end()).isEqualTo(d(2026, 10, 8));
                assertThat(cycle.expectedNextAnchor()).isEqualTo(EXPECTED_PAYDAY);
            });
        }

        @Test
        void lateSalaryKeepsAwaitingSalaryWithTheEndInThePast_noBackwardClamping() {
            givenFixtureUser();
            givenNoConfiguredRule();
            LocalDate today = d(2026, 10, 20);

            Optional<PayCycle> current = serviceAt(today).current(USER_ID);

            assertThat(current).hasValueSatisfying(cycle -> {
                assertThat(cycle.state()).isEqualTo(CycleState.AWAITING_SALARY);
                assertThat(cycle.end()).isEqualTo(d(2026, 10, 8));
                assertThat(cycle.expectedNextAnchor()).isEqualTo(EXPECTED_PAYDAY);
                assertThat(cycle.contains(today)).isFalse();
                assertThat(cycle.dayIndex(today)).isGreaterThan(cycle.lengthDays());
            });
        }

        @Test
        void newAnchorClosesThePreviousCycleAndOpensTheNextOne() {
            givenAnchorCategory();
            givenConfiguredSalaryWallet(SALARY_WALLET_ID);
            givenSalaryWalletAnchors(plus(September2026Fixture.ANCHOR_DATES, EXPECTED_PAYDAY)); // salary arrived Oct 9
            givenNoConfiguredRule();
            PayCycleService service = serviceAt(d(2026, 10, 20));

            assertThat(service.current(USER_ID)).hasValueSatisfying(cycle -> {
                assertThat(cycle.start()).isEqualTo(EXPECTED_PAYDAY);
                assertThat(cycle.state()).isEqualTo(CycleState.OPEN);
                assertThat(cycle.expectedNextAnchor()).isEqualTo(d(2026, 11, 10)); // Tuesday, no shift needed
                assertThat(cycle.end()).isEqualTo(d(2026, 11, 9));
            });
            assertThat(service.last(USER_ID)).hasValueSatisfying(cycle -> {
                assertThat(cycle.start()).isEqualTo(d(2026, 9, 9));
                assertThat(cycle.end()).isEqualTo(d(2026, 10, 8));
                assertThat(cycle.state()).isEqualTo(CycleState.CLOSED);
            });
        }
    }

    // --- anchor merging ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Anchor merging (min-cycle-days = 20)")
    class Merging {

        @Test
        void twoAnchorsFiveDaysApartFormOneCycle() {
            givenAnchorCategory();
            givenConfiguredSalaryWallet(SALARY_WALLET_ID);
            givenSalaryWalletAnchors(List.of(d(2026, 8, 10), d(2026, 8, 15)));
            givenNoConfiguredRule();
            PayCycleService service = serviceAt(TODAY);

            assertThat(service.anchorDates(USER_ID, TODAY, 10)).containsExactly(d(2026, 8, 10));
            assertThat(service.current(USER_ID)).hasValueSatisfying(cycle -> {
                assertThat(cycle.start()).isEqualTo(d(2026, 8, 10));
                // single anchor → +1 month; Sep 14 is past Sep 10 → awaiting
                assertThat(cycle.expectedNextAnchor()).isEqualTo(d(2026, 9, 10));
                assertThat(cycle.state()).isEqualTo(CycleState.AWAITING_SALARY);
            });
            assertThat(service.last(USER_ID)).isEmpty();
            assertThat(service.history(USER_ID, 6)).isEmpty();
        }

        @Test
        void mergeKeepsAnAnchorOnlyWhenAtLeastMinCycleDaysAfterTheLastKeptOne() {
            List<LocalDate> merged = PayCycleService.mergeAnchors(
                    List.of(d(2026, 1, 25), d(2026, 1, 15), d(2026, 1, 1)), 20);

            // Jan 15 joins Jan 1's cycle; Jan 25 is 24 days after the kept Jan 1 and starts a new one
            assertThat(merged).containsExactly(d(2026, 1, 25), d(2026, 1, 1));
        }

        @Test
        void mergeCollapsesSameDayDuplicatesAndAcceptsExactlyMinCycleDays() {
            List<LocalDate> merged = PayCycleService.mergeAnchors(
                    List.of(d(2026, 3, 1), d(2026, 3, 1), d(2026, 3, 21), d(2026, 4, 9), d(2026, 4, 10)), 20);

            assertThat(merged).containsExactly(d(2026, 4, 10), d(2026, 3, 21), d(2026, 3, 1));
        }

        @Test
        void mergeIsOrderIndependentAndIgnoresNulls() {
            List<LocalDate> shuffled = new ArrayList<>(September2026Fixture.ANCHOR_DATES);
            Collections.shuffle(shuffled);
            shuffled.add(null);

            List<LocalDate> merged = PayCycleService.mergeAnchors(shuffled, 20);

            List<LocalDate> expected = new ArrayList<>(September2026Fixture.ANCHOR_DATES);
            Collections.reverse(expected);
            assertThat(merged).containsExactlyElementsOf(expected);
        }
    }

    // --- salary wallet resolution ------------------------------------------------------------------------------

    @Nested
    @DisplayName("Salary wallet resolution")
    class SalaryWallet {

        @Test
        void configuredSalaryWalletWinsAndSkipsTheFallbackLookup() {
            givenConfiguredSalaryWallet(SAVINGS_WALLET_ID);
            PayCycleService service = serviceAt(TODAY);

            assertThat(service.salaryWalletId(USER_ID)).hasValue(SAVINGS_WALLET_ID);
            assertThat(service.isSalaryWallet(USER_ID, SAVINGS_WALLET_ID)).isTrue();
            assertThat(service.isSalaryWallet(USER_ID, SALARY_WALLET_ID)).isFalse();
            assertThat(service.isSalaryWallet(USER_ID, null)).isFalse();
            verify(transactionRepository, never()).findLatestAnchorWalletIds(any(), any(), any());
        }

        @Test
        void withoutConfiguredWalletTheWalletHoldingTheLatestAnchorTransactionIsUsed() {
            givenConfiguredSalaryWallet(null);
            givenAnchorCategory();
            when(transactionRepository.findLatestAnchorWalletIds(eq(USER_ID), eq(ANCHOR_CATEGORY_ID), any(Pageable.class)))
                    .thenReturn(List.of(SALARY_WALLET_ID));

            assertThat(serviceAt(TODAY).salaryWalletId(USER_ID)).hasValue(SALARY_WALLET_ID);
        }

        @Test
        void unknownUserFallsBackToTheAnchorTransactionLookup() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
            givenAnchorCategory();
            when(transactionRepository.findLatestAnchorWalletIds(eq(USER_ID), eq(ANCHOR_CATEGORY_ID), any(Pageable.class)))
                    .thenReturn(List.of());

            assertThat(serviceAt(TODAY).salaryWalletId(USER_ID)).isEmpty();
        }

        @Test
        void noSalaryWalletAtAllMeansNoCycle() {
            givenConfiguredSalaryWallet(null);
            givenAnchorCategory();
            when(transactionRepository.findLatestAnchorWalletIds(eq(USER_ID), eq(ANCHOR_CATEGORY_ID), any(Pageable.class)))
                    .thenReturn(List.of());
            PayCycleService service = serviceAt(TODAY);

            assertThat(service.current(USER_ID)).isEmpty();
            assertThat(service.last(USER_ID)).isEmpty();
            assertThat(service.history(USER_ID, 6)).isEmpty();
            assertThat(service.anchorDates(USER_ID, TODAY, 6)).isEmpty();
            verify(transactionRepository, never()).findAnchorDates(any(), any(), any(), any(), any());
        }

        @Test
        void configuredWalletWithoutAnchorsNeverFallsBackToAnotherWalletThatHasThem() {
            givenAnchorCategory();
            givenConfiguredSalaryWallet(SALARY_WALLET_ID);
            // configured wallet: no anchors at all
            givenSalaryWalletAnchors(List.of());
            // a different wallet DOES have anchor transactions, but must never be consulted once a wallet is configured
            org.mockito.Mockito.lenient().when(transactionRepository.findAnchorDates(eq(SAVINGS_WALLET_ID),
                    eq(USER_ID), eq(ANCHOR_CATEGORY_ID), any(LocalDate.class), any(Pageable.class)))
                    .thenReturn(List.of(d(2026, 9, 9), d(2026, 8, 10)));
            PayCycleService service = serviceAt(TODAY);

            assertThat(service.current(USER_ID)).isEmpty();
            assertThat(service.last(USER_ID)).isEmpty();
            assertThat(service.history(USER_ID, 6)).isEmpty();
            assertThat(service.salaryWalletId(USER_ID)).hasValue(SALARY_WALLET_ID);
            verify(transactionRepository, never()).findAnchorDates(eq(SAVINGS_WALLET_ID), any(), any(), any(), any());
            verify(transactionRepository, never()).findLatestAnchorWalletIds(any(), any(), any());
        }

        @Test
        void anchorsAreReadFromTheSalaryWalletOnly() {
            givenFixtureUser();
            givenNoConfiguredRule();

            serviceAt(TODAY).current(USER_ID);

            verify(transactionRepository).findAnchorDates(eq(SALARY_WALLET_ID), eq(USER_ID), eq(ANCHOR_CATEGORY_ID),
                    eq(TODAY), any(Pageable.class));
            verify(transactionRepository, never()).findAnchorDates(eq(SAVINGS_WALLET_ID), any(), any(), any(), any());
        }
    }

    // --- guards ------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Guards")
    class Guards {

        @Test
        void noAnchorCategoryMeansNoCycleAndNoTransactionQueries() {
            when(categoryRepository.findByUserIdAndIsCycleAnchorTrue(USER_ID)).thenReturn(Optional.empty());
            PayCycleService service = serviceAt(TODAY);

            assertThat(service.current(USER_ID)).isEmpty();
            assertThat(service.last(USER_ID)).isEmpty();
            assertThat(service.history(USER_ID, 6)).isEmpty();
            assertThat(service.anchorDates(USER_ID, TODAY, 6)).isEmpty();
            verify(transactionRepository, never()).findAnchorDates(any(), any(), any(), any(), any());
        }

        @Test
        void salaryWalletWithoutAnchorTransactionsMeansNoCycle() {
            givenAnchorCategory();
            givenConfiguredSalaryWallet(SALARY_WALLET_ID);
            givenSalaryWalletAnchors(List.of());
            PayCycleService service = serviceAt(TODAY);

            assertThat(service.current(USER_ID)).isEmpty();
            assertThat(service.history(USER_ID, 6)).isEmpty();
        }

        @Test
        void nonPositiveCountsReturnEmptyWithoutQuerying() {
            PayCycleService service = serviceAt(TODAY);

            assertThat(service.history(USER_ID, 0)).isEmpty();
            assertThat(service.historyAsOf(USER_ID, TODAY, -1)).isEmpty();
            assertThat(service.anchorDates(USER_ID, TODAY, 0)).isEmpty();
            verify(categoryRepository, never()).findByUserIdAndIsCycleAnchorTrue(any());
        }

        @Test
        void minCycleDaysMustBeBetweenOneAndTheResolverGuard() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> new PayCycleProperties(0))
                    .isInstanceOf(IllegalArgumentException.class);
            // wider than the resolver's earliest prediction would merge away a genuine salary
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> new PayCycleProperties(ExpectedPaydayResolver.MIN_CYCLE_DAYS + 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new PayCycleProperties(ExpectedPaydayResolver.MIN_CYCLE_DAYS).minCycleDays()).isEqualTo(20);
        }
    }
}
