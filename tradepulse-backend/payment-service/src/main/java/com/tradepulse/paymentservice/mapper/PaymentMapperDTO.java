package com.tradepulse.paymentservice.mapper;

import com.tradepulse.paymentservice.dto.payment.PaymentResponseDTO;
import com.tradepulse.paymentservice.model.Payment;

import java.math.RoundingMode;

public class PaymentMapperDTO {

    public static PaymentResponseDTO toDTO(Payment payment) {
        return new PaymentResponseDTO(
                payment.getId(),
                payment.getOrderId(),
                payment.getTotalAmount() == null ? null : payment.getTotalAmount().setScale(2, RoundingMode.HALF_UP),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }
}

