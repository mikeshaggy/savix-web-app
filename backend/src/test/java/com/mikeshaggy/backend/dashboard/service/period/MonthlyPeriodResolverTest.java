package com.mikeshaggy.backend.dashboard.service.period;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MonthlyPeriodResolverTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-19T12:00:00Z"),
            ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Integer WALLET_ID = 1;

    private final MonthlyPeriodResolver resolver = new MonthlyPeriodResolver(FIXED_CLOCK);

    @Test
    void supports_returnsMonthly() {
        // given
        PeriodType result = resolver.supports();

        // when
        // then
        assertThat(result).isEqualTo(PeriodType.MONTHLY);
    }

    @Nested
    class CalendarMonthResolution {

        @Test
        void march2026_returnsFull31DayMonth() {
            // given
            LocalDate monthInput = LocalDate.of(2026, 3, 15);

            // when
            PeriodDto result = resolver.resolve(WALLET_ID, USER_ID, monthInput, null, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 3, 31));
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(result.periodType()).isEqualTo(PeriodType.MONTHLY);
        }

        @Test
        void april2026_returnsFull30DayMonth() {
            // given
            LocalDate monthInput = LocalDate.of(2026, 4, 30);

            // when
            PeriodDto result = resolver.resolve(WALLET_ID, USER_ID, monthInput, null, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 4, 30));
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        }

        @Test
        void leapYearFebruary2024_returnsTwentyNineDays() {
            // given
            LocalDate monthInput = LocalDate.of(2024, 2, 1);

            // when
            PeriodDto result = resolver.resolve(WALLET_ID, USER_ID, monthInput, null, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2024, 2, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2024, 2, 29));
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2024, 3, 1));
        }

        @Test
        void nonLeapYearFebruary2025_returnsTwentyEightDays() {
            // given
            LocalDate monthInput = LocalDate.of(2025, 2, 10);

            // when
            PeriodDto result = resolver.resolve(WALLET_ID, USER_ID, monthInput, null, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2025, 2, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2025, 2, 28));
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2025, 3, 1));
        }

        @Test
        void december2026_crossesYearBoundary() {
            // given
            LocalDate monthInput = LocalDate.of(2026, 12, 5);

            // when
            PeriodDto result = resolver.resolve(WALLET_ID, USER_ID, monthInput, null, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 12, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        }

        @Test
        void nullDates_resolvesCurrentMonthFromInjectedClock() {
            // given
            // when
            PeriodDto result = resolver.resolve(WALLET_ID, USER_ID, null, null, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 5, 31));
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        }

        @Test
        void nullStartDate_usesEndDateMonth() {
            // given
            LocalDate monthInput = LocalDate.of(2026, 6, 20);

            // when
            PeriodDto result = resolver.resolve(WALLET_ID, USER_ID, null, monthInput, null);

            // then
            assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 6, 30));
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        }
    }
}
