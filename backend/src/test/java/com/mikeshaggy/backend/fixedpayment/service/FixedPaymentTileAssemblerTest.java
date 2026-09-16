package com.mikeshaggy.backend.fixedpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.transaction.domain.Transaction;
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
            PeriodDto.of(
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
            assertThat(result.expectedPaydayDate()).isNull();
            assertThat(result.cycleState()).isNull();
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
    class CommittedWindow {

        /** Open cycle Mar 1 – Mar 31; the expected payday Apr 1 is also the {@code billingEndDate}. */
        private final PeriodDto openCycle = new PeriodDto(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),
                PeriodType.PAY_CYCLE, CycleState.OPEN, LocalDate.of(2026, 4, 1), true);

        @Test
        void upcomingStopsAtPeriodEndNotBillingEndDate() {
            // given: a pending occurrence on the last cycle day and one on the payday (leaked in by a caller)
            FixedPayment fp = fixedPayment(1, "Rent", "1500.00");
            FixedPaymentOccurrence lastDay = occurrence(1L, fp, OccurrenceStatus.PENDING, "1500.00", LocalDate.of(2026, 3, 31));
            FixedPaymentOccurrence payday = occurrence(2L, fp, OccurrenceStatus.PENDING, "1500.00", LocalDate.of(2026, 4, 1));

            // when
            FixedTransactionsTileDto result = assembler.assemble(
                    openCycle, List.of(lastDay, payday), List.of(),
                    new BigDecimal("4000.00"), new BigDecimal("5000.00"), 1, TODAY);

            // then
            assertThat(result.upcoming()).extracting(FixedOccurrenceRowDto::dueDate)
                    .containsExactly(LocalDate.of(2026, 3, 31));
            assertThat(result.progress().nextDueDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        }

        @Test
        void carriesCycleMetadataIntoTile() {
            // when
            FixedTransactionsTileDto assembled = assembler.assemble(
                    openCycle, List.of(), List.of(), BigDecimal.ZERO, new BigDecimal("5000.00"), 0, TODAY);
            FixedTransactionsTileDto empty = assembler.assembleEmpty(openCycle, new BigDecimal("5000.00"));

            // then
            for (FixedTransactionsTileDto result : List.of(assembled, empty)) {
                assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
                assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
                assertThat(result.expectedPaydayDate()).isEqualTo(LocalDate.of(2026, 4, 1));
                assertThat(result.cycleState()).isEqualTo(CycleState.OPEN);
            }
        }
    }

    @Nested
    class SummaryCalculation {

        /** Stage 3.3 — the September 2026 fixture: paid = actual transaction amounts, planned-paid kept separately. */
        @Test
        void paidAmountIsActual_plannedPaidAmountIsSeparate() {
            // given
            FixedPayment rent = fixedPayment(1, "FX_RENT", "1900.00");
            FixedPayment subC = fixedPayment(2, "FX_SUB_C", "89.00");
            FixedPayment subD = fixedPayment(3, "FX_SUB_D", "26.99");
            FixedPaymentOccurrence rentPaid =
                    occurrence(1L, rent, OccurrenceStatus.PAID, "1900.00", LocalDate.of(2026, 3, 10));
            rentPaid.setPaidAmount(new BigDecimal("1879.91"));
            FixedPaymentOccurrence subCPaid =
                    occurrence(2L, subC, OccurrenceStatus.PAID, "89.00", LocalDate.of(2026, 3, 12));
            FixedPaymentOccurrence subDPaid =
                    occurrence(3L, subD, OccurrenceStatus.PAID, "26.99", LocalDate.of(2026, 3, 12));
            FixedPaymentOccurrence pending =
                    occurrence(4L, rent, OccurrenceStatus.PENDING, "76.98", LocalDate.of(2026, 3, 20));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(
                            PERIOD,
                            List.of(rentPaid, subCPaid, subDPaid, pending),
                            List.of(),
                            new BigDecimal("9444.81"),
                            new BigDecimal("5896.89"),
                            4);

            // then
            assertThat(result.summary().paidAmount()).isEqualByComparingTo("1995.90");
            assertThat(result.summary().plannedPaidAmount()).isEqualByComparingTo("2015.99");
            assertThat(result.summary().paidCount()).isEqualTo(3);
            // planned / remaining stay expected-amount sums
            assertThat(result.summary().plannedAmount()).isEqualByComparingTo("2092.97");
            assertThat(result.summary().remainingAmount()).isEqualByComparingTo("76.98");
        }

        @Test
        void paidOccurrenceWithoutRecordedAmount_countsItsExpectedAmountAsActual() {
            // given
            FixedPayment fp = fixedPayment(1, "Rent", "1500.00");
            FixedPaymentOccurrence paid =
                    occurrence(1L, fp, OccurrenceStatus.PAID, "1500.00", LocalDate.of(2026, 3, 1));
            paid.setPaidAmount(null);

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(PERIOD, List.of(paid), List.of(),
                            new BigDecimal("4000.00"), new BigDecimal("5000.00"), 1);

            // then
            assertThat(result.summary().paidAmount()).isEqualByComparingTo("1500.00");
            assertThat(result.summary().plannedPaidAmount()).isEqualByComparingTo("1500.00");
        }

        @Test
        void assembleEmpty_zeroesPlannedPaidAmount() {
            assertThat(assembler.assembleEmpty(PERIOD, BigDecimal.ZERO).summary().plannedPaidAmount())
                    .isEqualByComparingTo("0");
        }

        /** Stage 3.2 — paid rows carry the transaction-date verdict; the same DTO feeds dashboard, strip, timeline and list. */
        @Test
        void paidRowsCarryPaidDateLateDaysAndVerdictFromTheTransactionDate() {
            // given
            FixedPayment fp = fixedPayment(1, "FX_RENT", "1900.00");
            FixedPaymentOccurrence late =
                    occurrence(1L, fp, OccurrenceStatus.PAID, "1900.00", LocalDate.of(2026, 3, 10));
            late.setTransaction(Transaction.builder().id(700L).amount(new BigDecimal("1879.91"))
                    .transactionDate(LocalDate.of(2026, 3, 11)).build());
            late.setPaidAmount(new BigDecimal("1879.91"));
            late.setPaidAt(LocalDateTime.of(2026, 3, 12, 0, 30)); // stale link timestamp must not matter
            FixedPaymentOccurrence onTime =
                    occurrence(2L, fp, OccurrenceStatus.PAID, "89.00", LocalDate.of(2026, 3, 12));
            onTime.setTransaction(Transaction.builder().id(701L).amount(new BigDecimal("89.00"))
                    .transactionDate(LocalDate.of(2026, 3, 12)).build());
            FixedPaymentOccurrence pending =
                    occurrence(3L, fp, OccurrenceStatus.PENDING, "76.98", LocalDate.of(2026, 3, 20));

            // when
            FixedTransactionsTileDto result =
                    assembler.assemble(PERIOD, List.of(late, onTime, pending), List.of(),
                            new BigDecimal("4000.00"), new BigDecimal("5000.00"), 1);

            // then
            FixedOccurrenceRowDto lateRow = result.paid().stream()
                    .filter(r -> r.occurrenceId() == 1L).findFirst().orElseThrow();
            assertThat(lateRow.paidDate()).isEqualTo(LocalDate.of(2026, 3, 11));
            assertThat(lateRow.lateDays()).isEqualTo(1);
            assertThat(lateRow.paidOnTime()).isFalse();
            assertThat(lateRow.paidAmount()).isEqualByComparingTo("1879.91");
            assertThat(lateRow.expectedAmount()).isEqualByComparingTo("1900.00");

            FixedOccurrenceRowDto onTimeRow = result.paid().stream()
                    .filter(r -> r.occurrenceId() == 2L).findFirst().orElseThrow();
            assertThat(onTimeRow.paidDate()).isEqualTo(LocalDate.of(2026, 3, 12));
            assertThat(onTimeRow.lateDays()).isZero();
            assertThat(onTimeRow.paidOnTime()).isTrue();

            assertThat(result.upcoming()).singleElement().satisfies(row -> {
                assertThat(row.paidDate()).isNull();
                assertThat(row.lateDays()).isNull();
                assertThat(row.paidOnTime()).isNull();
            });
        }

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
    class Buckets {

        // open cycle Mar 1 → Mar 31, next salary expected Apr 1, viewed Mar 11
        private final PeriodDto openCycle = new PeriodDto(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),
                PeriodType.PAY_CYCLE, CycleState.OPEN, LocalDate.of(2026, 4, 1), true);

        @Test
        void everyRowIsBucketedAgainstTheCommittedWindowEnd() {
            FixedPayment fp = fixedPayment(1, "Rent", "100.00");
            FixedPaymentOccurrence overdue = occurrence(1L, fp, OccurrenceStatus.OVERDUE, "100.00", LocalDate.of(2026, 3, 5));
            FixedPaymentOccurrence dueToday = occurrence(2L, fp, OccurrenceStatus.PENDING, "100.00", TODAY);
            FixedPaymentOccurrence dueIn7 = occurrence(3L, fp, OccurrenceStatus.PENDING, "100.00", TODAY.plusDays(7));
            FixedPaymentOccurrence dueIn8 = occurrence(4L, fp, OccurrenceStatus.PENDING, "100.00", TODAY.plusDays(8));
            FixedPaymentOccurrence lastDay = occurrence(5L, fp, OccurrenceStatus.PENDING, "100.00", LocalDate.of(2026, 3, 31));
            FixedPaymentOccurrence paid = occurrence(6L, fp, OccurrenceStatus.PAID, "100.00", LocalDate.of(2026, 3, 2));

            FixedTransactionsTileDto result = assembler.assemble(
                    openCycle, List.of(dueToday, dueIn7, dueIn8, lastDay, paid), List.of(overdue),
                    new BigDecimal("4000.00"), new BigDecimal("1000.00"), 1);

            assertThat(result.overdue()).extracting(FixedOccurrenceRowDto::bucket)
                    .containsExactly(FixedOccurrenceBucket.OVERDUE);
            assertThat(result.upcoming()).extracting(FixedOccurrenceRowDto::occurrenceId, FixedOccurrenceRowDto::bucket)
                    .containsExactly(
                            tuple(2L, FixedOccurrenceBucket.DUE_SOON),
                            tuple(3L, FixedOccurrenceBucket.DUE_SOON),
                            tuple(4L, FixedOccurrenceBucket.LATER_THIS_CYCLE),
                            tuple(5L, FixedOccurrenceBucket.LATER_THIS_CYCLE));
            assertThat(result.paid()).extracting(FixedOccurrenceRowDto::bucket)
                    .containsExactly(FixedOccurrenceBucket.PAID_THIS_CYCLE);
        }

        @Test
        void paydayOccurrenceIsNeverInTheTile_soAfterPaydayIsNotProducedHere() {
            FixedPayment fp = fixedPayment(1, "Rent", "100.00");
            FixedPaymentOccurrence payday = occurrence(1L, fp, OccurrenceStatus.PENDING, "100.00", LocalDate.of(2026, 4, 1));

            FixedTransactionsTileDto result = assembler.assemble(
                    openCycle, List.of(payday), List.of(), new BigDecimal("4000.00"), new BigDecimal("1000.00"), 1);

            assertThat(result.upcoming()).isEmpty();
        }

        @Test
        void afterPaydayRowsAreDisplayOnly_bucketedAfterPayday_andNeverCounted() {
            FixedPayment fp = fixedPayment(1, "Rent", "100.00");
            FixedPaymentOccurrence committed = occurrence(1L, fp, OccurrenceStatus.PENDING, "100.00", LocalDate.of(2026, 3, 20));
            FixedPaymentOccurrence onPayday = occurrence(2L, fp, OccurrenceStatus.PENDING, "100.00", LocalDate.of(2026, 4, 1));
            FixedPaymentOccurrence nextCycle = occurrence(3L, fp, OccurrenceStatus.PENDING, "100.00", LocalDate.of(2026, 4, 15));
            FixedPaymentOccurrence leaked = occurrence(4L, fp, OccurrenceStatus.PENDING, "100.00", LocalDate.of(2026, 3, 25)); // inside the window: never displayed twice

            FixedTransactionsTileDto result = assembler.assemble(
                    openCycle, List.of(committed), List.of(), new BigDecimal("4000.00"), new BigDecimal("1000.00"), 1,
                    TODAY, List.of(nextCycle, leaked, onPayday), LocalDate.of(2026, 4, 30));

            assertThat(result.afterPaydayEnd()).isEqualTo(LocalDate.of(2026, 4, 30));
            assertThat(result.afterPayday()).extracting(FixedOccurrenceRowDto::occurrenceId, FixedOccurrenceRowDto::bucket)
                    .containsExactly(
                            tuple(2L, FixedOccurrenceBucket.AFTER_PAYDAY),
                            tuple(3L, FixedOccurrenceBucket.AFTER_PAYDAY));
            // committed figures see only the committed row
            assertThat(result.summary().plannedCount()).isEqualTo(1);
            assertThat(result.summary().plannedAmount()).isEqualByComparingTo("100.00");
            assertThat(result.summary().remainingAmount()).isEqualByComparingTo("100.00");
            assertThat(result.progress().totalCount()).isEqualTo(1);
            assertThat(result.balanceAfterFixed()).isEqualByComparingTo("900.00");
            assertThat(result.riskIndicator().isAtRisk()).isFalse();
            assertThat(result.upcoming()).extracting(FixedOccurrenceRowDto::occurrenceId).containsExactly(1L);
        }

        @Test
        void emptyTileCanStillCarryAfterPaydayRows() {
            FixedPayment fp = fixedPayment(1, "Rent", "100.00");
            FixedPaymentOccurrence nextCycle = occurrence(3L, fp, OccurrenceStatus.PENDING, "100.00", LocalDate.of(2026, 4, 15));

            FixedTransactionsTileDto result = assembler.assembleEmpty(
                    openCycle, BigDecimal.TEN, List.of(nextCycle), LocalDate.of(2026, 4, 30), TODAY);

            assertThat(result.summary().plannedCount()).isZero();
            assertThat(result.balanceAfterFixed()).isEqualByComparingTo(BigDecimal.TEN);
            assertThat(result.afterPayday()).extracting(FixedOccurrenceRowDto::bucket)
                    .containsExactly(FixedOccurrenceBucket.AFTER_PAYDAY);
        }

        @Test
        void emptyTileHasNoRowsToBucket() {
            assertThat(assembler.assembleEmpty(openCycle, BigDecimal.TEN).upcoming()).isEmpty();
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
