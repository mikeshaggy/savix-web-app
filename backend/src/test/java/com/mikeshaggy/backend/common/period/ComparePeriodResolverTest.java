package com.mikeshaggy.backend.common.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ComparePeriodResolverTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private ComparePeriodResolver resolver;

    private static final Integer WALLET_ID = 1;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Integer ANCHOR_CATEGORY_ID = 5;

    @Nested
    class PayCycleComparison {

        @Test
        void withTwoAnchorTransactions_returnsPreviousCycle() {
            // given
            PeriodDto current =
                    new PeriodDto(
                            LocalDate.of(2026, 2, 25),
                            LocalDate.of(2026, 3, 11),
                            LocalDate.of(2026, 3, 25),
                            PeriodType.PAY_CYCLE);

            Transaction latest = Transaction.builder().transactionDate(LocalDate.of(2026, 2, 25)).build();
            Transaction previous =
                    Transaction.builder().transactionDate(LocalDate.of(2026, 1, 25)).build();
            when(transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                WALLET_ID, USER_ID, ANCHOR_CATEGORY_ID, PageRequest.of(0, 2)))
                    .thenReturn(List.of(latest, previous));

            // when
            PeriodDto result = resolver.resolve(current, WALLET_ID, USER_ID, ANCHOR_CATEGORY_ID);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 1, 25));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 2, 24));
            assertThat(result.periodType()).isEqualTo(PeriodType.LAST_PAY_CYCLE);
        }

        @Test
        void insufficientAnchorTransactions_fallsBackToPreviousEqualLengthPeriod() {
            // given
            PeriodDto current =
                    new PeriodDto(
                            LocalDate.of(2026, 2, 25),
                            LocalDate.of(2026, 3, 11),
                            LocalDate.of(2026, 3, 25),
                            PeriodType.PAY_CYCLE);

            // when
            PeriodDto result = resolver.resolve(current, WALLET_ID, USER_ID, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 2, 10));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 2, 24));
            assertThat(result.endDate()).isBefore(current.startDate());
            assertThat(result.periodType()).isEqualTo(PeriodType.CUSTOM);
        }
    }

    @Nested
    class LastPayCycleComparison {

        @Test
        void withThreeAnchorTransactions_returnsTwoCyclesBack() {
            // given
            PeriodDto current =
                    new PeriodDto(
                            LocalDate.of(2026, 1, 25),
                            LocalDate.of(2026, 2, 24),
                            LocalDate.of(2026, 2, 25),
                            PeriodType.LAST_PAY_CYCLE);

            Transaction t1 = Transaction.builder().transactionDate(LocalDate.of(2026, 2, 25)).build();
            Transaction t2 = Transaction.builder().transactionDate(LocalDate.of(2026, 1, 25)).build();
            Transaction t3 = Transaction.builder().transactionDate(LocalDate.of(2025, 12, 25)).build();
            when(transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                WALLET_ID, USER_ID, ANCHOR_CATEGORY_ID, PageRequest.of(0, 3)))
                    .thenReturn(List.of(t1, t2, t3));

            // when
            PeriodDto result = resolver.resolve(current, WALLET_ID, USER_ID, ANCHOR_CATEGORY_ID);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2025, 12, 25));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 1, 24));
        }

        @Test
        void insufficientAnchorTransactions_fallsBackToPreviousEqualLengthPeriod() {
            // given
            PeriodDto current =
                    new PeriodDto(
                            LocalDate.of(2026, 1, 25),
                            LocalDate.of(2026, 2, 24),
                            LocalDate.of(2026, 2, 25),
                            PeriodType.LAST_PAY_CYCLE);

            when(transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                WALLET_ID, USER_ID, ANCHOR_CATEGORY_ID, PageRequest.of(0, 3)))
                    .thenReturn(
                            List.of(
                                    Transaction.builder().transactionDate(LocalDate.of(2026, 2, 25)).build(),
                                    Transaction.builder().transactionDate(LocalDate.of(2026, 1, 25)).build()));

            // when
            PeriodDto result = resolver.resolve(current, WALLET_ID, USER_ID, ANCHOR_CATEGORY_ID);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2025, 12, 25));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 1, 24));
            assertThat(result.endDate()).isBefore(current.startDate());
            assertThat(result.periodType()).isEqualTo(PeriodType.CUSTOM);
        }
    }

    @Nested
    class CustomComparison {

        @Test
        void shiftsByDurationBackward() {
            // given
            PeriodDto current =
                    new PeriodDto(
                            LocalDate.of(2026, 3, 1),
                            LocalDate.of(2026, 3, 31),
                            LocalDate.of(2026, 4, 1),
                            PeriodType.CUSTOM);

            // when
            PeriodDto result = resolver.resolve(current, WALLET_ID, USER_ID, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 1, 29));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(result.periodType()).isEqualTo(PeriodType.CUSTOM);
        }

        @Test
        void singleDayPeriod_shiftsOneDayBack() {
            // given
            PeriodDto current =
                    new PeriodDto(
                            LocalDate.of(2026, 3, 15),
                            LocalDate.of(2026, 3, 15),
                            LocalDate.of(2026, 4, 15),
                            PeriodType.CUSTOM);

            // when
            PeriodDto result = resolver.resolve(current, WALLET_ID, USER_ID, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 3, 14));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 3, 14));
        }
    }

    @Nested
    class MonthlyComparison {

        @Test
        void shiftsToPreviousCalendarMonth() {
            // given
            PeriodDto current =
                    new PeriodDto(
                            LocalDate.of(2026, 3, 1),
                            LocalDate.of(2026, 3, 31),
                            LocalDate.of(2026, 4, 1),
                            PeriodType.MONTHLY);

            // when
            PeriodDto result = resolver.resolve(current, WALLET_ID, USER_ID, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(result.periodType()).isEqualTo(PeriodType.MONTHLY);
        }

        @Test
        void january_shiftsAcrossYearBoundary() {
            // given
            PeriodDto current =
                    new PeriodDto(
                            LocalDate.of(2026, 1, 1),
                            LocalDate.of(2026, 1, 31),
                            LocalDate.of(2026, 2, 1),
                            PeriodType.MONTHLY);

            // when
            PeriodDto result = resolver.resolve(current, WALLET_ID, USER_ID, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2025, 12, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2025, 12, 31));
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        }
    }
}
