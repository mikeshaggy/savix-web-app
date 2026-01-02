package com.mikeshaggy.backend.controller;

import com.mikeshaggy.backend.dto.WalletDTO;
import com.mikeshaggy.backend.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping(WalletController.BASE_URL)
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class WalletController {

    public static final String BASE_URL = "/api/wallets";

    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<List<WalletDTO>> getAllWallets() {
        List<WalletDTO> wallets = walletService.getAllWallets();
        return new ResponseEntity<>(wallets, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletDTO> getWalletDById(@PathVariable Integer id) {
        WalletDTO wallet = walletService.getWalletDTOById(id);
        return new ResponseEntity<>(wallet, HttpStatus.OK);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<WalletDTO>> getWalletsByUserId(@PathVariable Integer userId) {
        List<WalletDTO> wallets = walletService.getWalletsByUserId(userId);
        return new ResponseEntity<>(wallets, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<WalletDTO> createWallet(@Valid @RequestBody WalletDTO walletDTO) {
        WalletDTO createdWallet = walletService.createWallet(walletDTO);
        return new ResponseEntity<>(createdWallet, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WalletDTO> updateWallet(@PathVariable Integer id, @Valid @RequestBody WalletDTO walletDTO) {
        WalletDTO updatedWallet = walletService.updateWallet(id, walletDTO);
        return new ResponseEntity<>(updatedWallet, HttpStatus.OK);
    }

    @PatchMapping("/{id}/balance")
    public ResponseEntity<WalletDTO> updateWalletBalance(@PathVariable Integer id, @RequestBody BigDecimal newBalance) {
        WalletDTO updatedWallet = walletService.updateWalletBalance(id, newBalance);
        return new ResponseEntity<>(updatedWallet, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWallet(@PathVariable Integer id) {
        walletService.deleteWallet(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
