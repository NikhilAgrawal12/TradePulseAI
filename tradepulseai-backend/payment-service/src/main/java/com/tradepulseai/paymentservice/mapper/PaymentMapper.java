package com.tradepulseai.paymentservice.mapper;

import com.tradepulseai.paymentservice.model.Payment;

import java.math.BigDecimal;

public class PaymentMapper {

    public static Payment toModel(Long orderId, BigDecimal totalAmount) {
        BigDecimal scaled = totalAmount.setScale(4, java.math.RoundingMode.HALF_UP);

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setTotalAmount(scaled);
        payment.setStatus("COMPLETED");

        return payment;
    }
}

