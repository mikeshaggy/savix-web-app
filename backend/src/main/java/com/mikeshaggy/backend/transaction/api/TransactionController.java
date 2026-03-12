package com.mikeshaggy.backend.transaction.api;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.dto.*;
import com.mikeshaggy.backend.transaction.service.TransactionOrchestrator;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(TransactionController.BASE_URL)
@RequiredArgsConstructor
public class TransactionController {

    public static final String BASE_URL = "/api/transactions";

    private final TransactionService transactionService;
    private final TransactionOrchestrator transactionOrchestrator;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<TransactionPageResponse> getTransactions(
            @RequestParam(required = false) Integer walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "transactionDate,desc") String sort,
            @RequestParam(required = false) List<CategoryType> types,
            @RequestParam(required = false) List<Integer> categoryIds,
            @RequestParam(required = false) List<Importance> importances,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String q
    ) {
        TransactionFilterParams filter = new TransactionFilterParams(
                currentUserProvider.getCurrentUserId(),
                walletId, page, size, types, categoryIds,
                importances, startDate, endDate, q, sort
        );
        return ResponseEntity.ok(transactionService.getTransactionsForUser(filter));
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
        TransactionResponse createdTransaction = transactionOrchestrator.createTransaction(
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
