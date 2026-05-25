package com.mikeshaggy.backend.fixedpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FixedPaymentOccurrenceGenerationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 11);

    @Mock
    private FixedPaymentRepository fixedPaymentRepository;

    @Mock
    private FixedPaymentOccurrenceRepository occurrenceRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private FixedPaymentOccurrenceGenerationService service;

    @BeforeEach
    void setUp() {
        setClockDate(TODAY);
    }

    private void setClockDate(LocalDate date) {
        Instant fixedInstant = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        lenient().when(clock.instant()).thenReturn(fixedInstant);
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
    }


    @Nested
    class ComputeDueDates {

        @Test
        void monthly_generatesCorrectDates() {
            // given
            LocalDate anchor = LocalDate.of(2026, 1, 15);
            LocalDate from = LocalDate.of(2026, 1, 1);
            LocalDate to = LocalDate.of(2026, 4, 30);

            List<LocalDate> dates = service.computeDueDates(anchor, Cycle.MONTHLY, from, to);

            // when
            // then
            assertThat(dates)
                    .containsExactly(
                            LocalDate.of(2026, 1, 15),
                            LocalDate.of(2026, 2, 15),
                            LocalDate.of(2026, 3, 15),
                            LocalDate.of(2026, 4, 15));
        }

        @Test
        void weekly_generatesCorrectDates() {
            // given
            LocalDate anchor = LocalDate.of(2026, 3, 2); // Monday
            LocalDate from = LocalDate.of(2026, 3, 1);
            LocalDate to = LocalDate.of(2026, 3, 23);

            List<LocalDate> dates = service.computeDueDates(anchor, Cycle.WEEKLY, from, to);

            // when
            // then
            assertThat(dates)
                    .containsExactly(
                            LocalDate.of(2026, 3, 2),
                            LocalDate.of(2026, 3, 9),
                            LocalDate.of(2026, 3, 16),
                            LocalDate.of(2026, 3, 23));
        }

        @Test
        void quarterly_generatesCorrectDates() {
            // given
            LocalDate anchor = LocalDate.of(2026, 1, 1);
            LocalDate from = LocalDate.of(2026, 1, 1);
            LocalDate to = LocalDate.of(2026, 12, 31);

            List<LocalDate> dates = service.computeDueDates(anchor, Cycle.QUARTERLY, from, to);

            // when
            // then
            assertThat(dates)
                    .containsExactly(
                            LocalDate.of(2026, 1, 1),
                            LocalDate.of(2026, 4, 1),
                            LocalDate.of(2026, 7, 1),
                            LocalDate.of(2026, 10, 1));
        }

        @Test
        void yearly_generatesCorrectDates() {
            // given
            LocalDate anchor = LocalDate.of(2025, 6, 15);
            LocalDate from = LocalDate.of(2025, 6, 15);
            LocalDate to = LocalDate.of(2028, 6, 15);

            List<LocalDate> dates = service.computeDueDates(anchor, Cycle.YEARLY, from, to);

            // when
            // then
            assertThat(dates)
                    .containsExactly(
                            LocalDate.of(2025, 6, 15),
                            LocalDate.of(2026, 6, 15),
                            LocalDate.of(2027, 6, 15),
                            LocalDate.of(2028, 6, 15));
        }

        @Test
        void anchorBeforeFrom_skipsEarlyDates() {
            // given
            LocalDate anchor = LocalDate.of(2025, 1, 10);
            LocalDate from = LocalDate.of(2026, 3, 1);
            LocalDate to = LocalDate.of(2026, 5, 31);

            List<LocalDate> dates = service.computeDueDates(anchor, Cycle.MONTHLY, from, to);

            // when
            // then
            assertThat(dates)
                    .containsExactly(
                            LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 10));
        }

        @Test
        void anchorAfterTo_returnsEmpty() {
            // given
            LocalDate anchor = LocalDate.of(2027, 1, 1);
            LocalDate from = LocalDate.of(2026, 1, 1);
            LocalDate to = LocalDate.of(2026, 12, 31);

            List<LocalDate> dates = service.computeDueDates(anchor, Cycle.MONTHLY, from, to);

            // when
            // then
            assertThat(dates).isEmpty();
        }

        @Test
        void exactBoundary_inclusiveOnBothEnds() {
            // given
            LocalDate anchor = LocalDate.of(2026, 3, 1);
            LocalDate from = LocalDate.of(2026, 3, 1);
            LocalDate to = LocalDate.of(2026, 3, 1);

            List<LocalDate> dates = service.computeDueDates(anchor, Cycle.MONTHLY, from, to);

            // when
            // then
            assertThat(dates).containsExactly(LocalDate.of(2026, 3, 1));
        }

        @Test
        void monthly_endOfMonth_handlesShortMonths() {
            // Anchor on Jan 31 — Feb doesn't have 31st
            // given
            LocalDate anchor = LocalDate.of(2026, 1, 31);
            LocalDate from = LocalDate.of(2026, 1, 1);
            LocalDate to = LocalDate.of(2026, 4, 30);

            List<LocalDate> dates = service.computeDueDates(anchor, Cycle.MONTHLY, from, to);

            // Java's plusMonths handles end-of-month: Jan 31 → Feb 28 → Mar 28 → Apr 28
            // when
            // then
            assertThat(dates)
                    .containsExactly(
                            LocalDate.of(2026, 1, 31),
                            LocalDate.of(2026, 2, 28),
                            LocalDate.of(2026, 3, 28),
                            LocalDate.of(2026, 4, 28));
        }

        @Test
        void toDayBeforeCycleDate_excludesThatCycleDate() {
            // given
            LocalDate anchor = LocalDate.of(2026, 1, 15);
            LocalDate from = LocalDate.of(2026, 1, 15);
            LocalDate to = LocalDate.of(2026, 3, 14); // one day before March 15 cycle date

            List<LocalDate> dates = service.computeDueDates(anchor, Cycle.MONTHLY, from, to);

            // when
            // then
            assertThat(dates).containsExactly(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15));
        }

        @Test
        void fromDayAfterCycleDate_startsFromNextCycle() {
            // given
            LocalDate anchor = LocalDate.of(2026, 1, 15);
            LocalDate from = LocalDate.of(2026, 1, 16); // day after Jan 15 cycle date
            LocalDate to = LocalDate.of(2026, 4, 30);

            List<LocalDate> dates = service.computeDueDates(anchor, Cycle.MONTHLY, from, to);

            // when
            // then
            assertThat(dates)
                    .containsExactly(
                            LocalDate.of(2026, 2, 15), LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 15));
        }
    }

    @Nested
    class Advance {

        @Test
        void weekly_advancesBy7Days() {
            // given
            // when
            // then
            assertThat(service.advance(LocalDate.of(2026, 3, 1), Cycle.WEEKLY))
                    .isEqualTo(LocalDate.of(2026, 3, 8));
        }

        @Test
        void monthly_advancesBy1Month() {
            // given
            // when
            // then
            assertThat(service.advance(LocalDate.of(2026, 3, 15), Cycle.MONTHLY))
                    .isEqualTo(LocalDate.of(2026, 4, 15));
        }

        @Test
        void quarterly_advancesBy3Months() {
            // given
            // when
            // then
            assertThat(service.advance(LocalDate.of(2026, 1, 1), Cycle.QUARTERLY))
                    .isEqualTo(LocalDate.of(2026, 4, 1));
        }

        @Test
        void yearly_advancesBy1Year() {
            // given
            // when
            // then
            assertThat(service.advance(LocalDate.of(2026, 6, 15), Cycle.YEARLY))
                    .isEqualTo(LocalDate.of(2027, 6, 15));
        }
    }

    @Nested
    class EnsureOccurrencesGenerated {

        @Test
        void noActivePayments_doesNothing() {
            // given
            UUID userId = UUID.randomUUID();
            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of());

            service.ensureOccurrencesGenerated(userId);

            // when
            // then
            verify(occurrenceRepository, never()).saveAll(any());
        }

        @Test
        void generatesOccurrencesUpToHorizon() {
            // given
            UUID userId = UUID.randomUUID();
            FixedPayment fp =
                    FixedPayment.builder()
                            .id(1)
                            .anchorDate(TODAY.minusMonths(1))
                            .cycle(Cycle.MONTHLY)
                            .amount(new BigDecimal("100.00"))
                            .build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findMaxDueDateByFixedPaymentId(1))
                    .thenReturn(Optional.of(fp.getAnchorDate().minusDays(1)));
            when(occurrenceRepository.findDueDatesByFixedPaymentIdAndDueDateBetween(
                            anyInt(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of());

            service.ensureOccurrencesGenerated(userId);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FixedPaymentOccurrence>> captor = ArgumentCaptor.forClass(List.class);
            verify(occurrenceRepository).saveAll(captor.capture());

            // when
            List<FixedPaymentOccurrence> saved = captor.getValue();
            // then
            assertThat(saved).isNotEmpty();
            assertThat(saved)
                    .allSatisfy(
                            occ -> {
                                assertThat(occ.getFixedPayment()).isEqualTo(fp);
                                assertThat(occ.getExpectedAmount()).isEqualByComparingTo("100.00");
                                assertThat(occ.getStatus()).isEqualTo(OccurrenceStatus.PENDING);
                            });
        }

        @Test
        void activeToIsInclusiveAndPreventsOccurrencesAfterEndDate() {
            // given
            setClockDate(LocalDate.of(2026, 5, 24));
            UUID userId = UUID.randomUUID();
            FixedPayment fp =
                    FixedPayment.builder()
                            .id(1)
                            .anchorDate(LocalDate.of(2026, 3, 23))
                            .cycle(Cycle.MONTHLY)
                            .activeTo(LocalDate.of(2026, 6, 23))
                            .amount(new BigDecimal("100.00"))
                            .build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findMaxDueDateByFixedPaymentId(1))
                    .thenReturn(Optional.of(fp.getAnchorDate().minusDays(1)));
            when(occurrenceRepository.findDueDatesByFixedPaymentIdAndDueDateBetween(
                            anyInt(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of());

            service.ensureOccurrencesGenerated(userId);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FixedPaymentOccurrence>> captor = ArgumentCaptor.forClass(List.class);
            verify(occurrenceRepository).saveAll(captor.capture());

            // when
            List<LocalDate> dueDates = captor.getValue().stream()
                    .map(FixedPaymentOccurrence::getDueDate)
                    .toList();
            // then
            assertThat(dueDates)
                    .containsExactly(
                            LocalDate.of(2026, 3, 23),
                            LocalDate.of(2026, 4, 23),
                            LocalDate.of(2026, 5, 23),
                            LocalDate.of(2026, 6, 23));
            assertThat(dueDates).doesNotContain(LocalDate.of(2026, 7, 23));
        }

        @Test
        void nullActiveToContinuesGeneratingNormally() {
            // given
            UUID userId = UUID.randomUUID();
            FixedPayment fp =
                    FixedPayment.builder()
                            .id(1)
                            .anchorDate(TODAY)
                            .cycle(Cycle.MONTHLY)
                            .activeTo(null)
                            .amount(new BigDecimal("100.00"))
                            .build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findMaxDueDateByFixedPaymentId(1)).thenReturn(Optional.empty());
            when(occurrenceRepository.findDueDatesByFixedPaymentIdAndDueDateBetween(
                            anyInt(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of());

            service.ensureOccurrencesGenerated(userId);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FixedPaymentOccurrence>> captor = ArgumentCaptor.forClass(List.class);
            verify(occurrenceRepository).saveAll(captor.capture());

            // when
            List<LocalDate> dueDates = captor.getValue().stream()
                    .map(FixedPaymentOccurrence::getDueDate)
                    .toList();
            // then
            assertThat(dueDates)
                    .containsExactly(TODAY, TODAY.plusMonths(1), TODAY.plusMonths(2));
        }

        @Test
        void activeToBeforeNextGeneratedDueDateCreatesNoFutureOccurrence() {
            // given
            setClockDate(LocalDate.of(2026, 5, 24));
            UUID userId = UUID.randomUUID();
            FixedPayment fp =
                    FixedPayment.builder()
                            .id(1)
                            .anchorDate(LocalDate.of(2026, 3, 23))
                            .cycle(Cycle.MONTHLY)
                            .activeTo(LocalDate.of(2026, 6, 23))
                            .amount(new BigDecimal("100.00"))
                            .build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findMaxDueDateByFixedPaymentId(1))
                    .thenReturn(Optional.of(LocalDate.of(2026, 6, 23)));
            when(occurrenceRepository.findDueDatesByFixedPaymentIdAndDueDateBetween(
                            anyInt(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of());

            service.ensureOccurrencesGenerated(userId);

            // when
            // then
            verify(occurrenceRepository, never()).saveAll(any());
        }

        @Test
        void skipsAlreadyExistingOccurrences() {
            // given
            UUID userId = UUID.randomUUID();
            FixedPayment fp =
                    FixedPayment.builder()
                            .id(1)
                            .anchorDate(TODAY)
                            .cycle(Cycle.MONTHLY)
                            .amount(new BigDecimal("50.00"))
                            .build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findMaxDueDateByFixedPaymentId(1))
                    .thenReturn(Optional.of(fp.getAnchorDate().minusDays(1)));
            // All dates already exist
            when(occurrenceRepository.findDueDatesByFixedPaymentIdAndDueDateBetween(
                            anyInt(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of(TODAY, TODAY.plusMonths(1), TODAY.plusMonths(2)));

            service.ensureOccurrencesGenerated(userId);

            // when
            // then
            verify(occurrenceRepository, never()).saveAll(any());
        }

        @Test
        void alreadyUpToDate_doesNotRegenerate() {
            // given
            UUID userId = UUID.randomUUID();
            FixedPayment fp =
                    FixedPayment.builder()
                            .id(1)
                            .anchorDate(TODAY.minusMonths(6))
                            .cycle(Cycle.MONTHLY)
                            .amount(new BigDecimal("100.00"))
                            .build();

            // Last generated is already past the horizon (today + 2 months)
            LocalDate futureDate = TODAY.plusMonths(3);
            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findMaxDueDateByFixedPaymentId(1))
                    .thenReturn(Optional.of(futureDate));

            service.ensureOccurrencesGenerated(userId);

            // when
            // then
            verify(occurrenceRepository, never()).saveAll(any());
        }

        @Test
        void multipleActivePayments_generatesForEach() {
            // given
            UUID userId = UUID.randomUUID();
            FixedPayment weekly =
                    FixedPayment.builder()
                            .id(1)
                            .anchorDate(TODAY)
                            .cycle(Cycle.WEEKLY)
                            .amount(new BigDecimal("25.00"))
                            .build();
            FixedPayment monthly =
                    FixedPayment.builder()
                            .id(2)
                            .anchorDate(TODAY)
                            .cycle(Cycle.MONTHLY)
                            .amount(new BigDecimal("200.00"))
                            .build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(weekly, monthly));
            when(occurrenceRepository.findMaxDueDateByFixedPaymentId(anyInt()))
                    .thenReturn(Optional.empty());
            when(occurrenceRepository.findDueDatesByFixedPaymentIdAndDueDateBetween(
                            anyInt(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of());

            service.ensureOccurrencesGenerated(userId);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FixedPaymentOccurrence>> captor = ArgumentCaptor.forClass(List.class);
            verify(occurrenceRepository, times(2)).saveAll(captor.capture());

            // when
            List<List<FixedPaymentOccurrence>> allSaves = captor.getAllValues();
            // then
            assertThat(allSaves.get(0))
                    .allSatisfy(occ -> assertThat(occ.getFixedPayment()).isEqualTo(weekly));
            assertThat(allSaves.get(1))
                    .allSatisfy(occ -> assertThat(occ.getFixedPayment()).isEqualTo(monthly));
            // Different horizons produce different counts (weekly=5, monthly=3)
            assertThat(allSaves.get(0)).hasSizeGreaterThan(allSaves.get(1).size());
        }

        @Test
        void firstGeneration_noExistingOccurrences_includesAnchorDate() {
            // given
            UUID userId = UUID.randomUUID();
            FixedPayment fp =
                    FixedPayment.builder()
                            .id(1)
                            .anchorDate(TODAY)
                            .cycle(Cycle.MONTHLY)
                            .amount(new BigDecimal("100.00"))
                            .build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findMaxDueDateByFixedPaymentId(1)).thenReturn(Optional.empty());
            when(occurrenceRepository.findDueDatesByFixedPaymentIdAndDueDateBetween(
                            anyInt(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of());

            service.ensureOccurrencesGenerated(userId);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FixedPaymentOccurrence>> captor = ArgumentCaptor.forClass(List.class);
            verify(occurrenceRepository).saveAll(captor.capture());

            // when
            List<FixedPaymentOccurrence> saved = captor.getValue();
            // then
            assertThat(saved).isNotEmpty();
            assertThat(saved.get(0).getDueDate()).isEqualTo(TODAY);
        }

        @Test
        void weeklyCycle_horizonIsFourWeeks() {
            // given
            UUID userId = UUID.randomUUID();
            FixedPayment fp =
                    FixedPayment.builder()
                            .id(1)
                            .anchorDate(TODAY)
                            .cycle(Cycle.WEEKLY)
                            .amount(new BigDecimal("50.00"))
                            .build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findMaxDueDateByFixedPaymentId(1)).thenReturn(Optional.empty());
            when(occurrenceRepository.findDueDatesByFixedPaymentIdAndDueDateBetween(
                            anyInt(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of());

            service.ensureOccurrencesGenerated(userId);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FixedPaymentOccurrence>> captor = ArgumentCaptor.forClass(List.class);
            verify(occurrenceRepository).saveAll(captor.capture());

            // when
            List<FixedPaymentOccurrence> saved = captor.getValue();
            // anchor at TODAY, horizon = TODAY + 4 weeks → 5 weekly occurrences
            // then
            assertThat(saved).hasSize(5);
            assertThat(saved.get(0).getDueDate()).isEqualTo(TODAY);
            assertThat(saved.get(saved.size() - 1).getDueDate()).isEqualTo(TODAY.plusWeeks(4));
        }

        @Test
        void yearlyCycle_horizonIsThirteenMonths() {
            // given
            UUID userId = UUID.randomUUID();
            FixedPayment fp =
                    FixedPayment.builder()
                            .id(1)
                            .anchorDate(TODAY)
                            .cycle(Cycle.YEARLY)
                            .amount(new BigDecimal("1200.00"))
                            .build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findMaxDueDateByFixedPaymentId(1)).thenReturn(Optional.empty());
            when(occurrenceRepository.findDueDatesByFixedPaymentIdAndDueDateBetween(
                            anyInt(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of());

            service.ensureOccurrencesGenerated(userId);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FixedPaymentOccurrence>> captor = ArgumentCaptor.forClass(List.class);
            verify(occurrenceRepository).saveAll(captor.capture());

            // when
            List<FixedPaymentOccurrence> saved = captor.getValue();
            // anchor at TODAY, horizon = TODAY + 13 months → TODAY and TODAY + 1 year
            // then
            assertThat(saved).hasSize(2);
            assertThat(saved.get(0).getDueDate()).isEqualTo(TODAY);
            assertThat(saved.get(1).getDueDate()).isEqualTo(TODAY.plusYears(1));
        }

        @Test
        void partialDuplicates_savesOnlyNewOnes() {
            // given
            UUID userId = UUID.randomUUID();
            LocalDate anchor = TODAY;
            FixedPayment fp =
                    FixedPayment.builder()
                            .id(1)
                            .anchorDate(anchor)
                            .cycle(Cycle.MONTHLY)
                            .amount(new BigDecimal("100.00"))
                            .build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findMaxDueDateByFixedPaymentId(1)).thenReturn(Optional.empty());
            // Anchor date already exists, but later dates do not
            when(occurrenceRepository.findDueDatesByFixedPaymentIdAndDueDateBetween(
                            anyInt(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of(anchor));

            service.ensureOccurrencesGenerated(userId);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FixedPaymentOccurrence>> captor = ArgumentCaptor.forClass(List.class);
            verify(occurrenceRepository).saveAll(captor.capture());

            // when
            List<FixedPaymentOccurrence> saved = captor.getValue();
            // anchor date was skipped; monthly with 2-month horizon: anchor+1m, anchor+2m
            // then
            assertThat(saved).hasSize(2);
            assertThat(saved).noneMatch(occ -> occ.getDueDate().equals(anchor));
        }
    }

    @Nested
    class MarkOverdueOccurrences {

        @Test
        void noActivePayments_doesNothing() {
            // given
            UUID userId = UUID.randomUUID();
            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of());

            service.markOverdueOccurrences(userId);

            // when
            // then
            verify(occurrenceRepository, never()).findPendingOverdueOccurrences(any(), any());
        }

        @Test
        void marksPendingOccurrencesAsOverdue() {
            // given
            UUID userId = UUID.randomUUID();
            FixedPayment fp = FixedPayment.builder().id(1).build();

            FixedPaymentOccurrence occ1 =
                    FixedPaymentOccurrence.builder()
                            .id(1L)
                            .fixedPayment(fp)
                            .dueDate(TODAY.minusDays(5))
                            .status(OccurrenceStatus.PENDING)
                            .build();
            FixedPaymentOccurrence occ2 =
                    FixedPaymentOccurrence.builder()
                            .id(2L)
                            .fixedPayment(fp)
                            .dueDate(TODAY.minusDays(1))
                            .status(OccurrenceStatus.PENDING)
                            .build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findPendingOverdueOccurrences(eq(List.of(1)), any(LocalDate.class)))
                    .thenReturn(List.of(occ1, occ2));

            service.markOverdueOccurrences(userId);

            // when
            assertThat(occ1.getStatus()).isEqualTo(OccurrenceStatus.OVERDUE);
            // then
            assertThat(occ2.getStatus()).isEqualTo(OccurrenceStatus.OVERDUE);
            verify(occurrenceRepository).saveAll(List.of(occ1, occ2));
        }

        @Test
        void multipleActivePayments_passesAllIdsToQuery() {
            // given
            UUID userId = UUID.randomUUID();
            FixedPayment fp1 = FixedPayment.builder().id(1).build();
            FixedPayment fp2 = FixedPayment.builder().id(2).build();
            FixedPayment fp3 = FixedPayment.builder().id(3).build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp1, fp2, fp3));
            when(occurrenceRepository.findPendingOverdueOccurrences(anyList(), any(LocalDate.class)))
                    .thenReturn(List.of());

            service.markOverdueOccurrences(userId);

            // when
            // then
            verify(occurrenceRepository)
                    .findPendingOverdueOccurrences(eq(List.of(1, 2, 3)), any(LocalDate.class));
        }

        @Test
        void noPendingOverdue_savesEmptyList() {
            // given
            UUID userId = UUID.randomUUID();
            FixedPayment fp = FixedPayment.builder().id(1).build();

            when(fixedPaymentRepository.findAllActiveByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(List.of(fp));
            when(occurrenceRepository.findPendingOverdueOccurrences(eq(List.of(1)), any(LocalDate.class)))
                    .thenReturn(List.of());

            service.markOverdueOccurrences(userId);

            // when
            // then
            verify(occurrenceRepository).saveAll(List.of());
        }
    }
}
