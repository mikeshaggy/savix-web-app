package com.mikeshaggy.backend.fund.api;

import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.fund.dto.*;
import com.mikeshaggy.backend.fund.service.FundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(FundController.BASE_URL)
@RequiredArgsConstructor
public class FundController {

    public static final String BASE_URL = "/api/funds";

    private final FundService fundService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<FundResponse>> getAll() {
        return ResponseEntity.ok(fundService.getFundsForUser(currentUserProvider.getCurrentUserId()));
    }

    @GetMapping("/summary")
    public ResponseEntity<FundsSummaryDto> getSummary() {
        return ResponseEntity.ok(fundService.getFundsSummary(currentUserProvider.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FundResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(fundService.getFundById(id, currentUserProvider.getCurrentUserId()));
    }

    @PostMapping
    public ResponseEntity<FundResponse> create(@Valid @RequestBody FundCreateRequest request) {
        FundResponse created = fundService.createFund(request, currentUserProvider.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FundResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody FundUpdateRequest request) {
        return ResponseEntity.ok(fundService.updateFund(id, request, currentUserProvider.getCurrentUserId()));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<FundResponse> archive(
            @PathVariable Long id,
            @RequestBody(required = false) FundArchiveRequest request) {
        return ResponseEntity.ok(fundService.archiveFund(id, request, currentUserProvider.getCurrentUserId()));
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<FundResponse> deposit(
            @PathVariable Long id,
            @Valid @RequestBody FundDepositRequest request) {
        return ResponseEntity.ok(fundService.depositToFund(id, request, currentUserProvider.getCurrentUserId()));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<FundResponse> withdraw(
            @PathVariable Long id,
            @Valid @RequestBody FundWithdrawRequest request) {
        return ResponseEntity.ok(fundService.withdrawFromFund(id, request, currentUserProvider.getCurrentUserId()));
    }
}
