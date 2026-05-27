package com.mikeshaggy.backend.fixedpayment.maintenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FixedPaymentOccurrenceSchedulerTest {

    @Mock
    private FixedPaymentOccurrenceMaintenanceService maintenanceService;

    @InjectMocks
    private FixedPaymentOccurrenceScheduler scheduler;

    @Test
    void prepareDailyOccurrencesRunsAllActiveMaintenance() {
        scheduler.prepareDailyOccurrences();

        verify(maintenanceService).prepareAllActiveFixedPayments();
    }
}
