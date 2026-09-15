package com.mikeshaggy.backend.common.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.regression.September2026Fixture;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class PayCyclePeriodResolverTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PayCycleService payCycleService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Integer WALLET_ID = 1;
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 11);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    private static final FeatureFlags FLAG_OFF = new FeatureFlags(false, false, false, false, false);
    private static final FeatureFlags FLAG_ON = new FeatureFlags(true, false, false, false, false);

    private PayCyclePeriodResolver resolver(FeatureFlags flags, Clock clock) {
        return new PayCyclePeriodResolver(transactionRepository, clock, flags, payCycleService,
                new MonthlyPeriodResolver(clock));
    }

    private PayCyclePeriodResolver resolverWithClock() {
        return resolver(FLAG_OFF, FIXED_CLOCK);
    }

    private PayCyclePeriodResolver resolverWithClock(LocalDate today) {
        return resolver(FLAG_OFF,
                Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    @Test
    void supports_returnsPayCycle() {
        // given
        var result = resolverWithClock().supports();

        // when
        // then
        assertThat(result).isEqualTo(PeriodType.PAY_CYCLE);
    }

    @Nested
    class WithAnchorTransaction {

        @Test
        void usesAnchorDateAsStart_todayAsEnd() {
            // given
            PayCyclePeriodResolver sut = resolverWithClock();

            Transaction anchorTx =
                    Transaction.builder().transactionDate(LocalDate.of(2026, 2, 25)).build();
            when(transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                WALLET_ID, USER_ID, 5, PageRequest.of(0, 1)))
                    .thenReturn(List.of(anchorTx));

            // when
            PeriodDto result = sut.resolve(WALLET_ID, USER_ID, null, null, 5);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 2, 25));
            assertThat(result.endDate()).isEqualTo(TODAY);
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2026, 3, 25));
            assertThat(result.periodType()).isEqualTo(PeriodType.PAY_CYCLE);
        }
    }

    @Nested
    class WithoutAnchor {

        @Test
        void noCycleAnchorCategory_fallsBackToCalendarMonth() {
            // given
            PayCyclePeriodResolver sut = resolverWithClock();

            // when
            PeriodDto result = sut.resolve(WALLET_ID, USER_ID, null, null, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(result.endDate()).isEqualTo(TODAY);
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(result.periodType()).isEqualTo(PeriodType.PAY_CYCLE);
        }

        @Test
        void anchorCategoryExists_butNoTransactions_fallsBackToCalendarMonth() {
            // given
            PayCyclePeriodResolver sut = resolverWithClock();

            when(transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                WALLET_ID, USER_ID, 5, PageRequest.of(0, 1)))
                    .thenReturn(List.of());

            // when
            PeriodDto result = sut.resolve(WALLET_ID, USER_ID, null, null, 5);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(result.endDate()).isEqualTo(TODAY);
        }
    }

    @Nested
    class September2026FixtureAnchors {

        @Test
        void latestAnchorSep9_today_Sep14_pinsLegacyPlusOneMonth() {
            // given
            PayCyclePeriodResolver sut = resolver(FLAG_OFF, September2026Fixture.CLOCK);
            when(transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                    September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    September2026Fixture.ANCHOR_CATEGORY_ID, PageRequest.of(0, 1)))
                    .thenReturn(September2026Fixture.anchorTransactionsDescending(1));

            // when
            PeriodDto result = sut.resolve(September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    null, null, September2026Fixture.ANCHOR_CATEGORY_ID);

            // then
            assertThat(result).isEqualTo(PeriodDto.of(
                    LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 9),
                    PeriodType.PAY_CYCLE));
            verifyNoInteractions(payCycleService);
        }

        @ParameterizedTest
        @MethodSource("com.mikeshaggy.backend.common.period.PayCyclePeriodResolverTest#anchorAndExpectedBillingEnd")
        void anchor_pinsLegacyPlusOneMonthBoundary(LocalDate anchor, LocalDate expectedBillingEnd) {
            // given
            LocalDate today = anchor.plusDays(5);
            PayCyclePeriodResolver sut = resolverWithClock(today);
            when(transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                    September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    September2026Fixture.ANCHOR_CATEGORY_ID, PageRequest.of(0, 1)))
                    .thenReturn(List.of(Transaction.builder().transactionDate(anchor).build()));

            // when
            PeriodDto result = sut.resolve(September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    null, null, September2026Fixture.ANCHOR_CATEGORY_ID);

            // then
            assertThat(result.startDate()).isEqualTo(anchor);
            assertThat(result.endDate()).isEqualTo(today);
            assertThat(result.billingEndDate()).isEqualTo(expectedBillingEnd);
            assertThat(result.periodType()).isEqualTo(PeriodType.PAY_CYCLE);
        }
    }

    @Nested
    class PayCycleV2 {

        private PayCyclePeriodResolver sut() {
            return resolver(FLAG_ON, September2026Fixture.CLOCK);
        }

        @Test
        void salaryWallet_openCycle_endsAtExpectedAnchorMinusOne_notToday() {
            // given
            when(payCycleService.current(September2026Fixture.USER_ID)).thenReturn(Optional.of(PayCycle.open(
                    September2026Fixture.USER_ID, September2026Fixture.SALARY_WALLET_ID,
                    LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9))));

            // when
            PeriodDto result = sut().resolve(September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    null, null, September2026Fixture.ANCHOR_CATEGORY_ID);

            // then
            assertThat(result).isEqualTo(new PeriodDto(
                    LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9),
                    PeriodType.PAY_CYCLE, CycleState.OPEN, LocalDate.of(2026, 10, 9), true));
            verify(payCycleService).current(September2026Fixture.USER_ID);
            verifyNoInteractions(transactionRepository);
        }

        @Test
        void awaitingSalary_keepsExpectedEnd_noBackwardClamp() {
            // given today = Oct 12, salary expected Oct 9 not received
            PayCyclePeriodResolver sut = resolver(FLAG_ON,
                    Clock.fixed(LocalDate.of(2026, 10, 12).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                            ZoneId.systemDefault()));
            when(payCycleService.current(September2026Fixture.USER_ID)).thenReturn(Optional.of(PayCycle.awaitingSalary(
                    September2026Fixture.USER_ID, September2026Fixture.SALARY_WALLET_ID,
                    LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9))));

            // when
            PeriodDto result = sut.resolve(September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    null, null, September2026Fixture.ANCHOR_CATEGORY_ID);

            // then
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 10, 8));
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2026, 10, 9));
            assertThat(result.cycleState()).isEqualTo(CycleState.AWAITING_SALARY);
            assertThat(result.expectedNextAnchorDate()).isEqualTo(LocalDate.of(2026, 10, 9));
            assertThat(result.salaryWallet()).isTrue();
        }

        @Test
        void nonSalaryWallet_returnsCalendarMonthTypedMonthly_notPayCycle() {
            // given
            when(payCycleService.current(September2026Fixture.USER_ID)).thenReturn(Optional.of(PayCycle.open(
                    September2026Fixture.USER_ID, September2026Fixture.SALARY_WALLET_ID,
                    LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9))));

            // when
            PeriodDto result = sut().resolve(September2026Fixture.SAVINGS_WALLET_ID, September2026Fixture.USER_ID,
                    null, null, September2026Fixture.ANCHOR_CATEGORY_ID);

            // then
            assertThat(result).isEqualTo(new PeriodDto(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1),
                    PeriodType.MONTHLY, null, null, false));
            verify(payCycleService).current(September2026Fixture.USER_ID);
            verifyNoInteractions(transactionRepository);
        }

        @Test
        void noCycle_returnsCalendarMonthTypedMonthly() {
            // given
            when(payCycleService.current(September2026Fixture.USER_ID)).thenReturn(Optional.empty());

            // when
            PeriodDto result = sut().resolve(September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    null, null, null);

            // then
            assertThat(result.periodType()).isEqualTo(PeriodType.MONTHLY);
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(result.salaryWallet()).isFalse();
            assertThat(result.cycleState()).isNull();
            verifyNoInteractions(transactionRepository);
        }
    }

    static Stream<Arguments> anchorAndExpectedBillingEnd() {
        return Stream.of(
                Arguments.of(LocalDate.of(2025, 11, 10), LocalDate.of(2025, 12, 10)),
                Arguments.of(LocalDate.of(2025, 12, 10), LocalDate.of(2026, 1, 10)),
                Arguments.of(LocalDate.of(2026, 1, 9), LocalDate.of(2026, 2, 9)),
                Arguments.of(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 10)),
                Arguments.of(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 10)),
                Arguments.of(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 10)),
                Arguments.of(LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 8)),
                Arguments.of(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 10)),
                Arguments.of(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10)),
                Arguments.of(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 10)),
                Arguments.of(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9)));
    }
}
