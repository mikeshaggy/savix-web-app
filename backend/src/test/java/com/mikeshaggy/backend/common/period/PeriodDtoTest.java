package com.mikeshaggy.backend.common.period;

import com.mikeshaggy.backend.common.paycycle.CycleState;

import java.time.LocalDate;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodDtoTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 9);
    private static final LocalDate END = LocalDate.of(2026, 10, 8);
    private static final LocalDate BILLING_END = LocalDate.of(2026, 10, 9);

    @Nested
    class OfFactory {

        @Test
        void producesNullPayCycleMetadataForEveryPeriodType() {
            // given / when
            PeriodDto payCycle = PeriodDto.of(START, END, BILLING_END, PeriodType.PAY_CYCLE);
            PeriodDto lastPayCycle = PeriodDto.of(START, END, BILLING_END, PeriodType.LAST_PAY_CYCLE);
            PeriodDto monthly = PeriodDto.of(START, END, BILLING_END, PeriodType.MONTHLY);
            PeriodDto custom = PeriodDto.of(START, END, BILLING_END, PeriodType.CUSTOM);

            // then: the legacy shape never carries pay-cycle metadata, regardless of periodType
            for (PeriodDto period : java.util.List.of(payCycle, lastPayCycle, monthly, custom)) {
                assertThat(period.cycleState()).isNull();
                assertThat(period.expectedNextAnchorDate()).isNull();
                assertThat(period.salaryWallet()).isNull();
            }
        }

        @Test
        void carriesTheGivenBoundariesAndType() {
            // given / when
            PeriodDto period = PeriodDto.of(START, END, BILLING_END, PeriodType.PAY_CYCLE);

            // then
            assertThat(period.startDate()).isEqualTo(START);
            assertThat(period.endDate()).isEqualTo(END);
            assertThat(period.billingEndDate()).isEqualTo(BILLING_END);
            assertThat(period.periodType()).isEqualTo(PeriodType.PAY_CYCLE);
        }
    }

    @Nested
    class ElapsedEndDate {

        @Test
        void payCycleEndingInTheFutureIsClampedToToday() {
            // given: an OPEN pay cycle whose end (Oct 8) is after today (Sep 14)
            PeriodDto period = new PeriodDto(START, END, BILLING_END, PeriodType.PAY_CYCLE,
                    CycleState.OPEN, BILLING_END, true);
            LocalDate today = LocalDate.of(2026, 9, 14);

            // when
            LocalDate elapsedEnd = period.elapsedEndDate(today);

            // then
            assertThat(elapsedEnd).isEqualTo(today);
        }

        @Test
        void payCycleEndingInThePastKeepsItsEnd() {
            // given: a CLOSED pay cycle whose end (Sep 8) is before today (Sep 14)
            LocalDate closedEnd = LocalDate.of(2026, 9, 8);
            PeriodDto period = new PeriodDto(LocalDate.of(2026, 8, 10), closedEnd,
                    LocalDate.of(2026, 9, 9), PeriodType.PAY_CYCLE, CycleState.CLOSED, null, true);
            LocalDate today = LocalDate.of(2026, 9, 14);

            // when
            LocalDate elapsedEnd = period.elapsedEndDate(today);

            // then
            assertThat(elapsedEnd).isEqualTo(closedEnd);
        }

        @Test
        void payCycleEndingExactlyTodayKeepsItsEnd() {
            // given: endDate == today is neither before nor after; must not clamp differently
            LocalDate today = LocalDate.of(2026, 9, 14);
            PeriodDto period = new PeriodDto(LocalDate.of(2026, 8, 10), today,
                    today.plusDays(1), PeriodType.PAY_CYCLE, CycleState.AWAITING_SALARY, today.plusDays(1), true);

            // when
            LocalDate elapsedEnd = period.elapsedEndDate(today);

            // then
            assertThat(elapsedEnd).isEqualTo(today);
        }

        @Test
        void monthlyPeriodIsNeverClampedEvenWhenEndIsInTheFuture() {
            // given: a MONTHLY period whose end lies after today
            LocalDate today = LocalDate.of(2026, 9, 14);
            LocalDate futureEnd = LocalDate.of(2026, 9, 30);
            PeriodDto period = PeriodDto.of(LocalDate.of(2026, 9, 1), futureEnd,
                    LocalDate.of(2026, 10, 1), PeriodType.MONTHLY);

            // when
            LocalDate elapsedEnd = period.elapsedEndDate(today);

            // then
            assertThat(elapsedEnd).isEqualTo(futureEnd);
        }

        @Test
        void customPeriodIsNeverClampedEvenWhenEndIsInTheFuture() {
            // given: a CUSTOM period whose end lies after today
            LocalDate today = LocalDate.of(2026, 9, 14);
            LocalDate futureEnd = LocalDate.of(2026, 9, 20);
            PeriodDto period = PeriodDto.of(LocalDate.of(2026, 9, 10), futureEnd,
                    futureEnd.plusDays(1), PeriodType.CUSTOM);

            // when
            LocalDate elapsedEnd = period.elapsedEndDate(today);

            // then
            assertThat(elapsedEnd).isEqualTo(futureEnd);
        }

        @Test
        void lastPayCycleTypeIsNeverClampedEvenWhenEndIsInTheFuture() {
            // given: elapsedEndDate only special-cases PAY_CYCLE, not LAST_PAY_CYCLE
            LocalDate today = LocalDate.of(2026, 9, 1);
            LocalDate futureEnd = LocalDate.of(2026, 9, 8);
            PeriodDto period = new PeriodDto(LocalDate.of(2026, 8, 10), futureEnd,
                    futureEnd.plusDays(1), PeriodType.LAST_PAY_CYCLE, CycleState.CLOSED, null, true);

            // when
            LocalDate elapsedEnd = period.elapsedEndDate(today);

            // then
            assertThat(elapsedEnd).isEqualTo(futureEnd);
        }
    }
}
