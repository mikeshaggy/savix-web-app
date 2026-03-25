package com.mikeshaggy.backend.ledger.api;

import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletEntryResponse;
import com.mikeshaggy.backend.ledger.service.WalletEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(WalletEntryController.BASE_URL)
@RequiredArgsConstructor
public class WalletEntryController {

    public static final String BASE_URL = "/api/wallet-entries";

    private final WalletEntryService walletEntryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<List<WalletEntryResponse>> getEntriesByWalletId(
            @PathVariable Integer walletId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit
    ) {
        List<WalletEntryResponse> entries = walletEntryService.getEntriesByWalletIdForUser(
                walletId,
                currentUserProvider.getCurrentUserId(),
                from,
                to,
                limit
        );
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/wallet/{walletId}/balance-history")
    public ResponseEntity<WalletBalanceHistoryResponse> getBalanceHistoryByWalletId(
            @PathVariable Integer walletId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        WalletBalanceHistoryResponse response = walletEntryService.getBalanceHistoryByWalletIdForUser(
                walletId,
                currentUserProvider.getCurrentUserId(),
                from,
                to,
                page,
                size
        );
        return ResponseEntity.ok(response);
    }
}
