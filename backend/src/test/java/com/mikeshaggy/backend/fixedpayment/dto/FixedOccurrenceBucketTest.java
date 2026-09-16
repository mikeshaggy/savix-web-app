package com.mikeshaggy.backend.fixedpayment.dto;

import static com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Stage 3.4: the bucket is a pure function of status, due date, viewing day and committed-window end. */
class FixedOccurrenceBucketTest {

    // the fixture cycle: Sep 9 → Oct 8, next salary expected Oct 9, viewed on Sep 14
    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 14);
    private static final LocalDate CYCLE_END = LocalDate.of(2026, 10, 8);

    private static FixedOccurrenceBucket pending(LocalDate due) {
        return of(OccurrenceStatus.PENDING, due, AS_OF, CYCLE_END);
    }

    @Test
    void paidIsAlwaysPaidThisCycle_regardlessOfDueDate() {
        assertThat(of(OccurrenceStatus.PAID, AS_OF.minusDays(10), AS_OF, CYCLE_END)).isEqualTo(PAID_THIS_CYCLE);
        assertThat(of(OccurrenceStatus.PAID, AS_OF, AS_OF, CYCLE_END)).isEqualTo(PAID_THIS_CYCLE);
        assertThat(of(OccurrenceStatus.PAID, CYCLE_END, AS_OF, CYCLE_END)).isEqualTo(PAID_THIS_CYCLE);
    }

    @Test
    void overdue_byMaintenanceStatusOrByPastDueDate() {
        assertThat(of(OccurrenceStatus.OVERDUE, AS_OF.minusDays(1), AS_OF, CYCLE_END)).isEqualTo(OVERDUE);
        assertThat(of(OccurrenceStatus.OVERDUE, AS_OF, AS_OF, CYCLE_END)).isEqualTo(OVERDUE);
        assertThat(pending(AS_OF.minusDays(1))).isEqualTo(OVERDUE);
    }

    @Test
    void dueSoon_isTodayThroughSevenDaysOut() {
        assertThat(pending(AS_OF)).isEqualTo(DUE_SOON);
        assertThat(pending(AS_OF.plusDays(1))).isEqualTo(DUE_SOON);
        assertThat(pending(AS_OF.plusDays(DUE_SOON_DAYS))).isEqualTo(DUE_SOON);
        assertThat(pending(AS_OF.plusDays(DUE_SOON_DAYS + 1))).isEqualTo(LATER_THIS_CYCLE);
    }

    @Test
    void laterThisCycle_runsThroughTheLastDayOfTheCommittedWindow() {
        // the Oct 8 instalment of the fixture sits here, not under a calendar-month heading
        assertThat(pending(LocalDate.of(2026, 10, 8))).isEqualTo(LATER_THIS_CYCLE);
        assertThat(pending(CYCLE_END)).isEqualTo(LATER_THIS_CYCLE);
    }

    @Test
    void afterPayday_startsOnTheExpectedPaydayItself() {
        assertThat(pending(CYCLE_END.plusDays(1))).isEqualTo(AFTER_PAYDAY); // Oct 9 = expected payday
        assertThat(pending(CYCLE_END.plusDays(20))).isEqualTo(AFTER_PAYDAY);
    }

    @Test
    void dueSoonCutoffWinsOverAfterPaydayOnlyInsideTheWindow() {
        // viewed two days before payday: Oct 8 is due soon, Oct 9 is after payday even though it is 3 days out
        LocalDate lateInCycle = LocalDate.of(2026, 10, 6);
        assertThat(of(OccurrenceStatus.PENDING, LocalDate.of(2026, 10, 8), lateInCycle, CYCLE_END)).isEqualTo(DUE_SOON);
        assertThat(of(OccurrenceStatus.PENDING, LocalDate.of(2026, 10, 9), lateInCycle, CYCLE_END)).isEqualTo(AFTER_PAYDAY);
    }

    @Test
    void awaitingSalary_windowExtendedToTodayKeepsPostPaydayRowsInTheCycle() {
        // expected payday Oct 9 passed without a salary; the window now ends today (Oct 12): a row due Oct 10
        // is overdue in this cycle, one due Oct 12 is due soon — nothing is "after payday" while awaiting
        LocalDate today = LocalDate.of(2026, 10, 12);
        assertThat(of(OccurrenceStatus.PENDING, LocalDate.of(2026, 10, 10), today, today)).isEqualTo(OVERDUE);
        assertThat(of(OccurrenceStatus.PENDING, today, today, today)).isEqualTo(DUE_SOON);
        assertThat(of(OccurrenceStatus.PENDING, today.plusDays(1), today, today)).isEqualTo(AFTER_PAYDAY);
    }
}
