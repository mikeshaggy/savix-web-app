package com.mikeshaggy.backend.ledger.api;

import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.ledger.dto.WalletEntryResponse;
import com.mikeshaggy.backend.ledger.service.WalletEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(WalletEntryController.BASE_URL)
@RequiredArgsConstructor
public class WalletEntryController {

    public static final String BASE_URL = "/api/wallet-entries";

    private final WalletEntryService walletEntryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<List<WalletEntryResponse>> getEntriesByWalletId(@PathVariable Integer walletId) {
        List<WalletEntryResponse> entries = walletEntryService.getEntriesByWalletIdForUser(
                walletId,
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(entries);
    }
}
