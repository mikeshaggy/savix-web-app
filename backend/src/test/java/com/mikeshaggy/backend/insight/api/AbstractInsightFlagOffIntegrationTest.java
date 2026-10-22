package com.mikeshaggy.backend.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.insight.detector.InsightCandidate;
import com.mikeshaggy.backend.insight.detector.InsightContext;
import com.mikeshaggy.backend.insight.detector.InsightDetector;
import com.mikeshaggy.backend.insight.domain.InsightType;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.repository.UserRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Stage 7 flag off ({@code app.features.insights-v2=false}) through the full HTTP stack, against a database whose
 * {@code insights} table has been <em>dropped</em>: any statement touching it would fail the request. Proves the
 * flag-off path neither evaluates (no detector, no pay-cycle lookup) nor reads/writes the insights table.
 */
abstract class AbstractInsightFlagOffIntegrationTest {

    static final AtomicInteger DETECTOR_CALLS = new AtomicInteger();

    @TestConfiguration
    static class CountingDetector {
        @Bean
        InsightDetector countingDetector() {
            return new InsightDetector() {
                @Override
                public InsightType type() {
                    return InsightType.CATEGORY_ABOVE_TYPICAL;
                }

                @Override
                public Optional<InsightCandidate> detect(InsightContext ctx) {
                    DETECTOR_CALLS.incrementAndGet();
                    return Optional.empty();
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    WalletRepository walletRepository;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @MockitoBean
    PayCycleService payCycleService;

    User user;
    Wallet wallet;
    User otherUser;
    Wallet otherWallet;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("drop table if exists insights");
        DETECTOR_CALLS.set(0);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder().email("off-" + suffix + "@example.com")
                .username("off-" + suffix).passwordHash("hash").build());
        wallet = walletRepository.save(Wallet.builder().user(user).name("Daily").balance(new BigDecimal("1.00")).build());
        otherUser = userRepository.save(User.builder().email("off-other-" + suffix + "@example.com")
                .username("off-other-" + suffix).passwordHash("hash").build());
        otherWallet = walletRepository.save(Wallet.builder().user(otherUser).name("Daily").balance(BigDecimal.ZERO).build());
        org.mockito.Mockito.when(currentUserProvider.getCurrentUserId()).thenReturn(user.getId());
    }

    @AfterEach
    void tearDown() {
        walletRepository.deleteAll(java.util.List.of(wallet, otherWallet));
        userRepository.deleteAll(java.util.List.of(user, otherUser));
    }

    @Test
    void insightsTable_isAbsent() {
        Integer tables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where lower(table_name) = 'insights' "
                        + "and table_schema = current_schema()", Integer.class);
        assertThat(tables).isZero();
    }

    @Test
    void get_returns200FeatureDisabled_withoutEvaluatingOrTouchingTheInsightsTable() throws Exception {
        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(wallet.getId()))
                .andExpect(jsonPath("$.status").value("FEATURE_DISABLED"))
                .andExpect(jsonPath("$.cycleStart").doesNotExist())
                .andExpect(jsonPath("$.cycleEnd").doesNotExist())
                .andExpect(jsonPath("$.cycleState").doesNotExist())
                .andExpect(jsonPath("$.insights").isEmpty());

        assertThat(DETECTOR_CALLS).hasValue(0);
        verifyNoInteractions(payCycleService);
    }

    @Test
    void get_foreignWallet_isStill404_beforeTheFlagIsConsulted() throws Exception {
        mockMvc.perform(get("/api/insights").param("walletId", otherWallet.getId().toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/insights").param("walletId", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_is404_withoutTouchingTheInsightsTable() throws Exception {
        mockMvc.perform(delete("/api/insights/1")).andExpect(status().isNotFound());
    }
}
