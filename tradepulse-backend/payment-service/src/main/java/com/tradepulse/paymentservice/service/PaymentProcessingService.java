package com.tradepulse.paymentservice.service;

import com.tradepulse.paymentservice.mapper.PaymentMapper;
import com.tradepulse.paymentservice.model.Payment;
import com.tradepulse.paymentservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);
    private static final String PAYMENT_STATUS_COMPLETED = "COMPLETED";
    private static final String PAYMENT_STATUS_REFUNDED = "REFUNDED";
    private static final String PAYMENT_STATUS_SELL_SETTLED = "SELL_SETTLED";

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
            return paymentRepository.findByOrderId(orderId).getFirst();
        }

        BigDecimal amount = BigDecimal.valueOf(totalAmount).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Payment amount cannot be zero.");
        }

        Long userIdLong = Long.parseLong(userId);
        Payment payment;
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            walletService.deductForPurchase(userIdLong, amount);
            payment = PaymentMapper.toModel(orderId, amount, PAYMENT_STATUS_COMPLETED);
        } else {
            BigDecimal refundAmount = amount.abs();
            walletService.refundPurchase(userIdLong, refundAmount);
            payment = PaymentMapper.toModel(orderId, refundAmount, PAYMENT_STATUS_REFUNDED);
        }

        Payment savedPayment = paymentRepository.save(payment);

        log.info("Payment saved: id={}, status={}, totalAmount={}",
                savedPayment.getId(), savedPayment.getStatus(), savedPayment.getTotalAmount());

        return savedPayment;
    }

    @Transactional
    public Payment processSellSettlement(String rawSettlementRef, String userId, String stockId, int quantity, double totalAmount) {
        String settlementRef = validateOrderId(rawSettlementRef);
        if (stockId == null || stockId.isBlank()) {
            throw new IllegalArgumentException("Stock id is required for sell settlement.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Sell settlement quantity must be greater than zero.");
        }

        log.info("Processing sell settlement for settlementRef={}, userId={}, totalAmount={}",
                settlementRef, userId, totalAmount);
        log.info("Sell settlement metadata for settlementRef={}: stockId={}, quantity={}",
                settlementRef, stockId, quantity);

        if (paymentRepository.existsByOrderId(settlementRef)) {
            log.warn("Sell settlement already exists for settlementRef={}, returning existing record", settlementRef);
            return paymentRepository.findByOrderId(settlementRef).getFirst();
        }

        BigDecimal amount = BigDecimal.valueOf(totalAmount).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Sell settlement amount must be greater than zero.");
        }

        Long userIdLong = Long.parseLong(userId);
        walletService.creditForSell(userIdLong, amount);

        Payment payment = PaymentMapper.toModel(settlementRef, amount, PAYMENT_STATUS_SELL_SETTLED);
        Payment savedPayment = paymentRepository.save(payment);

        log.info("Sell settlement saved: id={}, status={}, totalAmount={}",
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


