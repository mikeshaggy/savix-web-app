package com.mikeshaggy.backend.transfer.api;

import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.transfer.dto.TransferCreateRequest;
import com.mikeshaggy.backend.transfer.dto.TransferResponse;
import com.mikeshaggy.backend.transfer.dto.TransferUpdateRequest;
import com.mikeshaggy.backend.transfer.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(TransferController.BASE_URL)
@RequiredArgsConstructor
public class TransferController {

    public static final String BASE_URL = "/api/transfers";

    private final TransferService transferService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<TransferResponse>> getTransfers() {
        List<TransferResponse> transfers = transferService.getTransfersForUser(
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(transfers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransferById(@PathVariable Long id) {
        TransferResponse transfer = transferService.getTransferByIdForUser(
                id,
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(transfer);
    }

    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<List<TransferResponse>> getTransfersByWalletId(@PathVariable Integer walletId) {
        List<TransferResponse> transfers = transferService.getTransfersByWalletIdForUser(
                walletId,
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(transfers);
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(@Valid @RequestBody TransferCreateRequest request) {
        TransferResponse createdTransfer = transferService.createTransfer(
                request,
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTransfer);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransferResponse> updateTransfer(
            @PathVariable Long id,
            @Valid @RequestBody TransferUpdateRequest request) {
        TransferResponse updatedTransfer = transferService.updateTransfer(
                id,
                request,
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(updatedTransfer);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransfer(@PathVariable Long id) {
        transferService.deleteTransfer(id, currentUserProvider.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
