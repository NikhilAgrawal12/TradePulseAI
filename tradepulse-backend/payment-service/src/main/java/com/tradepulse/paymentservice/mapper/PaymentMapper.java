package com.tradepulse.paymentservice.mapper;

import com.tradepulse.paymentservice.model.Payment;

import java.math.BigDecimal;

public class PaymentMapper {

    public static Payment toModel(String orderId, BigDecimal totalAmount) {
        return toModel(orderId, totalAmount, "COMPLETED");
    }

    public static Payment toModel(String orderId, BigDecimal totalAmount, String status) {
        BigDecimal scaled = totalAmount.setScale(2, java.math.RoundingMode.HALF_UP);

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setTotalAmount(scaled);
        payment.setStatus(status);

        return payment;
    }
}

