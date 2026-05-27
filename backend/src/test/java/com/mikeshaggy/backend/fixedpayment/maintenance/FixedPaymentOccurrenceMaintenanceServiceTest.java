package com.mikeshaggy.backend.fixedpayment.maintenance;

import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentOccurrenceGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class FixedPaymentOccurrenceMaintenanceServiceTest {

    @Mock
    private FixedPaymentOccurrenceGenerationService generationService;

    @Mock
    private FixedPaymentRepository fixedPaymentRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-11T00:00:00Z"), ZoneOffset.UTC);

    private FixedPaymentOccurrenceMaintenanceService maintenanceService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        maintenanceService =
                new FixedPaymentOccurrenceMaintenanceService(generationService, fixedPaymentRepository, clock);
    }

    @Nested
    class PrepareOccurrencesAfterFixedPaymentMutation {

        @Test
        void delegatesGenerationAndOverdueMarkingInOrder() {
            maintenanceService.prepareOccurrencesAfterFixedPaymentMutation(USER_ID);

            InOrder inOrder = inOrder(generationService);
            inOrder.verify(generationService).ensureOccurrencesGenerated(USER_ID);
            inOrder.verify(generationService).deletePendingOccurrencesAfterActiveTo(USER_ID);
            inOrder.verify(generationService).markOverdueOccurrences(USER_ID);
        }
    }

    @Nested
    class PrepareAllActiveFixedPayments {

        @Test
        void preparesEveryActiveUser() {
            when(fixedPaymentRepository.findActiveUserIds(LocalDate.of(2026, 3, 11)))
                    .thenReturn(List.of(USER_ID, OTHER_USER_ID));

            maintenanceService.prepareAllActiveFixedPayments();

            verify(generationService).ensureOccurrencesGenerated(USER_ID);
            verify(generationService).deletePendingOccurrencesAfterActiveTo(USER_ID);
            verify(generationService).markOverdueOccurrences(USER_ID);
            verify(generationService).ensureOccurrencesGenerated(OTHER_USER_ID);
            verify(generationService).deletePendingOccurrencesAfterActiveTo(OTHER_USER_ID);
            verify(generationService).markOverdueOccurrences(OTHER_USER_ID);
        }

        @Test
        void repeatedRunsRerunOnlyIdempotentGenerationAndOverdueSteps() {
            when(fixedPaymentRepository.findActiveUserIds(LocalDate.of(2026, 3, 11)))
                    .thenReturn(List.of(USER_ID));

            maintenanceService.prepareAllActiveFixedPayments();
            maintenanceService.prepareAllActiveFixedPayments();

            verify(generationService, times(2)).ensureOccurrencesGenerated(USER_ID);
            verify(generationService, times(2)).deletePendingOccurrencesAfterActiveTo(USER_ID);
            verify(generationService, times(2)).markOverdueOccurrences(USER_ID);
            verifyNoMoreInteractions(generationService);
        }
    }
}
