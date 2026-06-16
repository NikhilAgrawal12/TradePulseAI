package com.tradepulseai.paymentservice.service;

import com.tradepulseai.paymentservice.mapper.PaymentMapper;
import com.tradepulseai.paymentservice.model.Payment;
import com.tradepulseai.paymentservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);

    private final PaymentRepository paymentRepository;
    private final WalletService walletService;

    public PaymentProcessingService(PaymentRepository paymentRepository, WalletService walletService) {
        this.paymentRepository = paymentRepository;
        this.walletService = walletService;
    }

    @Transactional
    public Payment processPayment(String rawOrderId, String userId, double totalAmount) {
        String orderId = validateOrderId(rawOrderId);
        log.info("Processing payment for orderId={}, userId={}, totalAmount={}",
                orderId, userId, totalAmount);

        if (paymentRepository.existsByOrderId(orderId)) {
            log.warn("Payment already exists for orderId={}, returning existing record", orderId);
            return paymentRepository.findByOrderId(orderId).get(0);
        }

        // Deduct from wallet — throws IllegalStateException if insufficient balance
        Long userIdLong = Long.parseLong(userId);
        walletService.deductForPurchase(userIdLong, BigDecimal.valueOf(totalAmount));

        Payment payment = PaymentMapper.toModel(orderId, BigDecimal.valueOf(totalAmount));
        Payment savedPayment = paymentRepository.save(payment);

        log.info("Payment saved: id={}, status={}, totalAmount={}",
                savedPayment.getId(), savedPayment.getStatus(), savedPayment.getTotalAmount());

        return savedPayment;
    }

    public String generateAccountId(String userId) {
        return "acct-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String validateOrderId(String rawOrderId) {
        if (rawOrderId == null || rawOrderId.isBlank()) {
            throw new IllegalArgumentException("Invalid order id format: " + rawOrderId);
        }
        return rawOrderId;
    }
}


