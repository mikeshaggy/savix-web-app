package com.mikeshaggy.backend.fixedpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.PeriodService;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private PayCycleService payCycleService;

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

    private static FixedPaymentOccurrence occurrence(long id, FixedPayment fp, OccurrenceStatus status, LocalDate dueDate) {
        return FixedPaymentOccurrence.builder()
                .id(id)
                .fixedPayment(fp)
                .status(status)
                .expectedAmount(new BigDecimal("1500.00"))
                .dueDate(dueDate)
                .build();
    }

    /** An open cycle Mar 1 – Mar 31 whose expected payday is Apr 1 ({@code billingEndDate} = the payday itself). */
    private static PeriodDto openCycle() {
        return new PeriodDto(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),
                PeriodType.PAY_CYCLE, CycleState.OPEN, LocalDate.of(2026, 4, 1), true);
    }

    @Nested
    class TileData {

        private final PeriodDto period = openCycle();

        @Test
        void noActivePayments_delegatesToAssembleEmpty() {
            // given
            when(fixedPaymentRepository.findAllActiveInPeriodByWalletIdAndUserId(
                            1, USER_ID, period.startDate(), period.endDate()))
                    .thenReturn(List.of());
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);

            FixedTransactionsTileDto emptyTile = mock(FixedTransactionsTileDto.class);
            when(tileAssembler.assembleEmpty(period, wallet.getBalance())).thenReturn(emptyTile);

            // when
            FixedTransactionsTileDto result =
                    fixedPaymentDashboardService.getFixedPaymentsTileData(period, 1, USER_ID);

            // then
            assertThat(result).isSameAs(emptyTile);
            verify(tileAssembler).assembleEmpty(period, wallet.getBalance());
            verify(tileAssembler, never()).assemble(any(), anyList(), anyList(), any(), any(), anyInt(), any());
        }

        @Test
        void withActivePayments_queriesCommittedWindowAndDelegatesToAssemblerAsOfToday() {
            // given
            FixedPayment fp1 = buildFixedPayment(1);
            FixedPayment fp2 = buildFixedPayment(2);

            when(fixedPaymentRepository.findAllActiveInPeriodByWalletIdAndUserId(
                            1, USER_ID, period.startDate(), period.endDate()))
                    .thenReturn(List.of(fp1, fp2));
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);

            List<FixedPaymentOccurrence> periodOccurrences =
                    List.of(occurrence(1L, fp1, OccurrenceStatus.PAID, LocalDate.of(2026, 3, 5)));
            List<FixedPaymentOccurrence> overdueOccurrences =
                    List.of(occurrence(2L, fp2, OccurrenceStatus.OVERDUE, LocalDate.of(2026, 3, 1)));

            // the committed window ends on endDate (= expected payday − 1), never on billingEndDate
            when(occurrenceRepository.findAllByFixedPaymentIdsAndDueDateBetween(
                            List.of(1, 2), period.startDate(), period.endDate()))
                    .thenReturn(periodOccurrences);
            when(occurrenceRepository.findByFixedPaymentIdsAndStatus(List.of(1, 2), OccurrenceStatus.OVERDUE))
                    .thenReturn(overdueOccurrences);
            when(transactionService.sumIncomeByWalletIdAndDateRange(
                            1, USER_ID, period.startDate(), period.endDate()))
                    .thenReturn(new BigDecimal("4000.00"));

            FixedTransactionsTileDto expectedTile = mock(FixedTransactionsTileDto.class);
            when(tileAssembler.assemble(
                            eq(period),
                            eq(periodOccurrences),
                            eq(overdueOccurrences),
                            eq(new BigDecimal("4000.00")),
                            eq(new BigDecimal("5000.00")),
                            eq(2),
                            eq(TODAY)))
                    .thenReturn(expectedTile);

            // when
            FixedTransactionsTileDto result =
                    fixedPaymentDashboardService.getFixedPaymentsTileData(period, 1, USER_ID);

            // then
            assertThat(result).isSameAs(expectedTile);
            verify(occurrenceRepository, never()).findAllByFixedPaymentIdsAndDueDateBetween(
                    anyList(), any(), eq(period.billingEndDate()));
        }

        @Test
        void asOfAwareTileFiltersOccurrencesAfterFixedPaymentEndDateAndDelegatesAsOfDate() {
            // given
            FixedPayment fp = buildFixedPayment(1);
            fp.setActiveTo(LocalDate.of(2026, 3, 20));
            LocalDate asOfDate = LocalDate.of(2026, 3, 15);

            when(fixedPaymentRepository.findAllActiveInPeriodByWalletIdAndUserId(
                            1, USER_ID, period.startDate(), period.endDate()))
                    .thenReturn(List.of(fp));
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);

            FixedPaymentOccurrence valid = occurrence(1L, fp, OccurrenceStatus.PENDING, LocalDate.of(2026, 3, 20));
            FixedPaymentOccurrence afterEndDate = occurrence(2L, fp, OccurrenceStatus.PENDING, LocalDate.of(2026, 3, 25));

            when(occurrenceRepository.findAllByFixedPaymentIdsAndDueDateBetween(
                            List.of(1), period.startDate(), period.endDate()))
                    .thenReturn(List.of(valid, afterEndDate));
            when(occurrenceRepository.findByFixedPaymentIdsAndStatus(List.of(1), OccurrenceStatus.OVERDUE))
                    .thenReturn(List.of());
            when(transactionService.sumIncomeByWalletIdAndDateRange(
                            1, USER_ID, period.startDate(), period.endDate()))
                    .thenReturn(new BigDecimal("4000.00"));

            FixedTransactionsTileDto expectedTile = mock(FixedTransactionsTileDto.class);
            when(tileAssembler.assemble(
                            eq(period),
                            anyList(),
                            eq(List.of()),
                            eq(new BigDecimal("4000.00")),
                            eq(new BigDecimal("5000.00")),
                            eq(1),
                            eq(asOfDate)))
                    .thenReturn(expectedTile);

            // when
            FixedTransactionsTileDto result =
                    fixedPaymentDashboardService.getFixedPaymentsTileData(period, 1, USER_ID, asOfDate);

            // then
            assertThat(result).isSameAs(expectedTile);
            ArgumentCaptor<List<FixedPaymentOccurrence>> occurrencesCaptor = ArgumentCaptor.forClass(List.class);
            verify(tileAssembler).assemble(
                    eq(period),
                    occurrencesCaptor.capture(),
                    eq(List.of()),
                    eq(new BigDecimal("4000.00")),
                    eq(new BigDecimal("5000.00")),
                    eq(1),
                    eq(asOfDate));
            assertThat(occurrencesCaptor.getValue()).containsExactly(valid);
        }

        @Test
        void awaitingSalary_extendsCommittedWindowThroughToday() {
            // given: expected payday Mar 5 has passed without a salary; today is Mar 11
            LocalDate start = LocalDate.of(2026, 2, 5);
            LocalDate expectedPayday = LocalDate.of(2026, 3, 5);
            PeriodDto awaiting = new PeriodDto(start, expectedPayday.minusDays(1), expectedPayday,
                    PeriodType.PAY_CYCLE, CycleState.AWAITING_SALARY, expectedPayday, true);
            FixedPayment fp = buildFixedPayment(1);

            when(fixedPaymentRepository.findAllActiveInPeriodByWalletIdAndUserId(1, USER_ID, start, TODAY))
                    .thenReturn(List.of(fp));
            FixedPaymentOccurrence lateDay = occurrence(1L, fp, OccurrenceStatus.PENDING, LocalDate.of(2026, 3, 8));
            when(occurrenceRepository.findAllByFixedPaymentIdsAndDueDateBetween(List.of(1), start, TODAY))
                    .thenReturn(List.of(lateDay));
            when(occurrenceRepository.findByFixedPaymentIdsAndStatus(List.of(1), OccurrenceStatus.OVERDUE))
                    .thenReturn(List.of());
            when(transactionService.sumIncomeByWalletIdAndDateRange(1, USER_ID, start, TODAY))
                    .thenReturn(BigDecimal.ZERO);
            FixedTransactionsTileDto expectedTile = mock(FixedTransactionsTileDto.class);
            when(tileAssembler.assemble(any(), anyList(), anyList(), any(), any(), anyInt(), any()))
                    .thenReturn(expectedTile);

            // when
            FixedTransactionsTileDto result =
                    fixedPaymentDashboardService.getFixedPaymentsTileData(awaiting, wallet, USER_ID, TODAY);

            // then: the window keeps the cycle metadata but ends today, and the late-day occurrence is in it
            assertThat(result).isSameAs(expectedTile);
            ArgumentCaptor<PeriodDto> window = ArgumentCaptor.forClass(PeriodDto.class);
            verify(tileAssembler).assemble(window.capture(), eq(List.of(lateDay)), eq(List.of()),
                    eq(BigDecimal.ZERO), eq(new BigDecimal("5000.00")), eq(1), eq(TODAY));
            assertThat(window.getValue().startDate()).isEqualTo(start);
            assertThat(window.getValue().endDate()).isEqualTo(TODAY);
            assertThat(window.getValue().cycleState()).isEqualTo(CycleState.AWAITING_SALARY);
            assertThat(window.getValue().expectedNextAnchorDate()).isEqualTo(expectedPayday);
            assertThat(window.getValue().periodType()).isEqualTo(PeriodType.PAY_CYCLE);
        }

        @Test
        void closedCycle_windowIsNotExtendedAndExcludesOccurrenceDueOnBillingEndDate() {
            // given: a CLOSED period (LAST_PAY_CYCLE) Feb 5 - Mar 4, whose billingEndDate (Mar 5) is the next
            // actual anchor — an occurrence due exactly on billingEndDate belongs to the following cycle and must
            // never be pulled in by an accidental billingEndDate bound, even though the cycle already ended and
            // "today" (Mar 11) is long past both endDate and billingEndDate.
            LocalDate start = LocalDate.of(2026, 2, 5);
            LocalDate end = LocalDate.of(2026, 3, 4);
            LocalDate nextAnchor = LocalDate.of(2026, 3, 5);
            PeriodDto closed = new PeriodDto(start, end, nextAnchor,
                    PeriodType.LAST_PAY_CYCLE, CycleState.CLOSED, null, true);
            FixedPayment fp = buildFixedPayment(1);

            when(fixedPaymentRepository.findAllActiveInPeriodByWalletIdAndUserId(1, USER_ID, start, end))
                    .thenReturn(List.of(fp));
            FixedPaymentOccurrence inCycle = occurrence(1L, fp, OccurrenceStatus.PAID, end);
            when(occurrenceRepository.findAllByFixedPaymentIdsAndDueDateBetween(List.of(1), start, end))
                    .thenReturn(List.of(inCycle));
            when(occurrenceRepository.findByFixedPaymentIdsAndStatus(List.of(1), OccurrenceStatus.OVERDUE))
                    .thenReturn(List.of());
            when(transactionService.sumIncomeByWalletIdAndDateRange(1, USER_ID, start, end))
                    .thenReturn(BigDecimal.ZERO);
            FixedTransactionsTileDto expectedTile = mock(FixedTransactionsTileDto.class);
            when(tileAssembler.assemble(any(), anyList(), anyList(), any(), any(), anyInt(), any()))
                    .thenReturn(expectedTile);

            // when
            FixedTransactionsTileDto result =
                    fixedPaymentDashboardService.getFixedPaymentsTileData(closed, wallet, USER_ID, TODAY);

            // then: the window is the period unchanged — CLOSED is not extended like AWAITING_SALARY is
            assertThat(result).isSameAs(expectedTile);
            ArgumentCaptor<PeriodDto> window = ArgumentCaptor.forClass(PeriodDto.class);
            verify(tileAssembler).assemble(window.capture(), eq(List.of(inCycle)), eq(List.of()),
                    eq(BigDecimal.ZERO), eq(new BigDecimal("5000.00")), eq(1), eq(TODAY));
            assertThat(window.getValue().startDate()).isEqualTo(start);
            assertThat(window.getValue().endDate()).isEqualTo(end);
            assertThat(window.getValue().cycleState()).isEqualTo(CycleState.CLOSED);
            // the query never used billingEndDate (nextAnchor) as the upper bound
            verify(occurrenceRepository, never())
                    .findAllByFixedPaymentIdsAndDueDateBetween(anyList(), any(), eq(nextAnchor));
            verify(fixedPaymentRepository, never())
                    .findAllActiveInPeriodByWalletIdAndUserId(anyInt(), any(), any(), eq(nextAnchor));
        }

        @Test
        void walletNotFound_throwsEntityNotFound() {
            // given
            when(walletService.getWalletEntityByIdForUser(1, USER_ID))
                    .thenThrow(new EntityNotFoundException("Wallet not found with id: 1"));

            // when
            // then
            assertThatThrownBy(
                            () -> fixedPaymentDashboardService.getFixedPaymentsTileData(period, 1, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Wallet not found");
        }
    }

    @Nested
    class ForCurrentPeriod {

        private final LocalDate start = LocalDate.of(2026, 2, 10);
        private final LocalDate expectedPayday = LocalDate.of(2026, 3, 20);

        @Test
        void salaryWallet_resolvesOpenCycleFromPayCycleServiceAndUsesCommittedWindow() {
            // given
            PayCycle cycle = PayCycle.open(USER_ID, 1, start, expectedPayday);
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(payCycleService.current(USER_ID)).thenReturn(Optional.of(cycle));
            FixedPayment fp = buildFixedPayment(1);
            when(fixedPaymentRepository.findAllActiveInPeriodByWalletIdAndUserId(
                            1, USER_ID, start, expectedPayday.minusDays(1)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findAllByFixedPaymentIdsAndDueDateBetween(
                            List.of(1), start, expectedPayday.minusDays(1)))
                    .thenReturn(List.of());
            when(occurrenceRepository.findByFixedPaymentIdsAndStatus(List.of(1), OccurrenceStatus.OVERDUE))
                    .thenReturn(List.of());
            when(transactionService.sumIncomeByWalletIdAndDateRange(
                            1, USER_ID, start, expectedPayday.minusDays(1)))
                    .thenReturn(BigDecimal.ZERO);
            FixedTransactionsTileDto expectedTile = mock(FixedTransactionsTileDto.class);
            when(tileAssembler.assemble(any(), anyList(), anyList(), any(), any(), anyInt(), any()))
                    .thenReturn(expectedTile);

            // when
            FixedTransactionsTileDto result =
                    fixedPaymentDashboardService.getFixedPaymentsTileDataForCurrentPeriod(1, USER_ID);

            // then
            assertThat(result).isSameAs(expectedTile);
            ArgumentCaptor<PeriodDto> window = ArgumentCaptor.forClass(PeriodDto.class);
            verify(tileAssembler).assemble(window.capture(), anyList(), anyList(), any(), any(), eq(1), eq(TODAY));
            assertThat(window.getValue().startDate()).isEqualTo(start);
            assertThat(window.getValue().endDate()).isEqualTo(expectedPayday.minusDays(1));
            assertThat(window.getValue().periodType()).isEqualTo(PeriodType.PAY_CYCLE);
            assertThat(window.getValue().cycleState()).isEqualTo(CycleState.OPEN);
            assertThat(window.getValue().expectedNextAnchorDate()).isEqualTo(expectedPayday);
            assertThat(window.getValue().salaryWallet()).isTrue();
            verifyNoInteractions(periodService);
        }

        @Test
        void nonSalaryWallet_returnsEmptyMonthlyTileWithoutCommittedSemantics() {
            // given: the user's cycle belongs to wallet 7; the requested wallet is 1
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(payCycleService.current(USER_ID))
                    .thenReturn(Optional.of(PayCycle.open(USER_ID, 7, start, expectedPayday)));
            when(periodService.resolve(PeriodType.MONTHLY, 1, USER_ID, null, null))
                    .thenReturn(PeriodDto.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                            LocalDate.of(2026, 4, 1), PeriodType.MONTHLY));
            FixedTransactionsTileDto emptyTile = mock(FixedTransactionsTileDto.class);
            when(tileAssembler.assembleEmpty(any(), eq(wallet.getBalance()))).thenReturn(emptyTile);

            // when
            FixedTransactionsTileDto result =
                    fixedPaymentDashboardService.getFixedPaymentsTileDataForCurrentPeriod(1, USER_ID);

            // then
            assertThat(result).isSameAs(emptyTile);
            ArgumentCaptor<PeriodDto> period = ArgumentCaptor.forClass(PeriodDto.class);
            verify(tileAssembler).assembleEmpty(period.capture(), eq(wallet.getBalance()));
            assertThat(period.getValue().periodType()).isEqualTo(PeriodType.MONTHLY);
            assertThat(period.getValue().salaryWallet()).isFalse();
            assertThat(period.getValue().cycleState()).isNull();
            assertThat(period.getValue().startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(period.getValue().endDate()).isEqualTo(LocalDate.of(2026, 3, 31));
            verifyNoInteractions(fixedPaymentRepository, occurrenceRepository);
        }

        @Test
        void userWithoutCycle_returnsEmptyMonthlyTile() {
            // given
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(payCycleService.current(USER_ID)).thenReturn(Optional.empty());
            when(periodService.resolve(PeriodType.MONTHLY, 1, USER_ID, null, null))
                    .thenReturn(PeriodDto.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                            LocalDate.of(2026, 4, 1), PeriodType.MONTHLY));
            FixedTransactionsTileDto emptyTile = mock(FixedTransactionsTileDto.class);
            when(tileAssembler.assembleEmpty(any(), eq(wallet.getBalance()))).thenReturn(emptyTile);

            // when
            FixedTransactionsTileDto result =
                    fixedPaymentDashboardService.getFixedPaymentsTileDataForCurrentPeriod(1, USER_ID);

            // then
            assertThat(result).isSameAs(emptyTile);
            verifyNoInteractions(fixedPaymentRepository, occurrenceRepository);
        }

        @Test
        void awaitingSalary_windowRunsThroughToday() {
            // given: expected payday Mar 5 passed; today Mar 11
            LocalDate lateExpected = LocalDate.of(2026, 3, 5);
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(payCycleService.current(USER_ID))
                    .thenReturn(Optional.of(PayCycle.awaitingSalary(USER_ID, 1, start, lateExpected)));
            when(fixedPaymentRepository.findAllActiveInPeriodByWalletIdAndUserId(1, USER_ID, start, TODAY))
                    .thenReturn(List.of());
            FixedTransactionsTileDto emptyTile = mock(FixedTransactionsTileDto.class);
            when(tileAssembler.assembleEmpty(any(), eq(wallet.getBalance()))).thenReturn(emptyTile);

            // when
            fixedPaymentDashboardService.getFixedPaymentsTileDataForCurrentPeriod(1, USER_ID);

            // then
            ArgumentCaptor<PeriodDto> window = ArgumentCaptor.forClass(PeriodDto.class);
            verify(tileAssembler).assembleEmpty(window.capture(), eq(wallet.getBalance()));
            assertThat(window.getValue().endDate()).isEqualTo(TODAY);
            assertThat(window.getValue().cycleState()).isEqualTo(CycleState.AWAITING_SALARY);
            assertThat(window.getValue().expectedNextAnchorDate()).isEqualTo(lateExpected);
        }
    }
}
