package com.mikeshaggy.backend.fixedpayment.api;

import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.fixedpayment.dto.CreateFixedPaymentRequest;
import com.mikeshaggy.backend.fixedpayment.dto.FixedPaymentResponse;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.UpdateFixedPaymentRequest;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentCrudService;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(FixedPaymentController.BASE_URL)
@RequiredArgsConstructor
public class FixedPaymentController {

    public static final String BASE_URL = "/api/fixed-payments";

    private final FixedPaymentCrudService fixedPaymentCrudService;
    private final FixedPaymentDashboardService fixedPaymentDashboardService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<FixedPaymentResponse> createFixedPayment(
            @Valid @RequestBody CreateFixedPaymentRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();
        FixedPaymentResponse dto = fixedPaymentCrudService.createFixedPayment(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FixedPaymentResponse> updateFixedPayment(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateFixedPaymentRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();
        FixedPaymentResponse dto = fixedPaymentCrudService.updateFixedPayment(id, request, userId);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateFixedPayment(@PathVariable Integer id) {
        UUID userId = currentUserProvider.getCurrentUserId();
        fixedPaymentCrudService.deactivateFixedPayment(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tile")
    public ResponseEntity<FixedTransactionsTileDto> getFixedPaymentsTileData(
            @RequestParam Integer walletId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        FixedTransactionsTileDto tileData = fixedPaymentDashboardService
                .getFixedPaymentsTileDataForCurrentPeriod(walletId, userId);
        return ResponseEntity.ok(tileData);
    }

    @GetMapping
    public ResponseEntity<List<FixedPaymentResponse>> getAllFixedPayments(
            @RequestParam Integer walletId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        List<FixedPaymentResponse> payments = fixedPaymentCrudService.getAllFixedPayments(walletId, userId);
        return ResponseEntity.ok(payments);
    }
}
