package com.mikeshaggy.backend.dashboard.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
class LastPayCyclePeriodResolverTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private LastPayCyclePeriodResolver resolver;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Integer WALLET_ID = 1;
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 11);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    private LastPayCyclePeriodResolver resolverWithClock() {
        return new LastPayCyclePeriodResolver(transactionRepository, FIXED_CLOCK);
    }

    @Test
    void supports_returnsLastPayCycle() {
        // given
        var result = resolver.supports();

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

            when(transactionRepository.findByWalletIdAndCategoryIdOrderByTransactionDateDesc(
                            WALLET_ID, 5, PageRequest.of(0, 2)))
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
            when(transactionRepository.findByWalletIdAndCategoryIdOrderByTransactionDateDesc(
                            WALLET_ID, 5, PageRequest.of(0, 2)))
                    .thenReturn(List.of(single));

            // when
            PeriodDto result = sut.resolve(WALLET_ID, USER_ID, null, null, 5);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        }
    }
}
