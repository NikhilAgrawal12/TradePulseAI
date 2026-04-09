package com.tradepulseai.paymentservice.mapper;

import com.tradepulseai.paymentservice.model.Payment;

import java.math.BigDecimal;

public class PaymentMapper {

    public static Payment toModel(String cartItemId, String userEmail, String stockId, String symbol,
                                   double price, int quantity) {
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

        return payment;
    }
}

