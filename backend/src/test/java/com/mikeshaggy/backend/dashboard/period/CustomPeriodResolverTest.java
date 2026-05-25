package com.mikeshaggy.backend.dashboard.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CustomPeriodResolverTest {

    private final CustomPeriodResolver resolver = new CustomPeriodResolver();

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Integer WALLET_ID = 1;

    @Test
    void supports_returnsCustom() {
        // given
        var result = resolver.supports();

        // when
        // then
        assertThat(result).isEqualTo(PeriodType.CUSTOM);
    }

    @Nested
    class HappyPath {

        @Test
        void validRange_returnsPeriodWithCorrectDates() {
            // given
            LocalDate start = LocalDate.of(2026, 1, 1);
            LocalDate end = LocalDate.of(2026, 1, 31);

            // when
            PeriodDto result = resolver.resolve(WALLET_ID, USER_ID, start, end, null);

            // then
            assertThat(result.startDate()).isEqualTo(start);
            assertThat(result.endDate()).isEqualTo(end);
            assertThat(result.billingEndDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(result.periodType()).isEqualTo(PeriodType.CUSTOM);
        }

        @Test
        void singleDayRange_allowed() {
            // given
            LocalDate date = LocalDate.of(2026, 3, 15);

            // when
            PeriodDto result = resolver.resolve(WALLET_ID, USER_ID, date, date, null);

            // then
            assertThat(result.startDate()).isEqualTo(date);
            assertThat(result.endDate()).isEqualTo(date);
        }
    }

    @Nested
    class Validation {

        @Test
        void nullStartDate_throws() {
            // given
            // when
            // then
            assertThatThrownBy(
                            () -> resolver.resolve(WALLET_ID, USER_ID, null, LocalDate.of(2026, 1, 31), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("startDate and endDate are required");
        }

        @Test
        void nullEndDate_throws() {
            // given
            // when
            // then
            assertThatThrownBy(
                            () -> resolver.resolve(WALLET_ID, USER_ID, LocalDate.of(2026, 1, 1), null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("startDate and endDate are required");
        }

        @Test
        void bothDatesNull_throws() {
            // given
            // when
            // then
            assertThatThrownBy(() -> resolver.resolve(WALLET_ID, USER_ID, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void startAfterEnd_throws() {
            // given
            LocalDate start = LocalDate.of(2026, 3, 15);
            LocalDate end = LocalDate.of(2026, 3, 1);

            // when
            // then
            assertThatThrownBy(() -> resolver.resolve(WALLET_ID, USER_ID, start, end, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be after");
        }
    }
}
