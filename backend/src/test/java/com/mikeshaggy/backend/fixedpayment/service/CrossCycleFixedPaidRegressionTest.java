package com.mikeshaggy.backend.fixedpayment.service;

import static com.mikeshaggy.backend.regression.September2026Fixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
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
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Stage 3.5 decision B — the fixed-payments "Paid" total and the forecast's {@code linkedFixedExpensesToDate}
 * are two different metrics and are pinned as such:
 * <ul>
 *   <li>tile / dashboard / page {@code paidAmount}: PAID occurrences whose <em>due date</em> is in the cycle
 *       (obligation view, actual amounts);</li>
 *   <li>forecast {@code linkedFixedExpensesToDate}: linked transactions <em>dated</em> in {@code [cycle start, today]}
 *       (ledger view, {@code = expensesToDate − variableExpensesToDate}).</li>
 * </ul>
 * They coincide only when every obligation is paid inside its own cycle. This test pays one current-cycle
 * obligation before the cycle starts and one previous-cycle obligation after the current cycle has begun; the
 * ledger stubs honour transaction dates exactly as the repository sums would, and the occurrence stub honours
 * due-date bounds, so a change that silently forces one metric onto the other fails here.
 */
@ExtendWith(MockitoExtension.class)
class CrossCycleFixedPaidRegressionTest {

    // current cycle Sep 9 → Oct 8 (expected payday Oct 9); previous cycle Aug 10 → Sep 8; today Sep 14
    private static final LocalDate RENT_DUE = LocalDate.of(2026, 9, 10);      // current cycle
    private static final LocalDate RENT_PAID = LocalDate.of(2026, 9, 8);      // paid early, in the previous cycle
    private static final LocalDate INSURANCE_DUE = LocalDate.of(2026, 9, 5);  // previous cycle
    private static final LocalDate INSURANCE_PAID = LocalDate.of(2026, 9, 11); // paid late, in the current cycle
    private static final LocalDate GROCERIES = LocalDate.of(2026, 9, 12);     // a variable (unlinked) expense
    private static final LocalDate UTILITY_DUE = LocalDate.of(2026, 9, 20);   // still pending this cycle

    private static final BigDecimal RENT = new BigDecimal("1900.00");
    private static final BigDecimal INSURANCE = new BigDecimal("250.00");
    private static final BigDecimal VARIABLE = new BigDecimal("100.00");
    private static final BigDecimal UTILITY = new BigDecimal("76.98");

    @Mock private FixedPaymentRepository fixedPaymentRepository;
    @Mock private FixedPaymentOccurrenceRepository occurrenceRepository;
    @Mock private WalletService walletService;
    @Mock private TransactionService transactionService;
    @Mock private PeriodService periodService;
    @Mock private PayCycleService payCycleService;
    @Mock private AnalyticsTransactionQueryService transactionQueryService;

    private FixedPaymentDashboardService fixedPaymentDashboardService;
    private SpendingProjectionService projectionService;
    private Wallet salaryWallet;

    private record LedgerEntry(LocalDate date, BigDecimal amount, boolean linked) {}

    private final List<LedgerEntry> ledger = List.of(
            new LedgerEntry(RENT_PAID, RENT, true),
            new LedgerEntry(INSURANCE_PAID, INSURANCE, true),
            new LedgerEntry(GROCERIES, VARIABLE, false));

