package com.mikeshaggy.backend.fixedpayment.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FixedPaymentOccurrenceScheduler {

    private final FixedPaymentOccurrenceMaintenanceService maintenanceService;

    @Scheduled(cron = "0 0 2 * * *")
    public void prepareDailyOccurrences() {
        log.info("Starting scheduled fixed payment occurrence maintenance");
        maintenanceService.prepareAllActiveFixedPayments();
    }
}
