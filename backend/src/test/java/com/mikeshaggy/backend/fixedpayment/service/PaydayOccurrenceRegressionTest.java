package com.mikeshaggy.backend.fixedpayment.service;

import static com.mikeshaggy.backend.regression.September2026Fixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
import com.mikeshaggy.backend.common.period.InclusiveDateRange;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket;
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
    // Stage 3.4: a next-cycle occurrence, generated ahead (monthly horizon = today + 2 months), due after payday
    private static final LocalDate NEXT_CYCLE_DUE = LocalDate.of(2026, 10, 20);
    private static final BigDecimal NEXT_CYCLE_AMOUNT = new BigDecimal("77.00");
    private static final long NEXT_CYCLE_OCCURRENCE_ID = 901L;
    /** The cycle expected after the current one: Oct 9 → Nov 9 (rule: 10th, previous business day; Nov 10 is a Tuesday). */
    private static final InclusiveDateRange EXPECTED_NEXT_CYCLE = new InclusiveDateRange(PAYDAY_DUE, LocalDate.of(2026, 11, 9));

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
        all.add(occurrence(NEXT_CYCLE_OCCURRENCE_ID, fixedPayment, OccurrenceStatus.PENDING, NEXT_CYCLE_AMOUNT, NEXT_CYCLE_DUE));
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

        // Stage 3.4: the bucket is computed against the SAME extended window (cycleEnd = today, not the stale
        // period.endDate()) on both live paths, so the payday-due row reads DUE_SOON, never AFTER_PAYDAY, while
        // the cycle is still open awaiting its salary.
        for (FixedTransactionsTileDto tile : List.of(dashboard, endpoint)) {
            FixedOccurrenceRowDto paydayRow = tile.upcoming().stream()
                    .filter(r -> r.occurrenceId() == PAYDAY_OCCURRENCE_ID).findFirst().orElseThrow();
            assertThat(paydayRow.bucket()).isEqualTo(FixedOccurrenceBucket.DUE_SOON);
            assertThat(tile.upcoming()).extracting(FixedOccurrenceRowDto::bucket)
                    .doesNotContain(FixedOccurrenceBucket.AFTER_PAYDAY);
        }
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
        when(payCycleService.expectedNextCycle(USER_ID)).thenReturn(Optional.of(
                new InclusiveDateRange(newExpected, LocalDate.of(2026, 12, 9))));
        PeriodDto newCycle = new PeriodDto(PAYDAY_DUE, newExpected.minusDays(1), newExpected, PeriodType.PAY_CYCLE,
                CycleState.OPEN, newExpected, true);

        FixedTransactionsTileDto endpoint = service.getFixedPaymentsTileDataForCurrentPeriod(SALARY_WALLET_ID, USER_ID);
        FixedTransactionsTileDto dashboard = service.getFixedPaymentsTileData(newCycle, salaryWallet, USER_ID, PAYDAY_DUE);

        for (FixedTransactionsTileDto tile : List.of(endpoint, dashboard)) {
            assertThat(tile.periodStart()).isEqualTo(PAYDAY_DUE);
            assertThat(ids(tile.upcoming())).containsExactly(PAYDAY_OCCURRENCE_ID, NEXT_CYCLE_OCCURRENCE_ID);
            assertThat(tile.upcoming().getFirst().daysDelta()).isZero();
            assertThat(tile.progress().nextDueDate()).isEqualTo(PAYDAY_DUE);
            assertThat(tile.summary().remainingAmount()).isEqualByComparingTo(PAYDAY_AMOUNT.add(NEXT_CYCLE_AMOUNT));
            // yesterday's "after payday" rows are today's committed rows; the new horizon (Nov 9 → Dec 9) holds nothing
            assertThat(tile.afterPayday()).isEmpty();
            // nothing from the previous cycle leaks into the new one
            assertThat(tile.paid()).isEmpty();
        }
    }

    @Test
    void fixtureBuckets_areCycleRelative_andNothingIsAfterPayday() {
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.open(USER_ID, SALARY_WALLET_ID, CURRENT_CYCLE_START, EXPECTED_NEXT_PAYDAY)));

        FixedTransactionsTileDto dashboard = fixedPaymentDashboardService
                .getFixedPaymentsTileData(PAY_CYCLE_PERIOD_V2, salaryWallet, USER_ID, TODAY);
        FixedTransactionsTileDto endpoint = fixedPaymentDashboardService
                .getFixedPaymentsTileDataForCurrentPeriod(SALARY_WALLET_ID, USER_ID);

        for (FixedTransactionsTileDto tile : List.of(dashboard, endpoint)) {
            // viewed Sep 14: FX_UTILITY_A (Sep 20) is due soon; Sep 25, Oct 1, Oct 6 and the Oct 8 instalment are
            // later this cycle — the Oct 8 row is NOT "next month", and no committed row is after payday
            assertThat(tile.upcoming()).extracting(FixedOccurrenceRowDto::dueDate, FixedOccurrenceRowDto::bucket)
                    .containsExactly(
                            tuple(LocalDate.of(2026, 9, 20), FixedOccurrenceBucket.DUE_SOON),
                            tuple(LocalDate.of(2026, 9, 25), FixedOccurrenceBucket.LATER_THIS_CYCLE),
                            tuple(LocalDate.of(2026, 10, 1), FixedOccurrenceBucket.LATER_THIS_CYCLE),
                            tuple(LocalDate.of(2026, 10, 6), FixedOccurrenceBucket.LATER_THIS_CYCLE),
                            tuple(LAST_CYCLE_DAY, FixedOccurrenceBucket.LATER_THIS_CYCLE));
            assertThat(tile.paid()).extracting(FixedOccurrenceRowDto::bucket)
                    .containsOnly(FixedOccurrenceBucket.PAID_THIS_CYCLE);
            assertThat(tile.overdue()).isEmpty();
            assertThat(tile.upcoming()).extracting(FixedOccurrenceRowDto::bucket)
                    .doesNotContain(FixedOccurrenceBucket.AFTER_PAYDAY);
            assertThat(ids(tile.upcoming())).doesNotContain(PAYDAY_OCCURRENCE_ID);
        }
        // both live paths hand out the same verdict for the same occurrence
        assertThat(buckets(endpoint.upcoming())).isEqualTo(buckets(dashboard.upcoming()));
        assertThat(buckets(endpoint.paid())).isEqualTo(buckets(dashboard.paid()));
    }

    /**
     * Stage 3.4 closing gap: the page's data source ({@code /api/fixed-payments/tile}) lists the next cycle's
     * obligations under "After payday" — display only. All at once: Oct 8 is committed and LATER_THIS_CYCLE; the
     * Oct 9 payday row and the Oct 20 row are AFTER_PAYDAY, visible, and absent from every committed figure.
     * Fails if AFTER_PAYDAY becomes unreachable from the page again.
     */
    @Test
    void pagePath_listsNextCycleRowsAsAfterPayday_withoutTouchingCommittedTotals() {
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.open(USER_ID, SALARY_WALLET_ID, CURRENT_CYCLE_START, EXPECTED_NEXT_PAYDAY)));
        when(payCycleService.expectedNextCycle(USER_ID)).thenReturn(Optional.of(EXPECTED_NEXT_CYCLE));
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

        FixedTransactionsTileDto page = fixedPaymentDashboardService
                .getFixedPaymentsTileDataForCurrentPeriod(SALARY_WALLET_ID, USER_ID);
        FixedTransactionsTileDto dashboard = fixedPaymentDashboardService
                .getFixedPaymentsTileData(PAY_CYCLE_PERIOD_V2, salaryWallet, USER_ID, TODAY);
        SpendingProjectionDto forecast = projectionService
                .getSpendingProjection(SALARY_WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null);

        // expected payday Oct 9; Oct 8 committed and later-this-cycle
        assertThat(page.expectedPaydayDate()).isEqualTo(EXPECTED_NEXT_PAYDAY);
        assertThat(page.upcoming()).extracting(FixedOccurrenceRowDto::dueDate, FixedOccurrenceRowDto::bucket)
                .contains(tuple(LAST_CYCLE_DAY, FixedOccurrenceBucket.LATER_THIS_CYCLE));
        assertThat(page.summary().remainingAmount()).isEqualByComparingTo(REMAINING_FIXED_PAY_CYCLE); // includes the 408.30 due Oct 8

        // Oct 9 (payday) and Oct 20 are not this cycle's obligations …
        for (List<FixedOccurrenceRowDto> committed : List.of(page.overdue(), page.upcoming(), page.paid())) {
            assertThat(ids(committed)).doesNotContain(PAYDAY_OCCURRENCE_ID, NEXT_CYCLE_OCCURRENCE_ID);
        }
        // … but the page can show them, classified AFTER_PAYDAY, through the display horizon
        assertThat(page.afterPaydayEnd()).isEqualTo(EXPECTED_NEXT_CYCLE.endDate());
        assertThat(page.afterPayday()).extracting(FixedOccurrenceRowDto::occurrenceId, FixedOccurrenceRowDto::dueDate,
                        FixedOccurrenceRowDto::bucket)
                .containsExactly(
                        tuple(PAYDAY_OCCURRENCE_ID, PAYDAY_DUE, FixedOccurrenceBucket.AFTER_PAYDAY),
                        tuple(NEXT_CYCLE_OCCURRENCE_ID, NEXT_CYCLE_DUE, FixedOccurrenceBucket.AFTER_PAYDAY));

        // and no committed figure moved: page == dashboard (which carries no horizon) == forecast
        assertThat(page.summary()).isEqualTo(dashboard.summary());
        assertThat(page.summary().plannedCount()).isEqualTo(PAID_OCCURRENCES.size() + PENDING_OCCURRENCES.size());
        assertThat(page.summary().plannedAmount()).isEqualByComparingTo("2810.26");
        assertThat(page.summary().paidAmount()).isEqualByComparingTo(FIXED_PAID_ACTUAL_PAY_CYCLE);
        assertThat(page.summary().plannedPaidAmount()).isEqualByComparingTo("2015.99");
        assertThat(page.summary().remainingAmount()).isEqualByComparingTo(REMAINING_FIXED_PAY_CYCLE);
        assertThat(page.balanceAfterFixed()).isEqualByComparingTo(dashboard.balanceAfterFixed())
                .isEqualByComparingTo(SALARY_WALLET_BALANCE.subtract(REMAINING_FIXED_PAY_CYCLE));
        assertThat(page.progress().totalCount()).isEqualTo(dashboard.progress().totalCount());
        assertThat(page.riskIndicator()).isEqualTo(dashboard.riskIndicator());
        assertThat(dashboard.afterPayday()).isEmpty();
        assertThat(forecast.remainingFixedPayments()).isEqualByComparingTo(REMAINING_FIXED_PAY_CYCLE);
    }

    @Test
    void awaitingSalary_afterPaydayHorizonStartsAfterTheExtendedWindow() {
        // Oct 9, expected payday, no salary yet: the committed window runs through today (T5), so "after payday"
        // starts Oct 10 — the Oct 9 payday row is committed (as in the AWAITING test above), the Oct 20 row is
        // still display-only and never counted
        LocalDate today = PAYDAY_DUE;
        Clock paydayNoSalary = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        FixedPaymentDashboardService service = new FixedPaymentDashboardService(
                fixedPaymentRepository, occurrenceRepository, new FixedPaymentTileAssembler(paydayNoSalary),
                walletService, transactionService, periodService, payCycleService, paydayNoSalary);
        when(payCycleService.current(USER_ID)).thenReturn(Optional.of(
                PayCycle.awaitingSalary(USER_ID, SALARY_WALLET_ID, CURRENT_CYCLE_START, EXPECTED_NEXT_PAYDAY)));
        when(payCycleService.expectedNextCycle(USER_ID)).thenReturn(Optional.of(EXPECTED_NEXT_CYCLE));

        FixedTransactionsTileDto page = service.getFixedPaymentsTileDataForCurrentPeriod(SALARY_WALLET_ID, USER_ID);

        assertThat(page.periodEnd()).isEqualTo(today);
        assertThat(ids(page.upcoming())).contains(PAYDAY_OCCURRENCE_ID).doesNotContain(NEXT_CYCLE_OCCURRENCE_ID);
        assertThat(page.afterPayday()).extracting(FixedOccurrenceRowDto::occurrenceId, FixedOccurrenceRowDto::bucket)
                .containsExactly(tuple(NEXT_CYCLE_OCCURRENCE_ID, FixedOccurrenceBucket.AFTER_PAYDAY));
        // committed = the fixture rows + the payday row; the Oct 20 row is not counted anywhere
        assertThat(page.summary().plannedCount()).isEqualTo(PAID_OCCURRENCES.size() + PENDING_OCCURRENCES.size() + 1);
        assertThat(page.summary().plannedAmount()).isEqualByComparingTo(new BigDecimal("2810.26").add(PAYDAY_AMOUNT));
        assertThat(page.summary().remainingAmount()).isEqualByComparingTo(PAYDAY_AMOUNT); // only the row due today is still "remaining" as of Oct 9
    }

    private static List<FixedOccurrenceBucket> buckets(List<FixedOccurrenceRowDto> rows) {
        return rows.stream().map(FixedOccurrenceRowDto::bucket).toList();
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
        // Stage 3.3: "Paid" is the actual money out (1,879.91 + 89 + 26.99), the plan stays separately available.
        // Stage 3.5: it equals the forecast's LINKED_FIXED_EXPENSES_TO_DATE only because every fixture obligation is
        // paid inside its own cycle — the two are different metrics (CrossCycleFixedPaidRegressionTest).
        assertThat(tile.summary().paidAmount()).isEqualByComparingTo(FIXED_PAID_ACTUAL_PAY_CYCLE);
        assertThat(tile.summary().plannedPaidAmount()).isEqualByComparingTo("2015.99");
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
