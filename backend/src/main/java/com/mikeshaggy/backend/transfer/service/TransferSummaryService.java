package com.mikeshaggy.backend.transfer.service;

import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.transfer.domain.Transfer;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CounterWallet;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CounterWalletFlow;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CycleSummary;
import com.mikeshaggy.backend.transfer.repository.TransferRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.zero;

/**
 * Stage 6.2: transfers in/out of one wallet per pay cycle and per counter-wallet. Reads the same wallet-scoped
 * transfer query as {@code GET /api/transfers/wallet/{id}} (fund movements excluded); a transfer row is one
 * movement, so each is counted exactly once, from this wallet's side. Cycle windows come from
 * {@link PayCycleService} and exist for the salary wallet only; the open cycle is counted through today, also
 * while {@code AWAITING_SALARY}, and never beyond it.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferSummaryService {

    public static final int DEFAULT_CYCLES = 3;
    public static final int MAX_CYCLES = 24;

    private final TransferRepository transferRepository;
    private final WalletRepository walletRepository;
    private final PayCycleService payCycleService;
    private final Clock clock;

    public TransferSummaryResponse getSummary(Integer walletId, UUID userId, int cycles) {
        if (cycles < 1 || cycles > MAX_CYCLES) {
            throw new IllegalArgumentException("cycles must be between 1 and " + MAX_CYCLES);
        }
        walletRepository.findByIdAndUserId(walletId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found with id: " + walletId));

        List<Transfer> transfers = transferRepository.findByWalletIdAndUserId(walletId, userId);
        List<CounterWallet> counterWallets = counterWallets(walletId, transfers);

        Optional<PayCycle> current = payCycleService.current(userId);
        if (current.isEmpty() || !Objects.equals(current.get().salaryWalletId(), walletId)) {
            return new TransferSummaryResponse(walletId, null, List.of(), counterWallets);
        }

        LocalDate today = LocalDate.now(clock);
        PayCycle open = current.get();
        CycleSummary currentCycle = summarize(walletId, transfers, open, open.start(), today);
        List<CycleSummary> closed = payCycleService.history(userId, cycles).stream()
                .map(cycle -> summarize(walletId, transfers, cycle, cycle.start(), cycle.end()))
                .toList();

        return new TransferSummaryResponse(walletId, currentCycle, closed, counterWallets);
    }

    /** Transfers dated in {@code [from, through]}, grouped by counter-wallet (ascending id). */
    private static CycleSummary summarize(Integer walletId, List<Transfer> transfers, PayCycle cycle,
                                          LocalDate from, LocalDate through) {
        SortedMap<Integer, Flow> byCounterWallet = new TreeMap<>();
        for (Transfer transfer : transfers) {
            LocalDate date = transfer.getTransferDate();
            if (date.isBefore(from) || date.isAfter(through)) {
                continue;
            }
            boolean incoming = walletId.equals(transfer.getToWallet().getId());
            boolean outgoing = walletId.equals(transfer.getFromWallet().getId());
            if (incoming == outgoing) {
                continue; // a self-transfer (rejected on write) moves nothing
            }
            Wallet counter = incoming ? transfer.getFromWallet() : transfer.getToWallet();
            Flow flow = byCounterWallet.computeIfAbsent(counter.getId(), id -> new Flow(counter.getName()));
            if (incoming) {
                flow.in = flow.in.add(money(transfer.getAmount()));
            } else {
                flow.out = flow.out.add(money(transfer.getAmount()));
            }
        }

        BigDecimal totalIn = zero();
        BigDecimal totalOut = zero();
        List<CounterWalletFlow> flows = new ArrayList<>();
        for (Map.Entry<Integer, Flow> entry : byCounterWallet.entrySet()) {
            Flow flow = entry.getValue();
            totalIn = totalIn.add(flow.in);
            totalOut = totalOut.add(flow.out);
            flows.add(new CounterWalletFlow(entry.getKey(), flow.name, flow.in, flow.out, flow.in.subtract(flow.out)));
        }
        return new CycleSummary(cycle.start(), cycle.end(), cycle.state(), totalIn, totalOut,
                totalIn.subtract(totalOut), flows);
    }

    private static List<CounterWallet> counterWallets(Integer walletId, List<Transfer> transfers) {
        SortedMap<Integer, CounterWallet> counters = new TreeMap<>();
        for (Transfer transfer : transfers) {
            for (Wallet wallet : List.of(transfer.getFromWallet(), transfer.getToWallet())) {
                if (!walletId.equals(wallet.getId())) {
                    counters.putIfAbsent(wallet.getId(),
                            new CounterWallet(wallet.getId(), wallet.getName(), money(wallet.getBalance())));
                }
            }
        }
        return List.copyOf(counters.values());
    }

    private static final class Flow {
        private final String name;
        private BigDecimal in = zero();
        private BigDecimal out = zero();

        private Flow(String name) {
            this.name = name;
        }
    }
}
