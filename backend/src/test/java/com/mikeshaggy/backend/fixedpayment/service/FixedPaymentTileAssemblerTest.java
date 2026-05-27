package com.mikeshaggy.backend.fixedpayment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FixedPaymentTileAssemblerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 11);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    private final FixedPaymentTileAssembler assembler = new FixedPaymentTileAssembler(FIXED_CLOCK);

    private static final PeriodDto PERIOD =
            new PeriodDto(
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    LocalDate.of(2026, 4, 5),
                    PeriodType.PAY_CYCLE);

    private FixedPayment fixedPayment(Integer id, String title, String amount) {
        Wallet wallet = Wallet.builder().id(1).name("Main").build();
        Category category = Category.builder().id(10).name("Bills").emoji("📄").build();
        return FixedPayment.builder()
                .id(id)
                .wallet(wallet)
                .category(category)
                .title(title)
                .amount(new BigDecimal(amount))
                .anchorDate(LocalDate.of(2026, 1, 1))
                .cycle(Cycle.MONTHLY)
                .activeFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    private FixedPaymentOccurrence occurrence(
            Long id, FixedPayment fp, OccurrenceStatus status, String expectedAmount, LocalDate dueDate) {
        FixedPaymentOccurrence occ =
                FixedPaymentOccurrence.builder()
                        .id(id)
                        .fixedPayment(fp)
                        .status(status)
                        .expectedAmount(new BigDecimal(expectedAmount))
                        .dueDate(dueDate)
                        .build();
        if (status == OccurrenceStatus.PAID) {
            occ.setPaidAmount(new BigDecimal(expectedAmount));
            occ.setPaidAt(LocalDateTime.of(2026, 3, 5, 10, 0));
        }
        return occ;
    }


    @Nested
    class AssembleEmpty {

        @Test
        void returnsZeroedSummaryWithBalance() {
            // given
            // when
            FixedTransactionsTileDto result = assembler.assembleEmpty(PERIOD, new BigDecimal("5000.00"));

            // then
            assertThat(result.periodStart()).isEqualTo(PERIOD.startDate());
            assertThat(result.periodEnd()).isEqualTo(PERIOD.endDate());
            assertThat(result.billingEndDate()).isEqualTo(PERIOD.billingEndDate());
            assertThat(result.currentBalance()).isEqualByComparingTo("5000.00");
            assertThat(result.balanceAfterFixed()).isEqualByComparingTo("5000.00");
            assertThat(result.summary().plannedCount()).isZero();
            assertThat(result.summary().plannedAmount()).isEqualByComparingTo("0");
            assertThat(result.progress().paidCount()).isZero();
            assertThat(result.progress().totalCount()).isZero();
            assertThat(result.riskIndicator().isAtRisk()).isFalse();
            assertThat(result.overdue()).isEmpty();
            assertThat(result.upcoming()).isEmpty();
            assertThat(result.paid()).isEmpty();
        }
    }

    @Nested
    class SummaryCalculation {

        @Test
        void computesPlannedPaidPendingAmountsAndCounts() {
            // given
            FixedPayment fp = fixedPayment(1, "Rent", "1500.00");

            FixedPaymentOccurrence paid =
                    occurrence(1L, fp, OccurrenceStatus.PAID, "1500.00", LocalDate.of(2026, 3, 1));
            FixedPaymentOccurrence pending =
                    occurrence(2L, fp, OccurrenceStatus.PENDING, "1500.00", LocalDate.of(2026, 3, 15));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD,
                            List.of(paid, pending),
                            List.of(),
                            new BigDecimal("4000.00"),
                            new BigDecimal("5000.00"),
                            1);

            // then
            assertThat(result.summary().plannedAmount()).isEqualByComparingTo("3000.00");
            assertThat(result.summary().plannedCount()).isEqualTo(2);
            assertThat(result.summary().paidAmount()).isEqualByComparingTo("1500.00");
            assertThat(result.summary().paidCount()).isEqualTo(1);
            assertThat(result.summary().remainingAmount()).isEqualByComparingTo("1500.00");
            assertThat(result.summary().remainingCount()).isEqualTo(1);
        }

        @Test
        void assembleWithAsOfDateUsesProvidedDateForRemainingAndUpcoming() {
            // given
            FixedPayment fp = fixedPayment(1, "Rent", "1000.00");
            FixedPaymentOccurrence beforeAsOf =
                    occurrence(1L, fp, OccurrenceStatus.PENDING, "1000.00", LocalDate.of(2026, 3, 12));
            FixedPaymentOccurrence afterAsOf =
                    occurrence(2L, fp, OccurrenceStatus.PENDING, "1000.00", LocalDate.of(2026, 3, 20));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD,
                            List.of(beforeAsOf, afterAsOf),
                            List.of(),
                            new BigDecimal("4000.00"),
                            new BigDecimal("5000.00"),
                            1,
                            LocalDate.of(2026, 3, 15));

            // then
            assertThat(result.summary().plannedAmount()).isEqualByComparingTo("2000.00");
            assertThat(result.summary().remainingAmount()).isEqualByComparingTo("1000.00");
            assertThat(result.summary().remainingCount()).isEqualTo(1);
            assertThat(result.upcoming()).hasSize(1);
            assertThat(result.upcoming().getFirst().dueDate()).isEqualTo(LocalDate.of(2026, 3, 20));
            assertThat(result.upcoming().getFirst().daysDelta()).isEqualTo(5);
            assertThat(result.balanceAfterFixed()).isEqualByComparingTo("4000.00");
        }

        @Test
        void overdueCountAndAmountFromSeparateList() {
            // given
            FixedPayment fp = fixedPayment(1, "Insurance", "200.00");
            FixedPaymentOccurrence overdue =
                    occurrence(3L, fp, OccurrenceStatus.OVERDUE, "200.00", LocalDate.of(2026, 2, 15));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD,
                            List.of(),
                            List.of(overdue),
                            new BigDecimal("4000.00"),
                            new BigDecimal("300.00"),
                            1);

            // then
            assertThat(result.summary().overdueAmount()).isEqualByComparingTo("200.00");
            assertThat(result.summary().overdueCount()).isEqualTo(1);
        }

        @Test
        void fixedRatio_relativeToIncome() {
            // given
            FixedPayment fp = fixedPayment(1, "Rent", "1000.00");
            FixedPaymentOccurrence occ =
                    occurrence(1L, fp, OccurrenceStatus.PENDING, "1000.00", LocalDate.of(2026, 3, 15));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD,
                            List.of(occ),
                            List.of(),
                            new BigDecimal("4000.00"),
                            new BigDecimal("5000.00"),
                            1);

            // 1000 / 4000 * 100 = 25.00
            // then
            assertThat(result.summary().fixedRatio()).isEqualByComparingTo("25.00");
        }

        @Test
        void fixedRatio_nonEvenDivisionIsRoundedHalfUp() {
            // given: planned = 1, income = 3 → 1/3 * 100 = 33.333... rounds to 33.33
            FixedPayment fp = fixedPayment(1, "Subscription", "1.00");
            FixedPaymentOccurrence occ =
                    occurrence(1L, fp, OccurrenceStatus.PENDING, "1.00", LocalDate.of(2026, 3, 15));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD, List.of(occ), List.of(), new BigDecimal("3.00"),
                            new BigDecimal("5000.00"), 1);

            // then — no floating-point error, exactly 33.33
            assertThat(result.summary().fixedRatio()).isEqualByComparingTo("33.33");
        }

        @Test
        void fixedRatio_zeroIncomeReturnsZero() {
            // given
            FixedPayment fp = fixedPayment(1, "Rent", "1000.00");
            FixedPaymentOccurrence occ =
                    occurrence(1L, fp, OccurrenceStatus.PENDING, "1000.00", LocalDate.of(2026, 3, 15));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD, List.of(occ), List.of(), BigDecimal.ZERO, new BigDecimal("5000.00"), 1);

            // then
            assertThat(result.summary().fixedRatio()).isEqualByComparingTo("0");
        }
    }

    @Nested
    class RiskIndicator {

        @Test
        void balanceCoversUnpaid_notAtRisk() {
            // given
            FixedPayment fp = fixedPayment(1, "Rent", "1000.00");
            FixedPaymentOccurrence pending =
                    occurrence(1L, fp, OccurrenceStatus.PENDING, "1000.00", LocalDate.of(2026, 3, 15));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD,
                            List.of(pending),
                            List.of(),
                            new BigDecimal("4000.00"),
                            new BigDecimal("5000.00"),
                            1);

            // then
            assertThat(result.riskIndicator().isAtRisk()).isFalse();
            assertThat(result.riskIndicator().shortfallAmount()).isNull();
            assertThat(result.balanceAfterFixed()).isEqualByComparingTo("4000.00");
        }

        @Test
        void balanceInsufficientForPendingAndOverdue_atRiskWithShortfall() {
            // given
            FixedPayment fp = fixedPayment(1, "Rent", "1500.00");
            FixedPaymentOccurrence pending =
                    occurrence(1L, fp, OccurrenceStatus.PENDING, "1500.00", LocalDate.of(2026, 3, 15));
            FixedPaymentOccurrence overdue =
                    occurrence(2L, fp, OccurrenceStatus.OVERDUE, "1500.00", LocalDate.of(2026, 2, 15));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD,
                            List.of(pending),
                            List.of(overdue),
                            new BigDecimal("4000.00"),
                            new BigDecimal("2000.00"),
                            1);

            // unpaid = 1500 (pending) + 1500 (overdue) = 3000; balance 2000 - 3000 = -1000
            // then
            assertThat(result.riskIndicator().isAtRisk()).isTrue();
            assertThat(result.riskIndicator().shortfallAmount()).isEqualByComparingTo("1000.00");
            assertThat(result.balanceAfterFixed()).isEqualByComparingTo("-1000.00");
        }
    }

    @Nested
    class Progress {

        @Test
        void paidPercentageCalculation() {
            // given
            FixedPayment fp = fixedPayment(1, "Rent", "1000.00");
            FixedPaymentOccurrence paid =
                    occurrence(1L, fp, OccurrenceStatus.PAID, "1000.00", LocalDate.of(2026, 3, 1));
            FixedPaymentOccurrence pending =
                    occurrence(2L, fp, OccurrenceStatus.PENDING, "1000.00", LocalDate.of(2026, 3, 15));
            FixedPaymentOccurrence pending2 =
                    occurrence(3L, fp, OccurrenceStatus.PENDING, "1000.00", LocalDate.of(2026, 3, 28));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD,
                            List.of(paid, pending, pending2),
                            List.of(),
                            new BigDecimal("4000.00"),
                            new BigDecimal("5000.00"),
                            1);

            // then
            assertThat(result.progress().paidCount()).isEqualTo(1);
            assertThat(result.progress().totalCount()).isEqualTo(3);
            // 1/3 * 100 = 33.33
            assertThat(result.progress().paidPct()).isEqualByComparingTo("33.33");
        }

        @Test
        void paidPct_emptyPeriodReturnsZero() {
            // given: no occurrences in period at all
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD, List.of(), List.of(),
                            new BigDecimal("4000.00"), new BigDecimal("5000.00"), 0);

            // then
            assertThat(result.progress().paidPct()).isEqualByComparingTo("0");
        }

        @Test
        void nextDueDate_isEarliestUpcoming() {
            // given
            FixedPayment fp1 = fixedPayment(1, "Rent", "1500.00");
            FixedPayment fp2 = fixedPayment(2, "Internet", "60.00");

            FixedPaymentOccurrence later =
                    occurrence(1L, fp1, OccurrenceStatus.PENDING, "1500.00", LocalDate.of(2026, 3, 20));
            FixedPaymentOccurrence earlier =
                    occurrence(2L, fp2, OccurrenceStatus.PENDING, "60.00", LocalDate.of(2026, 3, 15));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD,
                            List.of(later, earlier),
                            List.of(),
                            new BigDecimal("4000.00"),
                            new BigDecimal("5000.00"),
                            2);

            // then
            assertThat(result.progress().nextDueDate()).isEqualTo(LocalDate.of(2026, 3, 15));
            assertThat(result.progress().nextDueName()).isEqualTo("Internet");
        }

        @Test
        void biggestUpcoming_isHighestExpectedAmount() {
            // given
            FixedPayment fp1 = fixedPayment(1, "Rent", "1500.00");
            FixedPayment fp2 = fixedPayment(2, "Internet", "60.00");

            FixedPaymentOccurrence big =
                    occurrence(1L, fp1, OccurrenceStatus.PENDING, "1500.00", LocalDate.of(2026, 3, 20));
            FixedPaymentOccurrence small =
                    occurrence(2L, fp2, OccurrenceStatus.PENDING, "60.00", LocalDate.of(2026, 3, 15));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD,
                            List.of(big, small),
                            List.of(),
                            new BigDecimal("4000.00"),
                            new BigDecimal("5000.00"),
                            2);

            // then
            assertThat(result.progress().biggestUpcomingTitle()).isEqualTo("Rent");
            assertThat(result.progress().biggestUpcomingAmount()).isEqualByComparingTo("1500.00");
        }

        @Test
        void activeFixedCount_passedThrough() {
            // given
            FixedPayment fp = fixedPayment(1, "Rent", "1000.00");
            FixedPaymentOccurrence occ =
                    occurrence(1L, fp, OccurrenceStatus.PENDING, "1000.00", LocalDate.of(2026, 3, 15));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD,
                            List.of(occ),
                            List.of(),
                            new BigDecimal("4000.00"),
                            new BigDecimal("5000.00"),
                            7);

            // then
            assertThat(result.progress().activeFixedCount()).isEqualTo(7);
        }
    }
}
