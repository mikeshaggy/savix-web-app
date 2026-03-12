package com.mikeshaggy.backend.wallet.api;

import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.wallet.dto.WalletBalanceUpdateRequest;
import com.mikeshaggy.backend.wallet.dto.WalletCreateRequest;
import com.mikeshaggy.backend.wallet.dto.WalletResponse;
import com.mikeshaggy.backend.wallet.dto.WalletUpdateRequest;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(WalletController.BASE_URL)
@RequiredArgsConstructor
public class WalletController {

    public static final String BASE_URL = "/api/wallets";

    private final WalletService walletService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<WalletResponse>> getWallets() {
        List<WalletResponse> wallets = walletService.getWalletsForUser(
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(wallets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getWalletById(@PathVariable Integer id) {
        WalletResponse wallet = walletService.getWalletByIdForUser(
                id, 
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(wallet);
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody WalletCreateRequest request) {
        WalletResponse createdWallet = walletService.createWallet(
                request, 
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(createdWallet);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WalletResponse> updateWallet(
            @PathVariable Integer id, 
            @Valid @RequestBody WalletUpdateRequest request) {
        WalletResponse updatedWallet = walletService.updateWallet(
                id, 
                request, 
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(updatedWallet);
    }

    @PatchMapping("/{id}/balance")
    public ResponseEntity<WalletResponse> updateWalletBalance(
            @PathVariable Integer id, 
            @Valid @RequestBody WalletBalanceUpdateRequest request) {
        WalletResponse updatedWallet = walletService.updateWalletBalance(
                id, 
                request.newBalance(), 
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(updatedWallet);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWallet(@PathVariable Integer id) {
        walletService.deleteWallet(id, currentUserProvider.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
