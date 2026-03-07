package com.mikeshaggy.backend.fixedpayment.api;

import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.fixedpayment.dto.CreateFixedPaymentRequest;
import com.mikeshaggy.backend.fixedpayment.dto.FixedPaymentDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.UpdateFixedPaymentRequest;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(FixedPaymentController.BASE_URL)
@RequiredArgsConstructor
public class FixedPaymentController {

    public static final String BASE_URL = "/api/fixed-payments";

    private final FixedPaymentService fixedPaymentService;
    private final PeriodService periodService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<FixedPaymentDto> createFixedPayment(
            @Valid @RequestBody CreateFixedPaymentRequest request) {
        FixedPaymentDto dto = fixedPaymentService.createFixedPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FixedPaymentDto> updateFixedPayment(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateFixedPaymentRequest request) {
        FixedPaymentDto dto = fixedPaymentService.updateFixedPayment(id, request);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateFixedPayment(@PathVariable Integer id) {
        fixedPaymentService.deactivateFixedPayment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tile")
    public ResponseEntity<FixedTransactionsTileDto> getFixedPaymentsTileData(
            @RequestParam Integer walletId) {
        PeriodDto period = periodService.getCurrentPeriod(
                currentUserProvider.getCurrentUserId());
        FixedTransactionsTileDto tileData = fixedPaymentService.getFixedPaymentsTileData(period, walletId);
        return ResponseEntity.ok(tileData);
    }

    @GetMapping
    public ResponseEntity<List<FixedPaymentDto>> getAllFixedPayments(
            @RequestParam Integer walletId) {
        List<FixedPaymentDto> payments = fixedPaymentService.getAllFixedPayments(walletId);
        return ResponseEntity.ok(payments);
    }
}
