package com.mikeshaggy.backend.common.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.regression.September2026Fixture;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class PayCyclePeriodResolverTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private PayCyclePeriodResolver resolver;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Integer WALLET_ID = 1;
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 11);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    private PayCyclePeriodResolver resolverWithClock() {
        return new PayCyclePeriodResolver(transactionRepository, FIXED_CLOCK);
    }

    private PayCyclePeriodResolver resolverWithClock(LocalDate today) {
        return new PayCyclePeriodResolver(transactionRepository,
                Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    @Test
    void supports_returnsPayCycle() {
        // given
        var result = resolver.supports();

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
            PayCyclePeriodResolver sut = new PayCyclePeriodResolver(
                    transactionRepository, September2026Fixture.CLOCK);
            when(transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                    September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    September2026Fixture.ANCHOR_CATEGORY_ID, PageRequest.of(0, 1)))
                    .thenReturn(September2026Fixture.anchorTransactionsDescending(1));

            // when
            PeriodDto result = sut.resolve(September2026Fixture.SALARY_WALLET_ID, September2026Fixture.USER_ID,
                    null, null, September2026Fixture.ANCHOR_CATEGORY_ID);

            // then
            assertThat(result).isEqualTo(new PeriodDto(
                    LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 9),
                    PeriodType.PAY_CYCLE));
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
