package com.mikeshaggy.backend.fixedpayment.service;

import static com.mikeshaggy.backend.regression.September2026Fixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * §7.6 — payment due on payday (lives next to the package-private {@link FixedPaymentTileAssembler}). The fixture's open cycle is Sep 9 → Oct 8 with the next salary expected Oct 9.
 * An occurrence due Oct 8 is committed in this cycle on every path; one due Oct 9 (the payday) belongs to the
 * next cycle on every path and becomes its day-1 committed occurrence once the Oct 9 salary arrives.
 * <p>
 * The dashboard tile, {@code /api/fixed-payments/tile} and the forecast {@code remainingFixedPayments} are
 * exercised through the real {@link FixedPaymentDashboardService} + {@link FixedPaymentTileAssembler}; the
 * repositories are stubbed to honour the query bounds, so a window that leaked to {@code billingEndDate}
 * would surface the payday occurrence.
 */
@ExtendWith(MockitoExtension.class)
class PaydayOccurrenceRegressionTest {

    private static final LocalDate PAYDAY_DUE = EXPECTED_NEXT_PAYDAY; // Oct 9
    private static final LocalDate LAST_CYCLE_DAY = EXPECTED_NEXT_PAYDAY.minusDays(1); // Oct 8
    private static final BigDecimal PAYDAY_AMOUNT = new BigDecimal("59.00");
    private static final long PAYDAY_OCCURRENCE_ID = 900L;

    @Mock private FixedPaymentRepository fixedPaymentRepository;
    @Mock private FixedPaymentOccurrenceRepository occurrenceRepository;
    @Mock private WalletService walletService;
    @Mock private TransactionService transactionService;
    @Mock private PeriodService periodService;
    @Mock private PayCycleService payCycleService;
    @Mock private AnalyticsTransactionQueryService transactionQueryService;

    private FixedPaymentDashboardService fixedPaymentDashboardService;
    private Wallet salaryWallet;
    private List<FixedPaymentOccurrence> occurrences;

    @BeforeEach
    void setUp() {
        salaryWallet = Wallet.builder().id(SALARY_WALLET_ID).name(SALARY_WALLET_NAME)
                .balance(SALARY_WALLET_BALANCE).build();
        Category category = Category.builder().id(200).name("fixed").type(CategoryType.EXPENSE).emoji("F").build();
        FixedPayment fixedPayment = FixedPayment.builder()
                .id(1).wallet(salaryWallet).category(category).title("FX").amount(BigDecimal.ONE)
                .anchorDate(LocalDate.of(2026, 1, 1)).cycle(Cycle.MONTHLY).activeFrom(LocalDate.of(2026, 1, 1))
                .build();

        long id = 1;
        var all = new java.util.ArrayList<FixedPaymentOccurrence>();
        for (FixedOccurrence o : PAID_OCCURRENCES) {
            FixedPaymentOccurrence occ = occurrence(id++, fixedPayment, OccurrenceStatus.PAID, o.plannedAmount(), o.dueDate());
            occ.setPaidAmount(o.paidAmount());
            all.add(occ);
        }
        for (FixedOccurrence o : PENDING_OCCURRENCES) {
            all.add(occurrence(id++, fixedPayment, OccurrenceStatus.PENDING, o.plannedAmount(), o.dueDate()));
        }
        // the §7.6 occurrence: due exactly on the expected payday
        all.add(occurrence(PAYDAY_OCCURRENCE_ID, fixedPayment, OccurrenceStatus.PENDING, PAYDAY_AMOUNT, PAYDAY_DUE));
        occurrences = List.copyOf(all);

        lenient().when(walletService.getWalletEntityByIdForUser(SALARY_WALLET_ID, USER_ID)).thenReturn(salaryWallet);
        lenient().when(fixedPaymentRepository.findAllActiveInPeriodByWalletIdAndUserId(
                        eq(SALARY_WALLET_ID), eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(fixedPayment));
        // honour the query bounds exactly as the JPQL BETWEEN would
        lenient().when(occurrenceRepository.findAllByFixedPaymentIdsAndDueDateBetween(
                        anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    LocalDate from = inv.getArgument(1);
                    LocalDate to = inv.getArgument(2);
                    return occurrences.stream()
                            .filter(o -> !o.getDueDate().isBefore(from) && !o.getDueDate().isAfter(to))
                            .toList();
                });
        lenient().when(occurrenceRepository.findByFixedPaymentIdsAndStatus(anyList(), eq(OccurrenceStatus.OVERDUE)))
                .thenReturn(List.of());
        lenient().when(transactionService.sumIncomeByWalletIdAndDateRange(
                        eq(SALARY_WALLET_ID), eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(INCOME_FOR_PERIOD);

        fixedPaymentDashboardService = new FixedPaymentDashboardService(
                fixedPaymentRepository, occurrenceRepository, new FixedPaymentTileAssembler(CLOCK),
                walletService, transactionService, periodService, payCycleService, CLOCK);
    }

    @Test
    void dashboardTile_countsOct8ButNotThePaydayOccurrence() {
        FixedTransactionsTileDto tile = fixedPaymentDashboardService
                .getFixedPaymentsTileData(PAY_CYCLE_PERIOD_V2, salaryWallet, USER_ID, TODAY);

        assertCommittedWindowOfCurrentCycle(tile);
    }

    @Test
    void tileEndpoint_countsOct8ButNotThePaydayOccurrence() {
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.open(USER_ID, SALARY_WALLET_ID, CURRENT_CYCLE_START, EXPECTED_NEXT_PAYDAY)));

        FixedTransactionsTileDto tile = fixedPaymentDashboardService
                .getFixedPaymentsTileDataForCurrentPeriod(SALARY_WALLET_ID, USER_ID);

        assertCommittedWindowOfCurrentCycle(tile);
    }

    @Test
    void dashboardAndTileEndpointAgreeOnTheCommittedSet() {
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.open(USER_ID, SALARY_WALLET_ID, CURRENT_CYCLE_START, EXPECTED_NEXT_PAYDAY)));

