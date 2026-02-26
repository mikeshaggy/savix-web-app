package com.mikeshaggy.backend.transaction.api;

import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.transaction.dto.TransactionCreateRequest;
import com.mikeshaggy.backend.transaction.dto.TransactionResponse;
import com.mikeshaggy.backend.transaction.dto.TransactionUpdateRequest;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(TransactionController.BASE_URL)
@RequiredArgsConstructor
public class TransactionController {

    public static final String BASE_URL = "/api/transactions";

    private final TransactionService transactionService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getTransactions() {
        List<TransactionResponse> transactions = transactionService.getTransactionsForUser(
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransactionById(@PathVariable Long id) {
        TransactionResponse transaction = transactionService.getTransactionByIdForUser(
                id, 
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(transaction);
    }

    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByWalletId(@PathVariable Integer walletId) {
        List<TransactionResponse> transactions = transactionService.getTransactionsByWalletIdForUser(
                walletId, 
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(transactions);
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody TransactionCreateRequest request) {
        TransactionResponse createdTransaction = transactionService.createTransaction(
                request, 
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTransaction);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable Long id, 
            @Valid @RequestBody TransactionUpdateRequest request) {
        TransactionResponse updatedTransaction = transactionService.updateTransaction(
                id, 
                request, 
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(updatedTransaction);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id, currentUserProvider.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
