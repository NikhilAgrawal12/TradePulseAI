package com.tradepulseai.paymentservice.controller;

import com.tradepulseai.paymentservice.dto.payment.PaymentResponseDTO;
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

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get all payments for an order")
    public ResponseEntity<List<PaymentResponseDTO>> getPaymentsByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(
                paymentRepository.findByOrderId(orderId)
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
