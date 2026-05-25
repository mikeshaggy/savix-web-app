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
            when(transactionRepository.findByWalletIdAndCategoryIdOrderByTransactionDateDesc(
                            WALLET_ID, 5, PageRequest.of(0, 1)))
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

            when(transactionRepository.findByWalletIdAndCategoryIdOrderByTransactionDateDesc(
                            WALLET_ID, 5, PageRequest.of(0, 1)))
                    .thenReturn(List.of());

            // when
            PeriodDto result = sut.resolve(WALLET_ID, USER_ID, null, null, 5);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(result.endDate()).isEqualTo(TODAY);
        }
    }
}
