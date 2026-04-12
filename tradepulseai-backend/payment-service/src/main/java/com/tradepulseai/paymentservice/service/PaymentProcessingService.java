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

    public PaymentProcessingService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment processPayment(String rawOrderId, String userEmail, double totalAmount) {
        Long orderId = parseOrderId(rawOrderId);
        log.info("Processing payment for orderId={}, userEmail={}, totalAmount={}",
                orderId, userEmail, totalAmount);

        if (paymentRepository.existsByOrderId(orderId)) {
            log.warn("Payment already exists for orderId={}, returning existing record", orderId);
            return paymentRepository.findByOrderId(orderId).get(0);
        }

        Payment payment = PaymentMapper.toModel(orderId, BigDecimal.valueOf(totalAmount));
        Payment savedPayment = paymentRepository.save(payment);

        log.info("Payment saved: id={}, status={}, totalAmount={}",
                savedPayment.getId(), savedPayment.getStatus(), savedPayment.getTotalAmount());

        return savedPayment;
    }

    public String generateAccountId(String userEmail) {
        return "acct-" + userEmail + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Long parseOrderId(String rawOrderId) {
        try {
            return Long.parseLong(rawOrderId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid order id format: " + rawOrderId);
        }
    }
}


