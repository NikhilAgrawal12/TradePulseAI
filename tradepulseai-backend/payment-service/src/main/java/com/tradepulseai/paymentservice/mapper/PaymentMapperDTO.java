package com.tradepulseai.paymentservice.mapper;

import com.tradepulseai.paymentservice.dto.PaymentResponseDTO;
import com.tradepulseai.paymentservice.model.Payment;

public class PaymentMapperDTO {

    public static PaymentResponseDTO toDTO(Payment payment) {
        return new PaymentResponseDTO(
                payment.getId(),
                payment.getCartItemId(),
                payment.getUserEmail(),
                payment.getStockId(),
                payment.getSymbol(),
                payment.getPrice(),
                payment.getQuantity(),
                payment.getTotalAmount(),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }
}

