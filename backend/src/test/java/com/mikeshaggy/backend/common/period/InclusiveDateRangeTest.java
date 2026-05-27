package com.mikeshaggy.backend.common.period;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InclusiveDateRangeTest {

    @Test
    void daysBetween_countsBothStartAndEndDates() {
        int days = InclusiveDateRange.daysBetween(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 3));

        assertThat(days).isEqualTo(3);
    }

    @Test
    void daysBetween_whenEndBeforeStartReturnsZero() {
        int days = InclusiveDateRange.daysBetween(
                LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 1));

        assertThat(days).isZero();
    }

    @Test
    void contains_includesBoundaryDates() {
        InclusiveDateRange range = new InclusiveDateRange(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 3));

        assertThat(range.contains(LocalDate.of(2026, 3, 1))).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 3, 3))).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 3, 4))).isFalse();
    }

    @Test
    void clampEnd_returnsRangeEndWhenRequestedDateIsAfterEnd() {
        InclusiveDateRange range = new InclusiveDateRange(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 3));

        assertThat(range.clampEnd(LocalDate.of(2026, 3, 10)))
                .isEqualTo(LocalDate.of(2026, 3, 3));
    }
}