    @BeforeEach
    void setUp() {
        salaryWallet = Wallet.builder().id(SALARY_WALLET_ID).name(SALARY_WALLET_NAME)
                .balance(SALARY_WALLET_BALANCE).build();
        Category category = Category.builder().id(200).name("fixed").type(CategoryType.EXPENSE).emoji("F").build();
        FixedPayment fixedPayment = FixedPayment.builder()
                .id(1).wallet(salaryWallet).category(category).title("FX").amount(BigDecimal.ONE)
                .anchorDate(LocalDate.of(2026, 1, 1)).cycle(Cycle.MONTHLY).activeFrom(LocalDate.of(2026, 1, 1))
                .build();

        FixedPaymentOccurrence rent = paid(1L, fixedPayment, RENT_DUE, RENT, 10L, RENT_PAID);
        FixedPaymentOccurrence insurance = paid(2L, fixedPayment, INSURANCE_DUE, INSURANCE, 11L, INSURANCE_PAID);
        FixedPaymentOccurrence utility = FixedPaymentOccurrence.builder()
                .id(3L).fixedPayment(fixedPayment).status(OccurrenceStatus.PENDING).expectedAmount(UTILITY)
                .dueDate(UTILITY_DUE).build();
        List<FixedPaymentOccurrence> occurrences = List.of(rent, insurance, utility);

        lenient().when(walletService.getWalletEntityByIdForUser(SALARY_WALLET_ID, USER_ID)).thenReturn(salaryWallet);
        lenient().when(fixedPaymentRepository.findAllActiveInPeriodByWalletIdAndUserId(
                        eq(SALARY_WALLET_ID), eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(fixedPayment));
        // occurrences are selected by DUE DATE, exactly as the JPQL BETWEEN would
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
        // ledger sums are selected by TRANSACTION DATE, exactly as the repository sums would
        lenient().when(transactionQueryService.sum(eq(SALARY_WALLET_ID), eq(USER_ID), any(), any(), eq(CategoryType.INCOME)))
                .thenReturn(INCOME_FOR_PERIOD);
        lenient().when(transactionQueryService.sum(eq(SALARY_WALLET_ID), eq(USER_ID), any(), any(), eq(CategoryType.EXPENSE)))
                .thenAnswer(inv -> ledgerSum(inv.getArgument(2), inv.getArgument(3), false));
        lenient().when(transactionQueryService.sumUnlinked(eq(SALARY_WALLET_ID), eq(USER_ID), any(), any(), eq(CategoryType.EXPENSE)))
                .thenAnswer(inv -> ledgerSum(inv.getArgument(2), inv.getArgument(3), true));

        fixedPaymentDashboardService = new FixedPaymentDashboardService(
                fixedPaymentRepository, occurrenceRepository, new FixedPaymentTileAssembler(CLOCK),
                walletService, transactionService, periodService, payCycleService, CLOCK);
        projectionService = new SpendingProjectionService(
                transactionQueryService, walletService, periodService, fixedPaymentDashboardService,
                new SpendingProjectionCalculator(), CLOCK);
    }

    @Test
    void currentCycle_obligationPaidBeforeTheCycleCountsAsPaid_butNotAsThisCyclesCashFlow() {
        when(periodService.resolve(PeriodType.PAY_CYCLE, SALARY_WALLET_ID, USER_ID, null, null))
                .thenReturn(PAY_CYCLE_PERIOD_V2);

        FixedTransactionsTileDto tile = fixedPaymentDashboardService
                .getFixedPaymentsTileData(PAY_CYCLE_PERIOD_V2, salaryWallet, USER_ID, TODAY);
        SpendingProjectionDto forecast = projectionService
                .getSpendingProjection(SALARY_WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null);

        // obligation view: the rent is due in this cycle, so it is "paid this cycle" — early, on Sep 8
        assertThat(tile.paid()).extracting(FixedOccurrenceRowDto::occurrenceId).containsExactly(1L);
        FixedOccurrenceRowDto rentRow = tile.paid().getFirst();
        assertThat(rentRow.bucket()).isEqualTo(FixedOccurrenceBucket.PAID_THIS_CYCLE);
        assertThat(rentRow.paidDate()).isEqualTo(RENT_PAID);
        assertThat(rentRow.lateDays()).isEqualTo(-2);
        assertThat(rentRow.paidOnTime()).isTrue();
        assertThat(tile.summary().paidAmount()).isEqualByComparingTo(RENT);
        assertThat(tile.summary().paidCount()).isEqualTo(1);
        // the previous-cycle insurance (due Sep 5) is not this cycle's obligation, whenever it was paid
        assertThat(tile.paid()).extracting(FixedOccurrenceRowDto::occurrenceId).doesNotContain(2L);
        assertThat(tile.summary().remainingAmount()).isEqualByComparingTo(UTILITY);

        // ledger view: only money that moved on Sep 9-14 — the insurance paid Sep 11, not the rent paid Sep 8
        assertThat(forecast.linkedFixedExpensesToDate()).isEqualByComparingTo(INSURANCE);
        assertThat(forecast.variableExpensesToDate()).isEqualByComparingTo(VARIABLE);
        assertThat(forecast.expensesToDate()).isEqualByComparingTo(INSURANCE.add(VARIABLE));
        assertThat(forecast.remainingFixedPayments()).isEqualByComparingTo(UTILITY);

        // the two metrics legitimately differ here, and the difference is exactly the cross-cycle money
        assertThat(tile.summary().paidAmount()).isNotEqualByComparingTo(forecast.linkedFixedExpensesToDate());
        assertLedgerIdentityHolds(forecast);
    }

    @Test
    void previousCycle_obligationPaidAfterTheCycleCountsAsPaid_butNotAsThatCyclesCashFlow() {
        when(periodService.resolve(PeriodType.LAST_PAY_CYCLE, SALARY_WALLET_ID, USER_ID, null, null))
                .thenReturn(LAST_PAY_CYCLE_PERIOD_V2);

        FixedTransactionsTileDto tile = fixedPaymentDashboardService
                .getFixedPaymentsTileData(LAST_PAY_CYCLE_PERIOD_V2, salaryWallet, USER_ID, TODAY);
        SpendingProjectionDto forecast = projectionService
                .getSpendingProjection(SALARY_WALLET_ID, USER_ID, PeriodType.LAST_PAY_CYCLE, null, null);

        // obligation view: the insurance was due in the previous cycle, so it is that cycle's paid item — 6 days late
        assertThat(tile.paid()).extracting(FixedOccurrenceRowDto::occurrenceId).containsExactly(2L);
        FixedOccurrenceRowDto insuranceRow = tile.paid().getFirst();
        assertThat(insuranceRow.bucket()).isEqualTo(FixedOccurrenceBucket.PAID_THIS_CYCLE);
        assertThat(insuranceRow.paidDate()).isEqualTo(INSURANCE_PAID);
        assertThat(insuranceRow.lateDays()).isEqualTo(6);
        assertThat(insuranceRow.paidOnTime()).isFalse();
        assertThat(tile.summary().paidAmount()).isEqualByComparingTo(INSURANCE);

        // ledger view of Aug 10 - Sep 8: the rent paid Sep 8 moved in that cycle, the insurance paid Sep 11 did not
        assertThat(forecast.projectionAvailable()).isFalse(); // closed cycle: actuals only
        assertThat(forecast.linkedFixedExpensesToDate()).isEqualByComparingTo(RENT);
        assertThat(forecast.expensesToDate()).isEqualByComparingTo(RENT);

        assertThat(tile.summary().paidAmount()).isNotEqualByComparingTo(forecast.linkedFixedExpensesToDate());
        assertLedgerIdentityHolds(forecast);
    }

    @Test
    void acrossBothCycles_eachMetricCountsEveryObligationExactlyOnce() {
        when(periodService.resolve(PeriodType.PAY_CYCLE, SALARY_WALLET_ID, USER_ID, null, null))
                .thenReturn(PAY_CYCLE_PERIOD_V2);
        when(periodService.resolve(PeriodType.LAST_PAY_CYCLE, SALARY_WALLET_ID, USER_ID, null, null))
                .thenReturn(LAST_PAY_CYCLE_PERIOD_V2);

        BigDecimal paidByDueCycle = fixedPaymentDashboardService
                .getFixedPaymentsTileData(PAY_CYCLE_PERIOD_V2, salaryWallet, USER_ID, TODAY).summary().paidAmount()
                .add(fixedPaymentDashboardService
                        .getFixedPaymentsTileData(LAST_PAY_CYCLE_PERIOD_V2, salaryWallet, USER_ID, TODAY).summary().paidAmount());
        BigDecimal paidByTransactionDate = projectionService
                .getSpendingProjection(SALARY_WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null).linkedFixedExpensesToDate()
                .add(projectionService
                        .getSpendingProjection(SALARY_WALLET_ID, USER_ID, PeriodType.LAST_PAY_CYCLE, null, null).linkedFixedExpensesToDate());

        // neither view double-counts or drops an obligation; they only disagree on WHICH cycle owns it
        assertThat(paidByDueCycle).isEqualByComparingTo(RENT.add(INSURANCE));
        assertThat(paidByTransactionDate).isEqualByComparingTo(RENT.add(INSURANCE));
    }

    /** {@code expensesToDate = variableExpensesToDate + linkedFixedExpensesToDate} — the accounting the forecast relies on. */
    private static void assertLedgerIdentityHolds(SpendingProjectionDto forecast) {
        assertThat(forecast.variableExpensesToDate().add(forecast.linkedFixedExpensesToDate()))
                .isEqualByComparingTo(forecast.expensesToDate());
    }

    private BigDecimal ledgerSum(LocalDate from, LocalDate to, boolean unlinkedOnly) {
        return ledger.stream()
                .filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to))
                .filter(e -> !unlinkedOnly || !e.linked())
                .map(LedgerEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static FixedPaymentOccurrence paid(long id, FixedPayment fp, LocalDate dueDate, BigDecimal amount,
                                               long transactionId, LocalDate transactionDate) {
        FixedPaymentOccurrence occ = FixedPaymentOccurrence.builder()
                .id(id).fixedPayment(fp).status(OccurrenceStatus.PAID).expectedAmount(amount).dueDate(dueDate).build();
        occ.setPaidAmount(amount);
        occ.setPaidAt(transactionDate.atStartOfDay());
        occ.setTransaction(Transaction.builder().id(transactionId).amount(amount).transactionDate(transactionDate).build());
        return occ;
    }
}
