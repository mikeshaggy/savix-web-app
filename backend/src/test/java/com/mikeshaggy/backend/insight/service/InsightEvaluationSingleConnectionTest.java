package com.mikeshaggy.backend.insight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Regression guard (Session W tester finding): with no caller transaction, an evaluation holds at most one pooled
 * connection at a time — detection releases its connection before any insert — so concurrent first-time
 * evaluations cannot starve the pool by each holding one connection while waiting for a second. Proven with a pool
 * of exactly one connection, including the lost-race path (real unique violation absorbed, winner re-read).
 */
@SpringBootTest(properties = {
        "app.features.insights-v2=true",
        "app.features.pay-cycle-v2=true",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=2000"
})
@Import(AbstractInsightEvaluationIntegrationTest.Detectors.class)
class InsightEvaluationSingleConnectionTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 9);

    @Autowired
    private InsightService insightService;

    @MockitoSpyBean
    private InsightRepository insightRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @MockitoBean
    private PayCycleService payCycleService;

    @MockitoBean
    private PeriodService periodService;

    @MockitoSpyBean
    private InsightWriter insightWriter;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        AbstractInsightEvaluationIntegrationTest.BARRIER.set(null);
        AbstractInsightEvaluationIntegrationTest.RULE.set(ctx -> Optional.of(new InsightCandidate(
                InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "category:3", "t", null, null, null, null)));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder().email("single-" + suffix + "@example.com")
                .username("single-" + suffix).passwordHash("hash").build());
        wallet = walletRepository.save(Wallet.builder().user(user).name("Daily").balance(BigDecimal.ZERO).build());
        PayCycle cycle = PayCycle.open(user.getId(), wallet.getId(), START, START.plusMonths(1));
        when(payCycleService.current(user.getId())).thenReturn(Optional.of(cycle));
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
    void firstTimeEvaluation_storesWithASingleConnection() {
        InsightListResponse response = insightService.evaluate(wallet.getId(), user.getId());

        assertThat(response.insights()).hasSize(1);
        assertThat(rows()).hasSize(1);
    }

    @Test
    void detectorReadingContextInputs_thenStoring_needsASingleConnection() {
        // reviewer MINOR-2: detection that really reads (the Stage 6.2 transfer summary through its repositories)
        // must release its connection before the insert phase
        AbstractInsightEvaluationIntegrationTest.RULE.set(ctx -> {
            assertThat(ctx.transferSummary().walletId()).isEqualTo(wallet.getId());
            return Optional.of(new InsightCandidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                    "category:3", "t", null, null, null, null));
        });

        InsightListResponse response = insightService.evaluate(wallet.getId(), user.getId());

        assertThat(response.insights()).hasSize(1);
        assertThat(rows()).hasSize(1);
    }

    @Test
    void lostRace_isAbsorbedWithASingleConnection() {
        // the winner's row is already committed, but our pre-check is made to miss it (as in a real race), so the
        // insert hits the unique constraint for real and the re-check must find the winner
        insightRepository.saveAndFlush(Insight.builder()
                .userId(user.getId()).walletId(wallet.getId()).type(InsightType.CATEGORY_ABOVE_TYPICAL)
                .severity(InsightSeverity.WARN).cycleStart(START).dedupeKey("category:3").title("winner").build());
        doReturn(false).doReturn(true).when(insightRepository)
                .existsByWalletTypeCycleAndKey(any(), any(), any(), any());

        InsightListResponse response = insightService.evaluate(wallet.getId(), user.getId());

        verify(insightWriter).insert(any());
        assertThat(response.insights()).singleElement()
                .satisfies(insight -> assertThat(insight.title()).isEqualTo("winner"));
        assertThat(rows()).hasSize(1);
    }

    private List<Insight> rows() {
        return insightRepository.findAll().stream().filter(i -> i.getUserId().equals(user.getId())).toList();
    }
}
