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
class LastPayCyclePeriodResolverTest {

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

    private LastPayCyclePeriodResolver resolver(FeatureFlags flags, Clock clock) {
        return new LastPayCyclePeriodResolver(transactionRepository, clock, flags, payCycleService,
                new MonthlyPeriodResolver(clock));
    }

    private LastPayCyclePeriodResolver resolverWithClock() {
        return resolver(FLAG_OFF, FIXED_CLOCK);
    }

    @Test
    void supports_returnsLastPayCycle() {
        // given
        var result = resolverWithClock().supports();

        // when
        // then
        assertThat(result).isEqualTo(PeriodType.LAST_PAY_CYCLE);
    }

    @Nested
    class WithTwoAnchorTransactions {

        @Test
        void returnsPreviousCycleRange() {
            // given
            LastPayCyclePeriodResolver sut = resolverWithClock();

            Transaction latest = Transaction.builder().transactionDate(LocalDate.of(2026, 2, 25)).build();
            Transaction previous =
                    Transaction.builder().transactionDate(LocalDate.of(2026, 1, 25)).build();

            when(transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                WALLET_ID, USER_ID, 5, PageRequest.of(0, 2)))
                    .thenReturn(List.of(latest, previous));

            // when
            PeriodDto result = sut.resolve(WALLET_ID, USER_ID, null, null, 5);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 1, 25));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 2, 24));
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2026, 2, 25));
            assertThat(result.periodType()).isEqualTo(PeriodType.LAST_PAY_CYCLE);
        }
    }

    @Nested
    class FallbackToCalendarMonth {

        @Test
        void noCycleAnchorCategory_returnsPreviousCalendarMonth() {
            // given
            LastPayCyclePeriodResolver sut = resolverWithClock();

            // when
            PeriodDto result = sut.resolve(WALLET_ID, USER_ID, null, null, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(result.periodType()).isEqualTo(PeriodType.LAST_PAY_CYCLE);
        }

        @Test
        void onlyOneAnchorTransaction_returnsPreviousCalendarMonth() {
            // given
            LastPayCyclePeriodResolver sut = resolverWithClock();

            Transaction single = Transaction.builder().transactionDate(LocalDate.of(2026, 2, 25)).build();
            when(transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                WALLET_ID, USER_ID, 5, PageRequest.of(0, 2)))
                    .thenReturn(List.of(single));

            // when
            PeriodDto result = sut.resolve(WALLET_ID, USER_ID, null, null, 5);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        }
    }

    @Nested
    class September2026FixtureAnchors {

        @Test
        void latestTwoAnchors_returnAug10ToSep8() {
            // given
            LastPayCyclePeriodResolver sut = resolver(FLAG_OFF, September2026Fixture.CLOCK);
            when(transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                    September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    September2026Fixture.ANCHOR_CATEGORY_ID, PageRequest.of(0, 2)))
                    .thenReturn(September2026Fixture.anchorTransactionsDescending(2));

            // when
            PeriodDto result = sut.resolve(September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    null, null, September2026Fixture.ANCHOR_CATEGORY_ID);

            // then
            assertThat(result).isEqualTo(PeriodDto.of(
                    LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10),
                    PeriodType.LAST_PAY_CYCLE));
            verifyNoInteractions(payCycleService);
        }

        @ParameterizedTest
        @MethodSource("com.mikeshaggy.backend.common.period.LastPayCyclePeriodResolverTest#consecutiveAnchorPairs")
        void consecutiveAnchors_pinLegacyBoundaries(LocalDate previous, LocalDate latest,
                                                    LocalDate expectedStart, LocalDate expectedEnd,
                                                    LocalDate expectedBillingEnd) {
            // given
            LastPayCyclePeriodResolver sut = resolver(FLAG_OFF, September2026Fixture.CLOCK);
            when(transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                    September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    September2026Fixture.ANCHOR_CATEGORY_ID, PageRequest.of(0, 2)))
                    .thenReturn(List.of(
                            Transaction.builder().transactionDate(latest).build(),
                            Transaction.builder().transactionDate(previous).build()));

            // when
            PeriodDto result = sut.resolve(September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    null, null, September2026Fixture.ANCHOR_CATEGORY_ID);

            // then
            assertThat(result).isEqualTo(PeriodDto.of(
                    expectedStart, expectedEnd, expectedBillingEnd, PeriodType.LAST_PAY_CYCLE));
        }
    }

    @Nested
    class PayCycleV2 {

        private LastPayCyclePeriodResolver sut() {
            return resolver(FLAG_ON, September2026Fixture.CLOCK);
        }

        @Test
        void salaryWallet_returnsLastClosedCycle_withNextActualAnchorAsBillingEnd() {
            // given
            when(payCycleService.last(September2026Fixture.USER_ID)).thenReturn(Optional.of(PayCycle.closed(
                    September2026Fixture.USER_ID, September2026Fixture.SALARY_WALLET_ID,
                    LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8))));

            // when
            PeriodDto result = sut().resolve(September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    null, null, September2026Fixture.ANCHOR_CATEGORY_ID);

            // then
            assertThat(result).isEqualTo(new PeriodDto(
                    LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 9),
                    PeriodType.LAST_PAY_CYCLE, CycleState.CLOSED, null, true));
            verify(payCycleService).last(September2026Fixture.USER_ID);
            verifyNoInteractions(transactionRepository);
        }

        @Test
        void nonSalaryWallet_returnsPreviousCalendarMonthTypedMonthly() {
            // given
            when(payCycleService.last(September2026Fixture.USER_ID)).thenReturn(Optional.of(PayCycle.closed(
                    September2026Fixture.USER_ID, September2026Fixture.SALARY_WALLET_ID,
                    LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8))));

            // when
            PeriodDto result = sut().resolve(September2026Fixture.SAVINGS_WALLET_ID, September2026Fixture.USER_ID,
                    null, null, September2026Fixture.ANCHOR_CATEGORY_ID);

            // then
            assertThat(result).isEqualTo(new PeriodDto(
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1),
                    PeriodType.MONTHLY, null, null, false));
            verifyNoInteractions(transactionRepository);
        }

        @Test
        void noClosedCycle_returnsPreviousCalendarMonthTypedMonthly() {
            // given
            when(payCycleService.last(September2026Fixture.USER_ID)).thenReturn(Optional.empty());

            // when
            PeriodDto result = sut().resolve(September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    null, null, September2026Fixture.ANCHOR_CATEGORY_ID);

            // then
            assertThat(result.periodType()).isEqualTo(PeriodType.MONTHLY);
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 8, 31));
            assertThat(result.salaryWallet()).isFalse();
            verifyNoInteractions(transactionRepository);
        }
    }

    static Stream<Arguments> consecutiveAnchorPairs() {
        return Stream.of(
                Arguments.of(LocalDate.of(2025, 11, 10), LocalDate.of(2025, 12, 10),
                        LocalDate.of(2025, 11, 10), LocalDate.of(2025, 12, 9), LocalDate.of(2025, 12, 10)),
                Arguments.of(LocalDate.of(2025, 12, 10), LocalDate.of(2026, 1, 9),
                        LocalDate.of(2025, 12, 10), LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 10)),
                Arguments.of(LocalDate.of(2026, 1, 9), LocalDate.of(2026, 2, 10),
                        LocalDate.of(2026, 1, 9), LocalDate.of(2026, 2, 9), LocalDate.of(2026, 2, 9)),
                Arguments.of(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 10),
                        LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 10)),
                Arguments.of(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 10),
                        LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 9), LocalDate.of(2026, 4, 10)),
                Arguments.of(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 8),
                        LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 7), LocalDate.of(2026, 5, 10)),
                Arguments.of(LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 8)),
                Arguments.of(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 10)),
                Arguments.of(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9), LocalDate.of(2026, 8, 10)),
                Arguments.of(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 9),
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10)));
    }
}
