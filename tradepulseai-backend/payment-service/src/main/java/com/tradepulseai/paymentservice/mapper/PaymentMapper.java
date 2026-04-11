package com.tradepulseai.paymentservice.mapper;

import com.tradepulseai.paymentservice.model.Payment;

import java.math.BigDecimal;

public class PaymentMapper {

    public static Payment toModel(Long orderId, double price, int quantity) {
        BigDecimal priceDecimal = BigDecimal.valueOf(price);
        BigDecimal totalAmount = priceDecimal.multiply(BigDecimal.valueOf(quantity))
                .setScale(4, java.math.RoundingMode.HALF_UP);

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setTotalAmount(totalAmount);
        payment.setStatus("COMPLETED");

        return payment;
    }
}