        FixedTransactionsTileDto dashboard = fixedPaymentDashboardService
                .getFixedPaymentsTileData(PAY_CYCLE_PERIOD_V2, salaryWallet, USER_ID, TODAY);
        FixedTransactionsTileDto endpoint = fixedPaymentDashboardService
                .getFixedPaymentsTileDataForCurrentPeriod(SALARY_WALLET_ID, USER_ID);

        assertThat(endpoint.summary()).isEqualTo(dashboard.summary());
        assertThat(endpoint.balanceAfterFixed()).isEqualByComparingTo(dashboard.balanceAfterFixed());
        assertThat(ids(endpoint.upcoming())).isEqualTo(ids(dashboard.upcoming()));
        assertThat(ids(endpoint.paid())).isEqualTo(ids(dashboard.paid()));
        assertThat(ids(endpoint.overdue())).isEqualTo(ids(dashboard.overdue()));
        assertThat(endpoint.periodStart()).isEqualTo(dashboard.periodStart());
        assertThat(endpoint.periodEnd()).isEqualTo(dashboard.periodEnd());
        assertThat(endpoint.expectedPaydayDate()).isEqualTo(dashboard.expectedPaydayDate()).isEqualTo(EXPECTED_NEXT_PAYDAY);
        assertThat(endpoint.cycleState()).isEqualTo(dashboard.cycleState()).isEqualTo(CycleState.OPEN);
    }

    @Test
    void dashboardAndTileEndpointAgreeWhenAwaitingSalaryOnTheExpectedPayday() {
        // the expected payday (Oct 9) has arrived but no salary transaction has landed yet: the cycle is
        // AWAITING_SALARY (T6) and its window extends through today (T5) — through Oct 9 itself — so the
        // payday occurrence is legitimately committed here, on both paths, unlike the OPEN-cycle case above.
        Clock paydayNoSalary = Clock.fixed(PAYDAY_DUE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        FixedPaymentDashboardService service = new FixedPaymentDashboardService(
                fixedPaymentRepository, occurrenceRepository, new FixedPaymentTileAssembler(paydayNoSalary),
                walletService, transactionService, periodService, payCycleService, paydayNoSalary);
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.awaitingSalary(USER_ID, SALARY_WALLET_ID, CURRENT_CYCLE_START, EXPECTED_NEXT_PAYDAY)));
        PeriodDto awaitingPeriod = new PeriodDto(CURRENT_CYCLE_START, LAST_CYCLE_DAY, EXPECTED_NEXT_PAYDAY,
                PeriodType.PAY_CYCLE, CycleState.AWAITING_SALARY, EXPECTED_NEXT_PAYDAY, true);

        FixedTransactionsTileDto dashboard = service
                .getFixedPaymentsTileData(awaitingPeriod, salaryWallet, USER_ID, PAYDAY_DUE);
        FixedTransactionsTileDto endpoint = service
                .getFixedPaymentsTileDataForCurrentPeriod(SALARY_WALLET_ID, USER_ID);

        assertThat(endpoint.summary()).isEqualTo(dashboard.summary());
        assertThat(ids(endpoint.upcoming())).isEqualTo(ids(dashboard.upcoming())).contains(PAYDAY_OCCURRENCE_ID);
        assertThat(ids(endpoint.paid())).isEqualTo(ids(dashboard.paid()));
        assertThat(ids(endpoint.overdue())).isEqualTo(ids(dashboard.overdue()));
        assertThat(endpoint.periodEnd()).isEqualTo(dashboard.periodEnd()).isEqualTo(PAYDAY_DUE);
        assertThat(endpoint.cycleState()).isEqualTo(dashboard.cycleState()).isEqualTo(CycleState.AWAITING_SALARY);
    }

    @Test
    void forecastRemainingFixed_matchesTheCommittedWindow() {
        when(periodService.resolve(PeriodType.PAY_CYCLE, SALARY_WALLET_ID, USER_ID, null, null))
                .thenReturn(PAY_CYCLE_PERIOD_V2);
        lenient().when(transactionQueryService.sum(eq(SALARY_WALLET_ID), eq(USER_ID), any(), any(), eq(CategoryType.INCOME)))
                .thenReturn(INCOME_FOR_PERIOD);
        lenient().when(transactionQueryService.sum(eq(SALARY_WALLET_ID), eq(USER_ID), any(), any(), eq(CategoryType.EXPENSE)))
                .thenReturn(EXPENSES_TO_DATE);
        lenient().when(transactionQueryService.sumUnlinked(eq(SALARY_WALLET_ID), eq(USER_ID), any(), any(), eq(CategoryType.EXPENSE)))
                .thenReturn(VARIABLE_EXPENSES_TO_DATE);
        SpendingProjectionService projectionService = new SpendingProjectionService(
                transactionQueryService, walletService, periodService, fixedPaymentDashboardService,
                new SpendingProjectionCalculator(), CLOCK);

        SpendingProjectionDto projection = projectionService
                .getSpendingProjection(SALARY_WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null);

        // five pending occurrences through Oct 8 = 794.27; the 59.00 due on payday is not committed here
        assertThat(projection.remainingFixedPayments()).isEqualByComparingTo(REMAINING_FIXED_PAY_CYCLE);
    }

    @Test
    void afterTheOct9Salary_thePaydayOccurrenceIsDayOneCommittedOfTheNewCycle() {
        // the salary arrived on Oct 9: new open cycle Oct 9 → Nov 8, next expected Nov 9; today Oct 9
        LocalDate newExpected = LocalDate.of(2026, 11, 9);
        Clock oct9 = Clock.fixed(PAYDAY_DUE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        FixedPaymentDashboardService service = new FixedPaymentDashboardService(
                fixedPaymentRepository, occurrenceRepository, new FixedPaymentTileAssembler(oct9),
                walletService, transactionService, periodService, payCycleService, oct9);
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.open(USER_ID, SALARY_WALLET_ID, PAYDAY_DUE, newExpected)));
        PeriodDto newCycle = new PeriodDto(PAYDAY_DUE, newExpected.minusDays(1), newExpected, PeriodType.PAY_CYCLE,
                CycleState.OPEN, newExpected, true);

        FixedTransactionsTileDto endpoint = service.getFixedPaymentsTileDataForCurrentPeriod(SALARY_WALLET_ID, USER_ID);
        FixedTransactionsTileDto dashboard = service.getFixedPaymentsTileData(newCycle, salaryWallet, USER_ID, PAYDAY_DUE);

        for (FixedTransactionsTileDto tile : List.of(endpoint, dashboard)) {
            assertThat(tile.periodStart()).isEqualTo(PAYDAY_DUE);
            assertThat(ids(tile.upcoming())).containsExactly(PAYDAY_OCCURRENCE_ID);
            assertThat(tile.upcoming().getFirst().daysDelta()).isZero();
            assertThat(tile.progress().nextDueDate()).isEqualTo(PAYDAY_DUE);
            assertThat(tile.summary().remainingAmount()).isEqualByComparingTo(PAYDAY_AMOUNT);
            // nothing from the previous cycle leaks into the new one
            assertThat(tile.paid()).isEmpty();
        }
    }

    private static void assertCommittedWindowOfCurrentCycle(FixedTransactionsTileDto tile) {
        assertThat(tile.periodStart()).isEqualTo(CURRENT_CYCLE_START);
        assertThat(tile.periodEnd()).isEqualTo(LAST_CYCLE_DAY);
        assertThat(tile.expectedPaydayDate()).isEqualTo(EXPECTED_NEXT_PAYDAY);
        assertThat(tile.cycleState()).isEqualTo(CycleState.OPEN);
        assertThat(tile.upcoming()).extracting(FixedOccurrenceRowDto::dueDate)
                .contains(LAST_CYCLE_DAY)
                .doesNotContain(PAYDAY_DUE);
        assertThat(ids(tile.upcoming())).doesNotContain(PAYDAY_OCCURRENCE_ID);
        assertThat(tile.summary().plannedCount()).isEqualTo(PAID_OCCURRENCES.size() + PENDING_OCCURRENCES.size());
        assertThat(tile.summary().remainingCount()).isEqualTo(PENDING_OCCURRENCES.size());
        assertThat(tile.summary().remainingAmount()).isEqualByComparingTo(REMAINING_FIXED_PAY_CYCLE);
        assertThat(tile.summary().paidCount()).isEqualTo(PAID_OCCURRENCES.size());
    }

    private static List<Long> ids(List<FixedOccurrenceRowDto> rows) {
        return rows.stream().map(FixedOccurrenceRowDto::occurrenceId).toList();
    }

    private static FixedPaymentOccurrence occurrence(long id, FixedPayment fp, OccurrenceStatus status,
                                                     BigDecimal amount, LocalDate dueDate) {
        return FixedPaymentOccurrence.builder()
                .id(id).fixedPayment(fp).status(status).expectedAmount(amount).dueDate(dueDate).build();
    }
}
