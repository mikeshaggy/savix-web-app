package com.mikeshaggy.backend.fixedpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.dto.*;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FixedPaymentDashboardServiceTest {

    @Mock
    private FixedPaymentRepository fixedPaymentRepository;

    @Mock
    private FixedPaymentOccurrenceRepository occurrenceRepository;

    @Mock
    private FixedPaymentTileAssembler tileAssembler;

    @Mock
    private WalletService walletService;

    @Mock
    private TransactionService transactionService;

    @Mock
    private PeriodService periodService;

    @Mock
    private Clock clock;


    @InjectMocks
    private FixedPaymentDashboardService fixedPaymentDashboardService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 11);

    private User user;
    private Wallet wallet;
    private Category category;

    @BeforeEach
    void setUp() {
        Instant fixedInstant = TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant();
        lenient().when(clock.instant()).thenReturn(fixedInstant);
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());

        user = User.builder().id(USER_ID).email("test@test.com").username("test").build();
        wallet =
                Wallet.builder().id(1).name("Main").balance(new BigDecimal("5000.00")).user(user).build();

        category =
                Category.builder()
                        .id(10)
                        .name("Rent")
                        .type(CategoryType.EXPENSE)
                        .emoji("🏠")
                        .user(user)
                        .build();
    }

    private FixedPayment buildFixedPayment(Integer id) {
        return FixedPayment.builder()
                .id(id)
                .wallet(wallet)
                .category(category)
                .title("Monthly Rent")
                .amount(new BigDecimal("1500.00"))
                .anchorDate(LocalDate.of(2026, 1, 1))
                .cycle(Cycle.MONTHLY)
                .activeFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    @Nested
    class TileData {

        private final PeriodDto period =
                new PeriodDto(
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 4, 5),
                        PeriodType.PAY_CYCLE);

        @Test
        void noActivePayments_delegatesToAssembleEmpty() {
            // given
            when(fixedPaymentRepository.findAllActiveByWalletIdAndUserId(
                            eq(1), eq(USER_ID), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);

            FixedTransactionsTileDto emptyTile =
                    new FixedTransactionsTileDto(
                            period.startDate(),
                            period.endDate(),
                            period.billingEndDate(),
                            new FixedSummaryDto(
                                    BigDecimal.ZERO,
                                    0,
                                    BigDecimal.ZERO,
                                    0,
                                    BigDecimal.ZERO,
                                    0,
                                    BigDecimal.ZERO,
                                    0,
                                    0.0),
                            new FixedProgressDto(0, 0, 0.0, null, null, null, null, 0),
                            wallet.getBalance(),
                            wallet.getBalance(),
                            new RiskIndicatorDto(false, null),
                            List.of(),
                            List.of(),
                            List.of());

            when(tileAssembler.assembleEmpty(period, wallet.getBalance())).thenReturn(emptyTile);

            // when
            FixedTransactionsTileDto result =
                    fixedPaymentDashboardService.getFixedPaymentsTileData(period, 1, USER_ID);

            // then
            assertThat(result).isSameAs(emptyTile);
            verify(tileAssembler).assembleEmpty(period, wallet.getBalance());
            verify(tileAssembler, never()).assemble(any(), anyList(), anyList(), any(), any(), anyInt());
        }

        @Test
        void withActivePayments_fetchesOccurrencesAndDelegatesToAssembler() {
            // given
            FixedPayment fp1 = buildFixedPayment(1);
            FixedPayment fp2 = buildFixedPayment(2);

            when(fixedPaymentRepository.findAllActiveByWalletIdAndUserId(
                            eq(1), eq(USER_ID), any(LocalDate.class)))
                    .thenReturn(List.of(fp1, fp2));
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);

            List<FixedPaymentOccurrence> periodOccurrences =
                    List.of(
                            FixedPaymentOccurrence.builder()
                                    .id(1L)
                                    .fixedPayment(fp1)
                                    .status(OccurrenceStatus.PAID)
                                    .expectedAmount(new BigDecimal("1500.00"))
                                    .build());
            List<FixedPaymentOccurrence> overdueOccurrences =
                    List.of(
                            FixedPaymentOccurrence.builder()
                                    .id(2L)
                                    .fixedPayment(fp2)
                                    .status(OccurrenceStatus.OVERDUE)
                                    .expectedAmount(new BigDecimal("1500.00"))
                                    .build());

            when(occurrenceRepository.findAllByFixedPaymentIdsAndDueDateBetween(
                            eq(List.of(1, 2)), eq(period.startDate()), eq(period.billingEndDate())))
                    .thenReturn(periodOccurrences);
            when(occurrenceRepository.findByFixedPaymentIdsAndStatus(
                            eq(List.of(1, 2)), eq(OccurrenceStatus.OVERDUE)))
                    .thenReturn(overdueOccurrences);
            when(transactionService.sumIncomeByWalletIdAndDateRange(
                            eq(1), eq(period.startDate()), eq(period.endDate())))
                    .thenReturn(new BigDecimal("4000.00"));

            FixedTransactionsTileDto expectedTile = mock(FixedTransactionsTileDto.class);
            when(tileAssembler.assemble(
                            eq(period),
                            eq(periodOccurrences),
                            eq(overdueOccurrences),
                            eq(new BigDecimal("4000.00")),
                            eq(new BigDecimal("5000.00")),
                            eq(2)))
                    .thenReturn(expectedTile);

            // when
            FixedTransactionsTileDto result =
                    fixedPaymentDashboardService.getFixedPaymentsTileData(period, 1, USER_ID);

            // then
            assertThat(result).isSameAs(expectedTile);
        }

        @Test
        void walletNotFound_throwsEntityNotFound() {
            // given
            when(fixedPaymentRepository.findAllActiveByWalletIdAndUserId(
                            eq(1), eq(USER_ID), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(walletService.getWalletEntityByIdForUser(1, USER_ID))
                    .thenThrow(new EntityNotFoundException("Wallet not found with id: 1"));

            // when
            // then
            assertThatThrownBy(
                            () -> fixedPaymentDashboardService.getFixedPaymentsTileData(period, 1, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Wallet not found");
        }

        @Test
        void forCurrentPeriod_resolvesPeriodThenDelegates() {
            // given
            when(periodService.getCurrentPeriod(USER_ID, 1)).thenReturn(period);
            when(fixedPaymentRepository.findAllActiveByWalletIdAndUserId(
                            eq(1), eq(USER_ID), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);

            FixedTransactionsTileDto emptyTile = mock(FixedTransactionsTileDto.class);
            when(tileAssembler.assembleEmpty(period, wallet.getBalance())).thenReturn(emptyTile);

            // when
            FixedTransactionsTileDto result =
                    fixedPaymentDashboardService.getFixedPaymentsTileDataForCurrentPeriod(1, USER_ID);

            // then
            assertThat(result).isSameAs(emptyTile);
            verify(periodService).getCurrentPeriod(USER_ID, 1);
        }
    }
}
