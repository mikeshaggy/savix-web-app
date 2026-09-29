package com.mikeshaggy.backend.analytics.forecast;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.regression.September2026Fixture;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mikeshaggy.backend.regression.September2026Fixture.PAY_CYCLE_PERIOD_V2;
import static com.mikeshaggy.backend.regression.September2026Fixture.SALARY_WALLET_ID;
import static com.mikeshaggy.backend.regression.September2026Fixture.TODAY;
import static com.mikeshaggy.backend.regression.September2026Fixture.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 4.8 safety, proven against a real {@code JpaTransactionManager} (H2): the consumers call the observer from
 * inside their own read-only transaction, and a v2 failure raised by a participating {@code @Transactional} bean
 * would mark that transaction rollback-only — the legacy request would then fail at commit with
 * {@code UnexpectedRollbackException} although the observer caught the exception. The observer therefore runs the
 * shadow computation in its own {@code REQUIRES_NEW} transaction; this test pins that the caller still commits.
 */
@DataJpaTest
@Import(ForecastShadowObserverTransactionTest.ThrowingForecastConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // no test-managed transaction: the templates below commit for real
class ForecastShadowObserverTransactionTest {

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ForecastService throwingForecastService;
    @Autowired
    private ThrowingForecastConfig.Calls calls;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void captureLog() {
        logger = (Logger) LoggerFactory.getLogger(ForecastShadowObserver.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void aThrowingTransactionalForecastServiceDoesNotPoisonTheCallersReadOnlyTransaction() {
        ForecastShadowObserver observer = new ForecastShadowObserver(
                new FeatureFlags(false, false, true, false, false), throwingForecastService, transactionManager);
        Wallet wallet = Wallet.builder().id(SALARY_WALLET_ID).balance(September2026Fixture.SALARY_WALLET_BALANCE).build();
        TransactionTemplate legacyRequest = new TransactionTemplate(transactionManager);
        legacyRequest.setReadOnly(true);

        // the consumer: legacy work, then the shadow hand-off, then a normal commit
        String response = legacyRequest.execute(status -> {
            observer.observe(wallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, null, ForecastShadowObserverTest.legacy(), "DANGER");
            assertThat(status.isRollbackOnly()).as("caller's transaction must not be marked rollback-only").isFalse();
            return "legacy payload";
        });

        assertThat(response).isEqualTo("legacy payload");
        assertThat(calls.count.get()).isEqualTo(1);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.getFirst().getFormattedMessage()).startsWith("forecast.shadow failed")
                .contains("shadow query exploded");
        assertThat(calls.ranInsideItsOwnTransaction).isTrue();
    }

    @Test
    void theCallersTransactionStaysReadOnlyAndSeparate() {
        ForecastShadowObserver observer = new ForecastShadowObserver(
                new FeatureFlags(false, false, true, false, false), throwingForecastService, transactionManager);
        Wallet wallet = Wallet.builder().id(SALARY_WALLET_ID).balance(September2026Fixture.SALARY_WALLET_BALANCE).build();
        TransactionTemplate legacyRequest = new TransactionTemplate(transactionManager);
        legacyRequest.setReadOnly(true);
        legacyRequest.setName("legacy-request");

        legacyRequest.execute(status -> {
            observer.observe(wallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, null, ForecastShadowObserverTest.legacy(), null);
            // after the shadow hand-off the caller is back in its own, still-active, unnamed-by-the-shadow transaction
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionName()).isEqualTo("legacy-request");
            return null;
        });
        // the shadow work ran in a transaction of its own, not the caller's
        assertThat(calls.ranInsideItsOwnTransaction).isTrue();
        assertThat(calls.seenTransactionName).isNotEqualTo("legacy-request");
    }

    /** A real Spring-proxied {@code @Transactional} orchestrator that always fails — the participating-bean case. */
    static class ThrowingForecastConfig {

        static class Calls {
            final AtomicInteger count = new AtomicInteger();
            volatile boolean ranInsideItsOwnTransaction;
            volatile String seenTransactionName;
        }

        @Bean
        Calls calls() {
            return new Calls();
        }

        @Bean
        ForecastService throwingForecastService(Calls calls) {
            return new ThrowingForecastService(calls);
        }

        @Transactional(readOnly = true)
        static class ThrowingForecastService extends ForecastService {
            private final Calls calls;

            ThrowingForecastService(Calls calls) {
                super(null, null, null, null, null, null);
                this.calls = calls;
            }

            @Override
            public boolean isApplicable(PeriodDto period) {
                return true;
            }

            @Override
            public ForecastV2Dto forecast(Wallet wallet, UUID userId, PeriodDto period, LocalDate asOfDate,
                                          FixedTransactionsTileDto tile) {
                calls.count.incrementAndGet();
                calls.ranInsideItsOwnTransaction = TransactionSynchronizationManager.isActualTransactionActive();
                calls.seenTransactionName = TransactionSynchronizationManager.getCurrentTransactionName();
                throw new IllegalStateException("shadow query exploded");
            }
        }
    }
}
