package com.tradepulseai.paymentservice.service;

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
    public Payment processPayment(String cartItemId, String userEmail, String stockId, String symbol,
                                   double price, int quantity) {
        log.info("Processing payment for cartItemId={}, userEmail={}, stockId={}, qty={}, price={}",
                cartItemId, userEmail, stockId, quantity, price);

        BigDecimal priceDecimal = BigDecimal.valueOf(price);
        BigDecimal totalAmount = priceDecimal.multiply(BigDecimal.valueOf(quantity));

        Payment payment = new Payment();
        payment.setCartItemId(cartItemId);
        payment.setUserEmail(userEmail);
        payment.setStockId(stockId);
        payment.setSymbol(symbol);
        payment.setPrice(priceDecimal);
        payment.setQuantity(quantity);
        payment.setTotalAmount(totalAmount);
        payment.setStatus("COMPLETED");

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment saved successfully with id={}, status={}, totalAmount={}",
                savedPayment.getId(), savedPayment.getStatus(), savedPayment.getTotalAmount());

        return savedPayment;
    }

    public String generateAccountId(String userEmail) {
        return "acct-" + userEmail + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

