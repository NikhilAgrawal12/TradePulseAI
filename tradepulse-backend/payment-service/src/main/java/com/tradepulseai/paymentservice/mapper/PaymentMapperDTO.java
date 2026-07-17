package com.tradepulseai.paymentservice.mapper;

import com.tradepulseai.paymentservice.dto.payment.PaymentResponseDTO;
import com.tradepulseai.paymentservice.model.Payment;

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

