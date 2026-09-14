package com.mikeshaggy.backend.common.paycycle;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.repository.CategoryRepository;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.domain.UserPaydayRule;
import com.mikeshaggy.backend.user.repository.UserPaydayRuleRepository;
import com.mikeshaggy.backend.user.repository.UserRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The single source of pay-cycle boundaries (decisions T2–T8): "what is the current cycle, the last cycle and
 * the last N cycles of this user?"
 * <p>
 * Cycles are user-level and resolved from the designated salary wallet only. Closed cycles are facts —
 * salary anchor → the day before the next salary anchor. The open cycle ends at {@code expectedNextAnchor − 1},
 * predicted by {@link ExpectedPaydayResolver}; its state is derived on read: {@link CycleState#OPEN} while today
 * precedes the expected anchor, {@link CycleState#AWAITING_SALARY} from that day on until a newer anchor
 * arrives. The end date is never clamped to today in either direction.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PayCycleService {

    private final UserRepository userRepository;
    private final UserPaydayRuleRepository paydayRuleRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final ExpectedPaydayResolver expectedPaydayResolver;
    private final PayCycleProperties properties;
    private final Clock clock;

    /** The cycle that started at the most recent anchor, or empty without a salary wallet, anchor category or anchor. */
    public Optional<PayCycle> current(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        return salaryContext(userId).flatMap(context -> {
            List<LocalDate> anchors = mergedAnchorDates(context, userId, today);
            if (anchors.isEmpty()) {
                return Optional.empty();
            }
            LocalDate lastAnchor = anchors.getFirst();
            ExpectedPayday expected = expectedPaydayResolver.resolve(userId, lastAnchor, anchors, configuredRule(userId));
            LocalDate expectedNextAnchor = expected.date();
            PayCycle cycle = today.isBefore(expectedNextAnchor)
                    ? PayCycle.open(userId, context.salaryWalletId(), lastAnchor, expectedNextAnchor)
                    : PayCycle.awaitingSalary(userId, context.salaryWalletId(), lastAnchor, expectedNextAnchor);
            return Optional.of(cycle);
        });
    }

    /** The most recent closed cycle (the one before {@link #current}). */
    public Optional<PayCycle> last(UUID userId) {
        return history(userId, 1).stream().findFirst();
    }

    /** Up to {@code n} closed cycles, newest first. */
    public List<PayCycle> history(UUID userId, int n) {
        return historyAsOf(userId, LocalDate.now(clock), n);
    }

    /** Up to {@code n} cycles closed on or before {@code asOf}, newest first — the cycle open on {@code asOf} is excluded. */
    public List<PayCycle> historyAsOf(UUID userId, LocalDate asOf, int n) {
        Objects.requireNonNull(asOf, "asOf");
        if (n <= 0) {
            return List.of();
        }
        return salaryContext(userId).map(context -> {
            List<LocalDate> anchors = mergedAnchorDates(context, userId, asOf);
            List<PayCycle> cycles = new ArrayList<>();
            for (int i = 1; i < anchors.size() && cycles.size() < n; i++) {
                cycles.add(PayCycle.closed(userId, context.salaryWalletId(), anchors.get(i),
                        anchors.get(i - 1).minusDays(1)));
            }
            return cycles;
        }).orElse(List.of());
    }

    /**
     * The user's salary wallet: {@code users.salary_wallet_id} when set, otherwise the regular wallet holding the
     * most recent anchor-category transaction (the {@code 04_pay_cycle.sql} backfill rule), otherwise empty.
     */
    public Optional<Integer> salaryWalletId(UUID userId) {
        Optional<Integer> configured = userRepository.findById(userId)
                .map(User::getSalaryWallet)
                .map(Wallet::getId);
        if (configured.isPresent()) {
            return configured;
        }
        return anchorCategoryId(userId).flatMap(categoryId -> transactionRepository
                .findLatestAnchorWalletIds(userId, categoryId, PageRequest.of(0, 1))
                .stream()
                .findFirst());
    }

    public boolean isSalaryWallet(UUID userId, Integer walletId) {
        return walletId != null && salaryWalletId(userId).filter(walletId::equals).isPresent();
    }

    /**
     * Up to {@code limit} merged anchor dates on or before {@code upTo}, newest first: anchor-category transactions
     * in the salary wallet only, distinct per date, and an anchor closer than {@code minCycleDays} to the previous
     * kept anchor joins that cycle instead of starting a new one (decision T8).
     */
    public List<LocalDate> anchorDates(UUID userId, LocalDate upTo, int limit) {
        Objects.requireNonNull(upTo, "upTo");
        if (limit <= 0) {
            return List.of();
        }
        return salaryContext(userId)
                .map(context -> {
                    List<LocalDate> anchors = mergedAnchorDates(context, userId, upTo);
                    return List.copyOf(anchors.subList(0, Math.min(limit, anchors.size())));
                })
                .orElse(List.of());
    }

    private record SalaryContext(Integer salaryWalletId, Integer anchorCategoryId) {
    }

    private Optional<SalaryContext> salaryContext(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return anchorCategoryId(userId).flatMap(categoryId ->
                salaryWalletId(userId).map(walletId -> new SalaryContext(walletId, categoryId)));
    }

    private Optional<Integer> anchorCategoryId(UUID userId) {
        return categoryRepository.findByUserIdAndIsCycleAnchorTrue(userId).map(Category::getId);
    }

    private PaydayRule configuredRule(UUID userId) {
        return paydayRuleRepository.findById(userId).map(UserPaydayRule::toPaydayRule).orElse(null);
    }

    /**
     * All anchor dates are loaded (salaries are monthly, so the set is small) so that the merge always sees the
     * anchor an early one might belong to; callers slice afterwards.
     */
    private List<LocalDate> mergedAnchorDates(SalaryContext context, UUID userId, LocalDate upTo) {
        List<LocalDate> rawDates = transactionRepository.findAnchorDates(
                context.salaryWalletId(), userId, context.anchorCategoryId(), upTo, Pageable.unpaged());
        return mergeAnchors(rawDates, properties.minCycleDays());
    }

    /**
     * Walks the anchors oldest → newest and keeps one only when it is at least {@code minCycleDays} after the last
     * kept anchor; the result is newest first. The scan must run forward: whether an anchor is "kept" depends on
     * the older anchors already decided (Jan 1 / Jan 15 / Jan 25 → Jan 1 and Jan 25).
     */
    static List<LocalDate> mergeAnchors(List<LocalDate> dates, int minCycleDays) {
        TreeSet<LocalDate> ascending = new TreeSet<>();
        for (LocalDate date : dates) {
            if (date != null) {
                ascending.add(date);
            }
        }
        List<LocalDate> kept = new ArrayList<>();
        LocalDate lastKept = null;
        for (LocalDate date : ascending) {
            if (lastKept == null || ChronoUnit.DAYS.between(lastKept, date) >= minCycleDays) {
                kept.add(date);
                lastKept = date;
            }
        }
        Collections.reverse(kept);
        return kept;
    }
}
