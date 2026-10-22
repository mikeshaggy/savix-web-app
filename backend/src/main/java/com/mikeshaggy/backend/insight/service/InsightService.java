package com.mikeshaggy.backend.insight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.insight.detector.InsightCandidate;
import com.mikeshaggy.backend.insight.detector.InsightContext;
import com.mikeshaggy.backend.insight.domain.Insight;
import com.mikeshaggy.backend.insight.dto.InsightAvailability;
import com.mikeshaggy.backend.insight.dto.InsightListResponse;
import com.mikeshaggy.backend.insight.dto.InsightResponse;
import com.mikeshaggy.backend.insight.repository.InsightRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.JDBCException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Insights v2 evaluation (Stage 7.2): evaluate-on-read, persisted and deduplicated per cycle (decision I4).
 * <p>
 * {@link #evaluate}: authorise the wallet → gate (flag, salary wallet's current pay-cycle-v2 cycle) → build a
 * lazy {@link InsightContext} → run every registered {@link InsightDetector} → validate → store candidates that
 * are new for {@code (wallet, type, cycle start, dedupe key)} → return the cycle's non-dismissed insights, most
 * severe first, then oldest first.
 * <p>
 * Transactions: detection runs in {@link InsightDetectorRunner} with {@link Propagation#NOT_SUPPORTED} — a caller's
 * transaction is suspended, context reads run in their own read-only transactions (a failing read cannot mark the
 * caller rollback-only), and whatever that scope held is released before anything is stored. Each insert then runs
 * in its own read-write transaction ({@link InsightWriter}), never through a read-only one. Without a caller
 * transaction an evaluation therefore needs one connection at a time; a caller that wraps {@code evaluate} in its
 * own transaction keeps that connection while each insert takes a second one (Stage 7.4: call it outside the
 * dashboard's read-only transaction).
 * <p>
 * Concurrency: the database unique constraint is the guarantee. A pre-check skips known events cheaply; when two
 * evaluations race past it, the loser's insert fails with a unique violation in its own transaction, which is
 * recognised (SQLState 23505 <em>and</em> the row now exists) and treated as "already stored". Any other
 * database error propagates.
 * <p>
 * Failure isolation: a detector that throws (including a failing context input) or returns an invalid candidate
 * is logged and skipped for this evaluation ({@link InsightDetectorRunner}); the remaining detectors still run and
 * the request succeeds.
 */
@Slf4j
@Service
public class InsightService {

    private static final String UNIQUE_VIOLATION = "23505";

    private static final Comparator<Insight> DISPLAY_ORDER = Comparator
            .comparing(Insight::getSeverity, Comparator.reverseOrder())
            .thenComparing(Insight::getId);

    private final WalletService walletService;
    private final PayCycleService payCycleService;
    private final PeriodService periodService;
    private final InsightContextFactory contextFactory;
    private final InsightRepository insightRepository;
    private final InsightWriter insightWriter;
    private final InsightDetectorRunner detectorRunner;
    private final FeatureFlags featureFlags;
    private final ObjectMapper objectMapper;
    private final ObjectReader payloadReader;
    private final Clock clock;

    public InsightService(WalletService walletService,
                          PayCycleService payCycleService,
                          PeriodService periodService,
                          InsightContextFactory contextFactory,
                          InsightRepository insightRepository,
                          InsightWriter insightWriter,
                          InsightDetectorRunner detectorRunner,
                          FeatureFlags featureFlags,
                          ObjectMapper objectMapper,
                          Clock clock) {
        this.walletService = walletService;
        this.payCycleService = payCycleService;
        this.periodService = periodService;
        this.contextFactory = contextFactory;
        this.insightRepository = insightRepository;
        this.insightWriter = insightWriter;
        this.detectorRunner = detectorRunner;
        this.featureFlags = featureFlags;
        this.objectMapper = objectMapper;
        // decimals stay BigDecimal with their scale (162.30 is not turned into 162.3 or a double)
        this.payloadReader = objectMapper.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .with(JsonNodeFactory.withExactBigDecimals(true));
        this.clock = clock;
    }

    /**
     * Not transactional itself: each step owns its transaction (see the class comment).
     * <p>
     * <strong>Transaction boundary (Stage 7.4):</strong> must NOT be called from inside a caller's transaction —
     * in particular not from {@code DashboardSummaryService}, which is {@code @Transactional(readOnly = true)} at
     * class level. The caller's suspended transaction keeps its pooled connection while detection and every insert
     * take another, so concurrent dashboard reads can exhaust the pool (and detector failures would then surface as
     * "no insights"). Call it from the controller or a non-transactional facade. A call inside an active
     * transaction is logged as {@code insight.evaluate.inside-transaction}.
     */
    public InsightListResponse evaluate(Integer walletId, UUID userId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("insight.evaluate.inside-transaction walletId={} — call InsightService.evaluate outside the "
                    + "caller's transaction (see its javadoc); this request holds an extra pooled connection", walletId);
        }
        Wallet wallet = walletService.getWalletEntityByIdForUser(walletId, userId);
        if (!featureFlags.insightsV2()) {
            return InsightListResponse.unavailable(walletId, InsightAvailability.FEATURE_DISABLED);
        }
        Optional<PayCycle> current = payCycleService.current(userId);
        if (current.isEmpty()) {
            return InsightListResponse.unavailable(walletId, InsightAvailability.NO_PAY_CYCLE);
        }
        PayCycle cycle = current.get();
        if (!walletId.equals(cycle.salaryWalletId())) {
            return InsightListResponse.unavailable(walletId, InsightAvailability.NOT_SALARY_WALLET);
        }
        PeriodDto period = periodService.resolve(PeriodType.PAY_CYCLE, walletId, userId, null, null);
        if (!isPeriodOf(period, cycle)) {
            return InsightListResponse.unavailable(walletId, InsightAvailability.PAY_CYCLE_V2_INACTIVE);
        }

        InsightContext context = contextFactory.create(userId, wallet, cycle, period, LocalDate.now(clock));
        for (InsightCandidate candidate : detectorRunner.detect(context)) {
            String payload;
            try {
                payload = serialise(candidate.payload());
            } catch (IllegalArgumentException e) {
                InsightDetectorRunner.invalid(candidate.type(), context, e.getMessage());
                continue;
            }
            storeIfNew(context, candidate, payload);
        }

        List<InsightResponse> insights = insightRepository
                .findActiveByWalletAndCycle(userId, walletId, cycle.start())
                .stream()
                .sorted(DISPLAY_ORDER)
                .map(this::toResponse)
                .toList();
        return new InsightListResponse(walletId, InsightAvailability.AVAILABLE, cycle.start(), cycle.end(),
                cycle.state(), insights);
    }

    /**
     * Dismisses the user's insight for good (sets {@code dismissed_at}; the row and its dedupe key stay).
     * Idempotent. An unknown id, another user's id, or the flag being off → {@link EntityNotFoundException} (404).
     */
    @Transactional
    public void dismiss(Long id, UUID userId) {
        if (!featureFlags.insightsV2()) {
            throw notFound(id);
        }
        int updated = insightRepository.dismiss(id, userId, Instant.now(clock));
        if (updated == 0 && insightRepository.findByIdAndUserId(id, userId).isEmpty()) {
            throw notFound(id);
        }
    }

    /** The resolved PAY_CYCLE period is the pay-cycle-v2 period of this very cycle (legacy resolution is not). */
    private static boolean isPeriodOf(PeriodDto period, PayCycle cycle) {
        return period != null
                && period.periodType() == PeriodType.PAY_CYCLE
                && Boolean.TRUE.equals(period.salaryWallet())
                && period.cycleState() == cycle.state()
                && cycle.start().equals(period.startDate());
    }

    private void storeIfNew(InsightContext context, InsightCandidate candidate, String payload) {
        Integer walletId = context.wallet().id();
        LocalDate cycleStart = context.cycle().start();
        if (insightRepository.existsByWalletTypeCycleAndKey(walletId, candidate.type(), cycleStart,
                candidate.dedupeKey())) {
            return;
        }
        Insight insight = Insight.builder()
                .userId(context.userId())
                .walletId(walletId)
                .type(candidate.type())
                .severity(candidate.severity())
                .cycleStart(cycleStart)
                .dedupeKey(candidate.dedupeKey())
                .title(candidate.title())
                .description(candidate.description())
                .payload(payload)
                .actionType(candidate.actionType())
                .actionRef(candidate.actionRef())
                .build();
        try {
            insightWriter.insert(insight);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e) && insightRepository.existsByWalletTypeCycleAndKey(walletId,
                    candidate.type(), cycleStart, candidate.dedupeKey())) {
                log.debug("insight.duplicate type={} walletId={} cycleStart={} dedupeKey={}",
                        candidate.type(), walletId, cycleStart, candidate.dedupeKey());
                return;
            }
            throw e;
        }
    }

    private String serialise(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("payload is not serialisable: " + e.getOriginalMessage());
        }
    }

    private InsightResponse toResponse(Insight insight) {
        return new InsightResponse(
                insight.getId(),
                insight.getType(),
                insight.getSeverity(),
                insight.getCycleStart(),
                insight.getTitle(),
                insight.getDescription(),
                readPayload(insight.getPayload()),
                insight.getActionType(),
                insight.getActionRef(),
                insight.getCreatedAt());
    }

    private JsonNode readPayload(String payload) {
        if (payload == null) {
            return null;
        }
        try {
            return payloadReader.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("stored insight payload is not valid JSON", e);
        }
    }

    /** SQLState 23505 anywhere in the cause chain (PostgreSQL and H2 both report unique violations with it). */
    public static boolean isUniqueViolation(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String state = t instanceof JDBCException jdbc ? jdbc.getSQLState()
                    : t instanceof SQLException sql ? sql.getSQLState()
                    : null;
            if (Objects.equals(UNIQUE_VIOLATION, state)) {
                return true;
            }
        }
        return false;
    }

    private static EntityNotFoundException notFound(Long id) {
        return new EntityNotFoundException("Insight not found with id: " + id);
    }
}
