package com.mikeshaggy.backend.regression;

import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator.PeriodWindow;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator.ProjectionInput;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.transaction.domain.Transaction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Synthetic fixture pinning the legacy forecast/period-resolver behaviour audited on
 * 2026-09-14. All identifiers, titles and amounts below are fabricated for this test
 * fixture — they do not correspond to any real user, wallet or transaction data.
 */
public final class September2026Fixture {

    private September2026Fixture() {
    }

    // Identity (synthetic)
    public static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final Integer SALARY_WALLET_ID = 1; // "FX_SALARY_WALLET"
    public static final Integer SAVINGS_WALLET_ID = 2; // "FX_SAVINGS_WALLET"
    public static final Integer ANCHOR_CATEGORY_ID = 100; // generic "salary" category (isCycleAnchor)
    public static final String SALARY_WALLET_NAME = "FX_SALARY_WALLET";

    // Audit date / clock
    public static final LocalDate TODAY = LocalDate.of(2026, 9, 14);
    public static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    // Anchors (ascending) and derived legacy boundaries
    public static final List<LocalDate> ANCHOR_DATES = List.of(
            LocalDate.of(2025, 11, 10),
            LocalDate.of(2025, 12, 10),
            LocalDate.of(2026, 1, 9),
            LocalDate.of(2026, 2, 10),
            LocalDate.of(2026, 3, 10),
            LocalDate.of(2026, 4, 10),
            LocalDate.of(2026, 5, 8),
            LocalDate.of(2026, 6, 10),
            LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 8, 10),
            LocalDate.of(2026, 9, 9));

    public static final LocalDate CURRENT_CYCLE_START = LocalDate.of(2026, 9, 9);
    public static final LocalDate PREVIOUS_CYCLE_START = LocalDate.of(2026, 8, 10);
    public static final LocalDate LEGACY_CURRENT_BILLING_END = LocalDate.of(2026, 10, 9); // start + 1 month (legacy)
    public static final LocalDate LEGACY_CURRENT_CYCLE_END = LocalDate.of(2026, 10, 8); // billingEnd - 1
    public static final LocalDate LAST_CYCLE_START = LocalDate.of(2026, 8, 10);
    public static final LocalDate LAST_CYCLE_END = LocalDate.of(2026, 9, 8);
    public static final LocalDate MONTH_START = LocalDate.of(2026, 9, 1);
    public static final LocalDate MONTH_END = LocalDate.of(2026, 9, 30);

    /** Legacy resolver shape (flag off): the open cycle ends "today" and has no state. */
    public static final PeriodDto PAY_CYCLE_PERIOD = PeriodDto.of(
            LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 9), PeriodType.PAY_CYCLE);
    public static final PeriodDto LAST_PAY_CYCLE_PERIOD = PeriodDto.of(
            LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10), PeriodType.LAST_PAY_CYCLE);
    /** pay-cycle-v2 shape (Stage 1.5): the whole open cycle Sep 9 → Oct 8, next salary expected Oct 9, salary wallet. */
    public static final LocalDate EXPECTED_NEXT_PAYDAY = LocalDate.of(2026, 10, 9);
    public static final PeriodDto PAY_CYCLE_PERIOD_V2 = new PeriodDto(
            CURRENT_CYCLE_START, LEGACY_CURRENT_CYCLE_END, EXPECTED_NEXT_PAYDAY, PeriodType.PAY_CYCLE,
            CycleState.OPEN, EXPECTED_NEXT_PAYDAY, true);
    public static final PeriodDto LAST_PAY_CYCLE_PERIOD_V2 = new PeriodDto(
            LAST_CYCLE_START, LAST_CYCLE_END, CURRENT_CYCLE_START, PeriodType.LAST_PAY_CYCLE,
            CycleState.CLOSED, null, true);
    /** The savings wallet is not the salary wallet: pay-cycle-v2 answers with the calendar month typed MONTHLY. */
    public static final PeriodDto SAVINGS_WALLET_MONTHLY_PERIOD = new PeriodDto(
            MONTH_START, MONTH_END, LocalDate.of(2026, 10, 1), PeriodType.MONTHLY, null, null, false);
    public static final PeriodDto MONTHLY_PERIOD = PeriodDto.of(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1), PeriodType.MONTHLY);
    public static final PeriodDto PREVIOUS_MONTH_PERIOD = PeriodDto.of(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1), PeriodType.MONTHLY);

    // Audit-date inputs (salary wallet, 2026-09-14)
    public static final BigDecimal SALARY_WALLET_BALANCE = new BigDecimal("5896.89");
    public static final BigDecimal SAVINGS_WALLET_BALANCE = new BigDecimal("460.01");
    public static final BigDecimal SALARY_AMOUNT = new BigDecimal("9444.81"); // booked on CURRENT_CYCLE_START in ANCHOR_CATEGORY_ID
    public static final BigDecimal INCOME_FOR_PERIOD = new BigDecimal("9444.81"); // identical for PAY_CYCLE and MONTHLY
    public static final BigDecimal EXPENSES_TO_DATE = new BigDecimal("3547.92");
    public static final BigDecimal VARIABLE_EXPENSES_TO_DATE = new BigDecimal("1552.02");
    public static final BigDecimal LINKED_FIXED_EXPENSES_TO_DATE = new BigDecimal("1995.90"); // linked transactions dated Sep 9-14
    public static final BigDecimal FIXED_PAID_ACTUAL_PAY_CYCLE = new BigDecimal("1995.90"); // PAID occurrences due Sep 9-Oct 8, actual amounts
    public static final BigDecimal REMAINING_FIXED_PAY_CYCLE = new BigDecimal("794.27");
    public static final BigDecimal REMAINING_FIXED_MONTHLY = new BigDecimal("181.98");
    public static final List<BigDecimal> DAILY_VARIABLE_SEP_9_TO_14 = List.of(
            new BigDecimal("94.98"), new BigDecimal("335.64"), new BigDecimal("361.64"),
            new BigDecimal("316.76"), new BigDecimal("443.00"), new BigDecimal("0.00")); // sums to 1552.02

    // One-off
    public static final String ONE_OFF_TITLE = "FX_REPAYMENT";
    public static final BigDecimal ONE_OFF_AMOUNT = new BigDecimal("300.00");
    public static final LocalDate ONE_OFF_DATE = LocalDate.of(2026, 9, 13);
    public static final String ONE_OFF_CATEGORY = "misc";

    public record FixedOccurrence(
            String title, String category, BigDecimal plannedAmount, BigDecimal paidAmount,
            LocalDate dueDate, boolean paid) {
    }

    public static final List<FixedOccurrence> PAID_OCCURRENCES = List.of(
            new FixedOccurrence("FX_RENT", "rent", new BigDecimal("1900.00"), new BigDecimal("1879.91"),
                    LocalDate.of(2026, 9, 10), true),
            new FixedOccurrence("FX_SUB_C", "subscriptions", new BigDecimal("89.00"), new BigDecimal("89.00"),
                    LocalDate.of(2026, 9, 12), true),
            new FixedOccurrence("FX_SUB_D", "subscriptions", new BigDecimal("26.99"), new BigDecimal("26.99"),
                    LocalDate.of(2026, 9, 12), true)); // paid sum 1995.90

    public static final List<FixedOccurrence> PENDING_OCCURRENCES = List.of(
            new FixedOccurrence("FX_UTILITY_A", "utilities", new BigDecimal("76.98"), null,
                    LocalDate.of(2026, 9, 20), false),
            new FixedOccurrence("FX_SUB_A", "subscriptions", new BigDecimal("105.00"), null,
                    LocalDate.of(2026, 9, 25), false),
            new FixedOccurrence("FX_MEMBERSHIP", "membership", new BigDecimal("169.00"), null,
                    LocalDate.of(2026, 10, 1), false),
            new FixedOccurrence("FX_SUB_B", "subscriptions", new BigDecimal("34.99"), null,
                    LocalDate.of(2026, 10, 6), false),
            new FixedOccurrence("FX_INSTALMENT", "instalments", new BigDecimal("408.30"), null,
                    LocalDate.of(2026, 10, 8), false)); // sum 794.27; due <= Sep 30: 181.98; beyond: 612.29

    // Last closed cycle (Aug 10 - Sep 8)
    public static final BigDecimal LAST_CYCLE_SPEND = new BigDecimal("5761.70");
    public static final BigDecimal LAST_CYCLE_INCOME = new BigDecimal("1736.52");
    public static final BigDecimal LAST_CYCLE_VARIABLE_PER_DAY = new BigDecimal("120.02");

    // Dashboard comparison windows on the audit date (aggregate totals only)
    public static final BigDecimal COMPARE_EXPENSES_PAY_CYCLE = new BigDecimal("3417.69"); // Aug 10-15, same elapsed days -> +130.23 / +3.81 %
    public static final BigDecimal COMPARE_INCOME_PAY_CYCLE = new BigDecimal("1736.52"); // Aug 10-15
    public static final BigDecimal COMPARE_EXPENSES_MONTHLY = new BigDecimal("4918.14"); // Aug 1-14 -> -1370.22 / -27.86 %

    // Expected legacy outputs (documented bug)
    public static final BigDecimal LEGACY_PAY_CYCLE_SAFE_TO_SPEND = new BigDecimal("-1105.46");
    public static final BigDecimal LEGACY_PAY_CYCLE_SAFE_PER_DAY = new BigDecimal("-46.06");
    public static final BigDecimal LEGACY_PAY_CYCLE_PROJECTED_END = new BigDecimal("-1105.46");
    public static final BigDecimal LEGACY_MONTHLY_SAFE_TO_SPEND = new BigDecimal("3941.15");
    public static final BigDecimal LEGACY_MONTHLY_SAFE_PER_DAY = new BigDecimal("246.32");
    public static final BigDecimal LEGACY_MONTHLY_PROJECTED_END = new BigDecimal("3941.15");

    public static ProjectionInput payCycleProjectionInput() {
        return new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 8)),
                TODAY,
                SALARY_WALLET_BALANCE,
                INCOME_FOR_PERIOD,
                EXPENSES_TO_DATE,
                VARIABLE_EXPENSES_TO_DATE,
                REMAINING_FIXED_PAY_CYCLE);
    }

    public static ProjectionInput monthlyProjectionInput() {
        return new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
                TODAY,
                SALARY_WALLET_BALANCE,
                INCOME_FOR_PERIOD,
                EXPENSES_TO_DATE,
                VARIABLE_EXPENSES_TO_DATE,
                REMAINING_FIXED_MONTHLY);
    }

    public static ProjectionInput projectionInput(LocalDate start, LocalDate end, BigDecimal remainingFixed) {
        return new ProjectionInput(
                new PeriodWindow(start, end),
                TODAY,
                SALARY_WALLET_BALANCE,
                INCOME_FOR_PERIOD,
                EXPENSES_TO_DATE,
                VARIABLE_EXPENSES_TO_DATE,
                remainingFixed);
    }

    public static List<Transaction> anchorTransactionsDescending(int limit) {
        List<LocalDate> descending = new ArrayList<>(ANCHOR_DATES);
        Collections.reverse(descending);
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < limit && i < descending.size(); i++) {
            transactions.add(Transaction.builder().transactionDate(descending.get(i)).build());
        }
        return transactions;
    }
}
