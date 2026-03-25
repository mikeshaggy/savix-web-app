package com.mikeshaggy.backend.ledger.service;

import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.domain.WalletEntry;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryChartPointResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistorySummaryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryTimelineEntryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryTimelineGroupResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryTimelinePaginationResponse;
import com.mikeshaggy.backend.ledger.repo.WalletEntryRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repo.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletBalanceHistoryQueryService {

    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(10, 20, 50, 100);
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final WalletRepository walletRepository;
    private final WalletEntryRepository walletEntryRepository;

    public WalletBalanceHistoryResponse getBalanceHistoryByWalletIdForUser(
            Integer walletId,
            UUID userId,
            LocalDate from,
            LocalDate to,
            Integer page,
            Integer size
    ) {
        Wallet wallet = walletRepository.findByIdAndUserId(walletId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found with id: " + walletId));

        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                "from (%s) must not be after to (%s)".formatted(from, to)
            );
        }

        if (page != null && page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }

        int effectivePage = page == null ? 0 : page;
        int effectiveSize = normalizePageSize(size);

        List<WalletEntry> entries = walletEntryRepository.findByWalletIdAndUserIdForHistory(
                walletId,
                userId,
                from,
                to,
                Pageable.unpaged()
        );

        WalletBalanceHistorySummaryResponse summary = buildSummary(entries);
        List<WalletBalanceHistoryChartPointResponse> chart = buildChart(entries);
        List<WalletBalanceHistoryTimelineGroupResponse> timeline = buildTimeline(entries);
        TimelinePaginationResult paginatedTimeline = paginateTimelineGroups(timeline, effectivePage, effectiveSize);

        return new WalletBalanceHistoryResponse(
                wallet.getId(),
                wallet.getName(),
                wallet.getBalance(),
                summary,
                chart,
                paginatedTimeline.items(),
                paginatedTimeline.pagination()
        );
    }

    public WalletBalanceHistoryResponse getBalanceHistoryByWalletIdForUser(Integer walletId, UUID userId) {
        return getBalanceHistoryByWalletIdForUser(walletId, userId, null, null, 0, DEFAULT_PAGE_SIZE);
    }

    private int normalizePageSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_PAGE_SIZE;
        }

        return ALLOWED_PAGE_SIZES.contains(requestedSize) ? requestedSize : DEFAULT_PAGE_SIZE;
    }

    private TimelinePaginationResult paginateTimelineGroups(
            List<WalletBalanceHistoryTimelineGroupResponse> groups,
            int requestedPage,
            int pageSize
    ) {
        int totalEntryRows = groups.stream()
                .mapToInt(g -> g.entries().size())
                .sum();

        if (totalEntryRows == 0) {
            WalletBalanceHistoryTimelinePaginationResponse emptyPagination =
                    new WalletBalanceHistoryTimelinePaginationResponse(0, pageSize, 0, 0, false, false);
            return new TimelinePaginationResult(List.of(), emptyPagination);
        }

        List<List<WalletBalanceHistoryTimelineGroupResponse>> pages = new ArrayList<>();
        List<WalletBalanceHistoryTimelineGroupResponse> bucket = new ArrayList<>();
        int bucketRows = 0;

        for (WalletBalanceHistoryTimelineGroupResponse group : groups) {
            int groupRows = group.entries().size();
            if (!bucket.isEmpty() && bucketRows + groupRows > pageSize) {
                pages.add(bucket);
                bucket = new ArrayList<>();
                bucketRows = 0;
            }
            bucket.add(group);
            bucketRows += groupRows;
        }
        if (!bucket.isEmpty()) {
            pages.add(bucket);
        }

        int totalPages = pages.size();
        int activePage = Math.min(requestedPage, totalPages - 1);

        WalletBalanceHistoryTimelinePaginationResponse pagination =
                new WalletBalanceHistoryTimelinePaginationResponse(
                        activePage,
                        pageSize,
                        totalEntryRows,
                        totalPages,
                        activePage + 1 < totalPages,
                        activePage > 0
                );

        return new TimelinePaginationResult(pages.get(activePage), pagination);
    }

    private WalletBalanceHistorySummaryResponse buildSummary(List<WalletEntry> entries) {
        if (entries.isEmpty()) {
            return new WalletBalanceHistorySummaryResponse(
                    null,
                    null,
                    null,
                    0,
                    BigDecimal.ZERO
            );
        }

        BigDecimal latestBalance = entries.stream()
            .filter(entry -> entry.getBalanceAfter() != null)
            .max(Comparator
                .comparing(WalletEntry::getEntryDate)
                .thenComparing(WalletEntry::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WalletEntry::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(WalletEntry::getBalanceAfter)
            .orElse(null);
        BigDecimal highestBalance = null;
        BigDecimal lowestBalance = null;
        BigDecimal netChange = BigDecimal.ZERO;

        for (WalletEntry entry : entries) {
            if (entry.getAmountSigned() != null) {
                netChange = netChange.add(entry.getAmountSigned());
            }

            BigDecimal balanceAfter = entry.getBalanceAfter();
            if (balanceAfter == null) {
                continue;
            }
            if (highestBalance == null || balanceAfter.compareTo(highestBalance) > 0) {
                highestBalance = balanceAfter;
            }
            if (lowestBalance == null || balanceAfter.compareTo(lowestBalance) < 0) {
                lowestBalance = balanceAfter;
            }
        }

        if (highestBalance == null) {
            lowestBalance = null;
        }

        return new WalletBalanceHistorySummaryResponse(
                latestBalance,
                highestBalance,
                lowestBalance,
                entries.size(),
                netChange
        );
    }

    private List<WalletBalanceHistoryChartPointResponse> buildChart(List<WalletEntry> entries) {
        List<WalletEntry> sortedEntries = entries.stream()
            .sorted(Comparator
                .comparing(WalletEntry::getEntryDate)
                .thenComparing(WalletEntry::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WalletEntry::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        Map<LocalDate, List<WalletEntry>> groupedByDay = new LinkedHashMap<>();

        for (WalletEntry entry : sortedEntries) {
            groupedByDay.computeIfAbsent(entry.getEntryDate(), ignored -> new ArrayList<>())
                .add(entry);
        }

        return groupedByDay.entrySet().stream()
            .map(group -> {
                List<WalletEntry> dayEntries = group.getValue();
                WalletEntry lastEntry = dayEntries.get(dayEntries.size() - 1);

                BigDecimal dayNetChange = dayEntries.stream()
                    .map(WalletEntry::getAmountSigned)
                    .filter(amount -> amount != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                return new WalletBalanceHistoryChartPointResponse(
                    group.getKey(),
                    lastEntry.getBalanceAfter(),
                    dayNetChange
                );
            })
            .toList();
    }

    private List<WalletBalanceHistoryTimelineGroupResponse> buildTimeline(List<WalletEntry> entries) {
        Map<LocalDate, List<WalletBalanceHistoryTimelineEntryResponse>> grouped = new LinkedHashMap<>();

        for (WalletEntry entry : entries) {
            grouped.computeIfAbsent(entry.getEntryDate(), ignored -> new ArrayList<>())
                    .add(toTimelineEntry(entry));
        }

        return grouped.entrySet().stream()
                .map(group -> new WalletBalanceHistoryTimelineGroupResponse(group.getKey(), group.getValue()))
                .toList();
    }

    private WalletBalanceHistoryTimelineEntryResponse toTimelineEntry(WalletEntry entry) {
        return new WalletBalanceHistoryTimelineEntryResponse(
                entry.getId(),
                entry.getEntryDate(),
                entry.getAmountSigned(),
                entry.getBalanceAfter(),
                entry.getSourceType(),
                entry.getSourceId(),
                resolveSourceLabel(entry.getSourceType()),
                resolveSourceReference(entry.getSourceId())
        );
    }

    private String resolveSourceLabel(SourceType sourceType) {
        return switch (sourceType) {
            case TRANSACTION -> "Transaction";
            case TRANSFER -> "Transfer";
            case ADJUSTMENT -> "Adjustment";
        };
    }

    private String resolveSourceReference(Long sourceId) {
        if (sourceId == null) {
            return null;
        }
        return "#" + sourceId;
    }

    private record TimelinePaginationResult(
            List<WalletBalanceHistoryTimelineGroupResponse> items,
            WalletBalanceHistoryTimelinePaginationResponse pagination
    ) {
    }
}