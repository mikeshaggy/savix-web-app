package com.mikeshaggy.backend.insight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.insight.detector.InsightCandidate;
import com.mikeshaggy.backend.insight.domain.Insight;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import com.mikeshaggy.backend.insight.dto.InsightListResponse;
import com.mikeshaggy.backend.insight.repository.InsightRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.repository.UserRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Complements {@link InsightEvaluationSingleConnectionTest} for the inputs that only touch the database when closed
 * cycles exist and Forecast v2 is on (the sibling test's mocked pay-cycle history is empty and its forecast flag off, so
 * {@code cycleHistory}, the category baseline and the forecast return before they query). With a real history, Forecast
 * v2 enabled and a pool of exactly one connection, reading every Stage 7.3 input in one detection phase must still need
 * a single connection at a time — a bare repository call outside a transaction would pin it and time out here.
 */
@SpringBootTest(properties = {
        "app.features.insights-v2=true",
        "app.features.pay-cycle-v2=true",
        "app.features.forecast-v2=true",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=2000"
})
@Import(AbstractInsightEvaluationIntegrationTest.Detectors.class)
class InsightHistoryInputsSingleConnectionTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 9);

    @Autowired
    private InsightService insightService;

    @Autowired
    private InsightRepository insightRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @MockitoBean
    private PayCycleService payCycleService;

    @MockitoBean
    private PeriodService periodService;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        AbstractInsightEvaluationIntegrationTest.BARRIER.set(null);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder().email("history-" + suffix + "@example.com")
                .username("history-" + suffix).passwordHash("hash").build());
        wallet = walletRepository.save(Wallet.builder().user(user).name("Daily").balance(new BigDecimal("1500.00"))
                .build());
        PayCycle cycle = PayCycle.open(user.getId(), wallet.getId(), START, START.plusMonths(1));
        when(payCycleService.current(user.getId())).thenReturn(Optional.of(cycle));
        List<PayCycle> closed = List.of(
                PayCycle.closed(user.getId(), wallet.getId(), START.minusMonths(1), START.minusDays(1)),
                PayCycle.closed(user.getId(), wallet.getId(), START.minusMonths(2), START.minusMonths(1).minusDays(1)),
                PayCycle.closed(user.getId(), wallet.getId(), START.minusMonths(3), START.minusMonths(2).minusDays(1)));
        when(payCycleService.history(eq(user.getId()), anyInt())).thenReturn(closed);
        when(periodService.resolve(eq(PeriodType.PAY_CYCLE), eq(wallet.getId()), eq(user.getId()), any(), any()))
                .thenReturn(new PeriodDto(cycle.start(), cycle.end(), cycle.expectedNextAnchor(), PeriodType.PAY_CYCLE,
                        CycleState.OPEN, cycle.expectedNextAnchor(), true));
    }

    @AfterEach
    void cleanUp() {
        AbstractInsightEvaluationIntegrationTest.RULE.set(null);
        insightRepository.deleteAll(rows());
        walletRepository.delete(wallet);
        userRepository.delete(user);
    }

    @Test
    void everyInputReadsWithASingleConnection_whenClosedCyclesExistAndForecastV2IsOn() {
        AbstractInsightEvaluationIntegrationTest.RULE.set(ctx -> {
            assertThat(ctx.cycleHistory()).hasSize(3);
            assertThat(ctx.categoryDayBaseline().cyclesUsed()).isEqualTo(3);
            assertThat(ctx.forecast()).isPresent();
            ctx.transferSummary();
            ctx.sparkline();
            ctx.impulseSplit();
            ctx.expenseHistory();
            ctx.fixedPayments();
            ctx.savingsWallet();
            ctx.fixedPaymentRows();
            return Optional.of(new InsightCandidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                    "category:3", "t", null, null, null, null));
        });

        InsightListResponse response = insightService.evaluate(wallet.getId(), user.getId());

        // the rule's own candidate was stored (its reads did not time out); the real detectors ran in the same
        // single-connection evaluation and the closed cycle was summarised from the same history
        assertThat(response.insights()).extracting(insight -> insight.type())
                .contains(InsightType.CATEGORY_ABOVE_TYPICAL, InsightType.CYCLE_CLOSED);
        assertThat(rows()).extracting(Insight::getType)
                .contains(InsightType.CATEGORY_ABOVE_TYPICAL, InsightType.CYCLE_CLOSED);
    }

    private List<Insight> rows() {
        return insightRepository.findAll().stream().filter(i -> i.getUserId().equals(user.getId())).toList();
    }
}
