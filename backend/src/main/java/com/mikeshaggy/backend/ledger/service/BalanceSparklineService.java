package com.mikeshaggy.backend.ledger.service;

import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.CycleMinimum;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.Point;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryChartPointResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Stage 6.1: the dashboard balance sparkline. Daily closing balances are the {@code closingBalance} points of
 * {@link WalletBalanceHistoryQueryService} (the same chart {@code /wallets} draws), cut off at today so no
 * future-dated entry leaks in; paydays and per-cycle minima come from {@link PayCycleService} and only for the
 * salary wallet.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalanceSparklineService {

    public static final int DEFAULT_DAYS = 90;
    public static final int MAX_DAYS = 366;
    static final int MINIMA_CYCLES = 3;

    private final WalletBalanceHistoryQueryService balanceHistoryQueryService;
    private final PayCycleService payCycleService;
    private final Clock clock;

    public BalanceSparklineResponse getSparkline(Integer walletId, UUID userId, int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException("days must be between 1 and " + MAX_DAYS);
        }
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusDays(days - 1L);

        // the whole history up to today: the balance carried into the window and full-cycle minima need it
        WalletBalanceHistoryResponse history = balanceHistoryQueryService.getBalanceHistoryByWalletIdForUser(
                walletId, userId, null, to, 0, null);
        NavigableMap<LocalDate, BigDecimal> closing = closingBalances(history.chart());

        List<Point> points = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            BigDecimal balance = closingOn(closing, date);
            if (balance != null) {
                points.add(new Point(date, balance));
            }
        }

        Optional<PayCycle> current = payCycleService.current(userId);
        if (current.isEmpty() || !Objects.equals(current.get().salaryWalletId(), walletId)) {
            return new BalanceSparklineResponse(walletId, from, to, points, List.of(), List.of());
        }

        // every cycle is at least one day long, so `days` closed cycles always reach back past `from`
        List<PayCycle> closed = payCycleService.history(userId, Math.max(days, MINIMA_CYCLES));

        List<LocalDate> paydays = new ArrayList<>();
        for (int i = closed.size() - 1; i >= 0; i--) {
            addIfWithin(paydays, closed.get(i).start(), from, to);
        }
        addIfWithin(paydays, current.get().start(), from, to);

        List<CycleMinimum> minima = new ArrayList<>();
        for (int i = Math.min(MINIMA_CYCLES, closed.size()) - 1; i >= 0; i--) {
            minimum(closing, closed.get(i)).ifPresent(minima::add);
        }

        return new BalanceSparklineResponse(walletId, from, to, points, paydays, minima);
    }

    private static NavigableMap<LocalDate, BigDecimal> closingBalances(List<WalletBalanceHistoryChartPointResponse> chart) {
        NavigableMap<LocalDate, BigDecimal> closing = new TreeMap<>();
        for (WalletBalanceHistoryChartPointResponse point : chart) {
            if (point.closingBalance() != null) {
                closing.put(point.date(), point.closingBalance());
            }
        }
        return closing;
    }

    /** Closing balance at the end of {@code date}: the last closing on or before it, or null before any entry. */
    private static BigDecimal closingOn(NavigableMap<LocalDate, BigDecimal> closing, LocalDate date) {
        Map.Entry<LocalDate, BigDecimal> entry = closing.floorEntry(date);
        return entry == null ? null : entry.getValue();
    }

    /** Lowest daily closing balance over the whole cycle; the earliest day wins a tie. Empty before any entry. */
    private static Optional<CycleMinimum> minimum(NavigableMap<LocalDate, BigDecimal> closing, PayCycle cycle) {
        BigDecimal min = null;
        LocalDate minDate = null;
        for (LocalDate date = cycle.start(); !date.isAfter(cycle.end()); date = date.plusDays(1)) {
            BigDecimal balance = closingOn(closing, date);
            if (balance != null && (min == null || balance.compareTo(min) < 0)) {
                min = balance;
                minDate = date;
            }
        }
        return min == null
                ? Optional.empty()
                : Optional.of(new CycleMinimum(cycle.start(), cycle.end(), min, minDate));
    }

    private static void addIfWithin(List<LocalDate> dates, LocalDate date, LocalDate from, LocalDate to) {
        if (!date.isBefore(from) && !date.isAfter(to)) {
            dates.add(date);
        }
    }
}
