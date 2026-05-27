package com.mikeshaggy.backend.fixedpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.CreateFixedPaymentRequest;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.UpdateFixedPaymentRequest;
import com.mikeshaggy.backend.fixedpayment.maintenance.FixedPaymentOccurrenceMaintenanceService;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@DataJpaTest
class FixedPaymentDashboardMaintenanceReadinessTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 11);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-03-11T00:00:00Z"), ZoneOffset.UTC);
    private static final PeriodDto PERIOD = new PeriodDto(
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 4, 5),
            PeriodType.PAY_CYCLE);

    @Autowired
    private FixedPaymentRepository fixedPaymentRepository;

    @Autowired
    private FixedPaymentOccurrenceRepository occurrenceRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User user;
    private Wallet wallet;
    private Category category;
    private WalletService walletService;
    private CategoryService categoryService;
    private TransactionService transactionService;
    private FixedPaymentCrudService crudService;
    private FixedPaymentOccurrenceMaintenanceService maintenanceService;
    private FixedPaymentDashboardService dashboardService;

    @BeforeEach
    void setUp() {
        user = entityManager.persist(User.builder()
                .email("fixed-readiness@example.com")
                .username("fixed-readiness")
                .passwordHash("hash")
                .build());
        wallet = entityManager.persist(Wallet.builder()
                .user(user)
                .name("Main")
                .balance(new BigDecimal("1000.00"))
                .build());
        category = entityManager.persist(Category.builder()
                .user(user)
                .name("Rent")
                .type(CategoryType.EXPENSE)
                .emoji("H")
                .build());
        entityManager.flush();

        walletService = mock(WalletService.class);
        categoryService = mock(CategoryService.class);
        transactionService = mock(TransactionService.class);
        PeriodService periodService = mock(PeriodService.class);

        FixedPaymentOccurrenceGenerationService generationService =
                new FixedPaymentOccurrenceGenerationService(fixedPaymentRepository, occurrenceRepository, CLOCK);
        maintenanceService =
                new FixedPaymentOccurrenceMaintenanceService(generationService, fixedPaymentRepository, CLOCK);
        crudService = new FixedPaymentCrudService(
                fixedPaymentRepository,
                occurrenceRepository,
                maintenanceService,
                walletService,
                categoryService,
                CLOCK);
        FixedPaymentTileAssembler tileAssembler = new FixedPaymentTileAssembler(CLOCK);
        dashboardService = new FixedPaymentDashboardService(
                fixedPaymentRepository,
                occurrenceRepository,
                tileAssembler,
                walletService,
                transactionService,
                periodService,
                CLOCK);

        when(walletService.getWalletEntityByIdForUser(wallet.getId(), user.getId())).thenReturn(wallet);
        when(categoryService.getCategoryEntityByIdForUser(category.getId(), user.getId())).thenReturn(category);
        when(transactionService.sumIncomeByWalletIdAndDateRange(
                eq(wallet.getId()), eq(user.getId()), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    void dashboardReadsPreparedOccurrencesAfterFixedPaymentCreateWithoutDashboardMaintenance() {
        crudService.createFixedPayment(new CreateFixedPaymentRequest(
                wallet.getId(),
                category.getId(),
                "Rent",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 12),
                Cycle.MONTHLY,
                LocalDate.of(2026, 3, 1),
                null,
                null), user.getId());
        flushAndClear();

        FixedTransactionsTileDto tile = readDashboardTile();

        assertThat(tile.summary().plannedAmount()).isEqualByComparingTo("150.00");
        assertThat(tile.summary().plannedCount()).isEqualTo(1);
        assertThat(tile.summary().remainingAmount()).isEqualByComparingTo("150.00");
        assertThat(tile.progress().nextDueDate()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(tile.upcoming()).hasSize(1);
        assertThat(tile.upcoming().getFirst().title()).isEqualTo("Rent");
    }

    @Test
    void dashboardReadsUpdatedOccurrencesAfterFixedPaymentUpdateWithoutDashboardMaintenance() {
        var created = crudService.createFixedPayment(new CreateFixedPaymentRequest(
                wallet.getId(),
                category.getId(),
                "Rent",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 12),
                Cycle.MONTHLY,
                LocalDate.of(2026, 3, 1),
                null,
                null), user.getId());
        flushAndClear();

        crudService.updateFixedPayment(created.id(), new UpdateFixedPaymentRequest(
                "Updated Rent",
                new BigDecimal("225.00"),
                LocalDate.of(2026, 3, 12),
                Cycle.MONTHLY,
                null,
                null), user.getId());
        flushAndClear();

        FixedTransactionsTileDto tile = readDashboardTile();

        assertThat(tile.summary().plannedAmount()).isEqualByComparingTo("225.00");
        assertThat(tile.summary().plannedCount()).isEqualTo(1);
        assertThat(tile.progress().nextDueName()).isEqualTo("Updated Rent");
        assertThat(tile.upcoming()).hasSize(1);
        assertThat(tile.upcoming().getFirst().expectedAmount()).isEqualByComparingTo("225.00");
    }

    @Test
    void dashboardReadsPreparedOccurrencesAfterSchedulerMaintenanceWithoutDashboardMaintenance() {
        persistFixedPayment("Rent", "175.00", LocalDate.of(2026, 3, 12), null);
        flushAndClear();

        maintenanceService.prepareAllActiveFixedPayments();
        flushAndClear();

        FixedTransactionsTileDto tile = readDashboardTile();

        assertThat(tile.summary().plannedAmount()).isEqualByComparingTo("175.00");
        assertThat(tile.summary().remainingCount()).isEqualTo(1);
        assertThat(tile.upcoming()).hasSize(1);
        assertThat(tile.upcoming().getFirst().dueDate()).isEqualTo(LocalDate.of(2026, 3, 12));
    }

    @Test
    void dashboardReadsOverdueOccurrencesAfterSchedulerMaintenanceWithoutDashboardMaintenance() {
        persistFixedPayment("Past Rent", "175.00", LocalDate.of(2026, 3, 1), TODAY);
        flushAndClear();

        maintenanceService.prepareAllActiveFixedPayments();
        flushAndClear();

        FixedTransactionsTileDto tile = readDashboardTile();

        assertThat(tile.summary().plannedAmount()).isEqualByComparingTo("175.00");
        assertThat(tile.summary().overdueAmount()).isEqualByComparingTo("175.00");
        assertThat(tile.summary().overdueCount()).isEqualTo(1);
        assertThat(tile.summary().remainingCount()).isZero();
        assertThat(tile.overdue()).hasSize(1);
        assertThat(tile.overdue().getFirst().status()).isEqualTo(OccurrenceStatus.OVERDUE);
    }

    @Test
    void paidOccurrenceIsNotRegeneratedBeforeDashboardRead() {
        FixedPayment payment = persistFixedPayment("Rent", "175.00", LocalDate.of(2026, 3, 12), null);
        entityManager.persist(FixedPaymentOccurrence.builder()
                .fixedPayment(payment)
                .dueDate(LocalDate.of(2026, 3, 12))
                .expectedAmount(new BigDecimal("175.00"))
                .paidAmount(new BigDecimal("175.00"))
                .status(OccurrenceStatus.PAID)
                .build());
        flushAndClear();

        maintenanceService.prepareAllActiveFixedPayments();
        flushAndClear();

        FixedTransactionsTileDto tile = readDashboardTile();
        List<LocalDate> dueDates = occurrenceRepository.findDueDatesByFixedPaymentIdAndDueDateBetween(
                payment.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(dueDates).containsExactly(LocalDate.of(2026, 3, 12));
        assertThat(tile.summary().plannedCount()).isEqualTo(1);
        assertThat(tile.summary().paidCount()).isEqualTo(1);
        assertThat(tile.paid()).hasSize(1);
        assertThat(tile.upcoming()).isEmpty();
    }

    @Test
    void maintenanceDoesNotGenerateOccurrencesAfterActiveToBeforeDashboardRead() {
        persistFixedPayment(
                "Short Contract",
                "175.00",
                LocalDate.of(2026, 3, 12),
                LocalDate.of(2026, 3, 12));
        flushAndClear();

        maintenanceService.prepareAllActiveFixedPayments();
        flushAndClear();

        FixedTransactionsTileDto tile = readDashboardTile();

        assertThat(tile.summary().plannedAmount()).isEqualByComparingTo("175.00");
        assertThat(tile.summary().plannedCount()).isEqualTo(1);
        assertThat(tile.upcoming()).hasSize(1);
        assertThat(tile.upcoming().getFirst().dueDate()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(tile.upcoming()).noneSatisfy(row -> assertThat(row.dueDate()).isAfter(LocalDate.of(2026, 3, 12)));
    }

    @Test
    void activeToShorteningRemovesStalePendingOccurrencesButPreservesPaidHistoryBeforeDashboardRead() {
        var created = crudService.createFixedPayment(new CreateFixedPaymentRequest(
                wallet.getId(),
                category.getId(),
                "Rent",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 12),
                Cycle.MONTHLY,
                LocalDate.of(2026, 3, 1),
                null,
                null), user.getId());
        flushAndClear();

        FixedPayment payment = fixedPaymentRepository.findById(created.id()).orElseThrow();
        persistOccurrence(payment, LocalDate.of(2026, 3, 20), OccurrenceStatus.PENDING);
        persistPaidOccurrence(payment, LocalDate.of(2026, 3, 21));
        flushAndClear();

        crudService.updateFixedPayment(created.id(), new UpdateFixedPaymentRequest(
                "Rent",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 12),
                Cycle.MONTHLY,
                null,
                LocalDate.of(2026, 3, 12)), user.getId());
        flushAndClear();

        maintenanceService.prepareAllActiveFixedPayments();
        flushAndClear();

        FixedTransactionsTileDto tile = readDashboardTile();

        assertThat(occurrenceRepository.findPendingAfterActiveTo(created.id(), LocalDate.of(2026, 3, 12))).isEmpty();
        assertThat(tile.upcoming()).noneSatisfy(row -> assertThat(row.dueDate()).isAfter(LocalDate.of(2026, 3, 12)));
        assertThat(tile.paid())
                .anySatisfy(row -> {
                    assertThat(row.dueDate()).isEqualTo(LocalDate.of(2026, 3, 21));
                    assertThat(row.status()).isEqualTo(OccurrenceStatus.PAID);
                });
    }

    private FixedTransactionsTileDto readDashboardTile() {
        return dashboardService.getFixedPaymentsTileData(PERIOD, wallet.getId(), user.getId());
    }

    private FixedPayment persistFixedPayment(String title, String amount, LocalDate anchorDate, LocalDate activeTo) {
        return entityManager.persist(FixedPayment.builder()
                .wallet(wallet)
                .category(category)
                .title(title)
                .amount(new BigDecimal(amount))
                .anchorDate(anchorDate)
                .cycle(Cycle.MONTHLY)
                .activeFrom(LocalDate.of(2026, 3, 1))
                .activeTo(activeTo)
                .build());
    }

    private FixedPaymentOccurrence persistOccurrence(
            FixedPayment payment, LocalDate dueDate, OccurrenceStatus status) {
        return entityManager.persist(FixedPaymentOccurrence.builder()
                .fixedPayment(payment)
                .dueDate(dueDate)
                .expectedAmount(payment.getAmount())
                .status(status)
                .build());
    }

    private FixedPaymentOccurrence persistPaidOccurrence(FixedPayment payment, LocalDate dueDate) {
        return entityManager.persist(FixedPaymentOccurrence.builder()
                .fixedPayment(payment)
                .dueDate(dueDate)
                .expectedAmount(payment.getAmount())
                .paidAmount(payment.getAmount())
                .status(OccurrenceStatus.PAID)
                .build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
