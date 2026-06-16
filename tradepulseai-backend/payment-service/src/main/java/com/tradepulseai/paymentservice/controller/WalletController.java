package com.tradepulseai.paymentservice.controller;

import com.tradepulseai.paymentservice.dto.wallet.WalletAmountRequest;
import com.tradepulseai.paymentservice.dto.wallet.WalletResponseDTO;
import com.tradepulseai.paymentservice.dto.wallet.WalletTransactionDTO;
import com.tradepulseai.paymentservice.model.Wallet;
import com.tradepulseai.paymentservice.model.WalletTransaction;
import com.tradepulseai.paymentservice.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/wallet")
@Tag(name = "Wallet", description = "API for wallet management")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get wallet for current user")
    public ResponseEntity<WalletResponseDTO> getWallet(
            @RequestHeader("X-User-Id") Long userId) {
        Wallet wallet = walletService.getOrCreateWallet(userId);
        return ResponseEntity.ok(toDTO(wallet));
    }

    @PostMapping("/deposit")
    @Operation(summary = "Deposit money into wallet")
    public ResponseEntity<WalletResponseDTO> deposit(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody WalletAmountRequest request) {
        Wallet wallet = walletService.deposit(userId, request.getAmount());
        return ResponseEntity.ok(toDTO(wallet));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw money from wallet")
    public ResponseEntity<?> withdraw(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody WalletAmountRequest request) {
        try {
            Wallet wallet = walletService.withdraw(userId, request.getAmount());
            return ResponseEntity.ok(toDTO(wallet));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get wallet transaction history")
    public ResponseEntity<List<WalletTransactionDTO>> getTransactions(
            @RequestHeader("X-User-Id") Long userId) {
        List<WalletTransaction> transactions = walletService.getTransactions(userId);
        List<WalletTransactionDTO> dtos = transactions.stream()
                .map(this::toTransactionDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    private WalletResponseDTO toDTO(Wallet wallet) {
        return new WalletResponseDTO(
                wallet.getWalletId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }

    private WalletTransactionDTO toTransactionDTO(WalletTransaction tx) {
        return new WalletTransactionDTO(
                tx.getTransactionId(),
                tx.getWalletId(),
                tx.getTransactionType(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getCreatedAt()
        );
    }

    public record ErrorResponse(String message) {}
}

