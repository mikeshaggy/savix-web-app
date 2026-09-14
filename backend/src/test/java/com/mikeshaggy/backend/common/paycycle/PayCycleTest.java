package com.mikeshaggy.backend.common.paycycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayCycleTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate SEP_9 = LocalDate.of(2026, 9, 9);
    private static final LocalDate OCT_9 = LocalDate.of(2026, 10, 9);

    @Test
    void openCycleEndsTheDayBeforeTheExpectedAnchor() {
        PayCycle cycle = PayCycle.open(USER_ID, 1, SEP_9, OCT_9);

        assertThat(cycle.end()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(cycle.expectedNextAnchor()).isEqualTo(OCT_9);
        assertThat(cycle.state()).isEqualTo(CycleState.OPEN);
        assertThat(cycle.lengthDays()).isEqualTo(30);
    }

    @Test
    void dayIndexIsOneBasedAndNotClamped() {
        PayCycle cycle = PayCycle.open(USER_ID, 1, SEP_9, OCT_9);

        assertThat(cycle.dayIndex(SEP_9)).isEqualTo(1);
        assertThat(cycle.dayIndex(LocalDate.of(2026, 9, 14))).isEqualTo(6);
        assertThat(cycle.dayIndex(LocalDate.of(2026, 10, 8))).isEqualTo(30);
        assertThat(cycle.dayIndex(LocalDate.of(2026, 10, 11))).isEqualTo(33);
        assertThat(cycle.dayIndex(LocalDate.of(2026, 9, 8))).isZero();
    }

    @Test
    void containsIsInclusiveOnBothEnds() {
        PayCycle cycle = PayCycle.closed(USER_ID, 1, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8));

        assertThat(cycle.contains(LocalDate.of(2026, 8, 10))).isTrue();
        assertThat(cycle.contains(LocalDate.of(2026, 9, 8))).isTrue();
        assertThat(cycle.contains(SEP_9)).isFalse();
        assertThat(cycle.contains(LocalDate.of(2026, 8, 9))).isFalse();
        assertThat(cycle.expectedNextAnchor()).isNull();
        assertThat(cycle.lengthDays()).isEqualTo(30);
    }

    @Test
    void awaitingSalaryKeepsTheExpectedAnchor() {
        PayCycle cycle = PayCycle.awaitingSalary(USER_ID, 1, SEP_9, OCT_9);

        assertThat(cycle.state()).isEqualTo(CycleState.AWAITING_SALARY);
        assertThat(cycle.end()).isEqualTo(OCT_9.minusDays(1));
    }

    @Test
    void closedCycleRejectsAnExpectedAnchor() {
        assertThatThrownBy(() -> new PayCycle(USER_ID, 1, SEP_9, LocalDate.of(2026, 10, 8), OCT_9, CycleState.CLOSED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void openCycleRejectsAnEndThatIsNotTheDayBeforeTheAnchor() {
        assertThatThrownBy(() -> new PayCycle(USER_ID, 1, SEP_9, LocalDate.of(2026, 10, 9), OCT_9, CycleState.OPEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void endBeforeStartIsRejected() {
        assertThatThrownBy(() -> PayCycle.closed(USER_ID, 1, SEP_9, LocalDate.of(2026, 9, 8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paydayRuleValidatesDayOfMonth() {
        assertThatThrownBy(() -> PaydayRule.configured(0, WeekendShift.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaydayRule.configured(32, WeekendShift.NONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
