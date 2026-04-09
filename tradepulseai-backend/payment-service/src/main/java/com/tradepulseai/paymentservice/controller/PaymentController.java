package com.tradepulseai.paymentservice.controller;

import com.tradepulseai.paymentservice.dto.PaymentResponseDTO;
import com.tradepulseai.paymentservice.mapper.PaymentMapperDTO;
import com.tradepulseai.paymentservice.repository.PaymentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "API for retrieving payment records")
public class PaymentController {

    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/user/{userEmail}")
    @Operation(summary = "Get all payments for a user")
    public ResponseEntity<List<PaymentResponseDTO>> getPaymentsByUserEmail(@PathVariable String userEmail) {
        return ResponseEntity.ok(
                paymentRepository.findByUserEmail(userEmail.toLowerCase())
                        .stream()
                        .map(PaymentMapperDTO::toDTO)
                        .toList()
        );
    }

    @GetMapping("/stock/{stockId}")
    @Operation(summary = "Get all payments for a stock")
    public ResponseEntity<List<PaymentResponseDTO>> getPaymentsByStockId(@PathVariable String stockId) {
        return ResponseEntity.ok(
                paymentRepository.findByStockId(stockId)
                        .stream()
                        .map(PaymentMapperDTO::toDTO)
                        .toList()
        );
    }

    @GetMapping
    @Operation(summary = "Get all payments")
    public ResponseEntity<List<PaymentResponseDTO>> getAllPayments() {
        return ResponseEntity.ok(
                paymentRepository.findAll()
                        .stream()
                        .map(PaymentMapperDTO::toDTO)
                        .toList()
        );
    }
}
