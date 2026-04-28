package com.tradepulseai.paymentservice.mapper;

import com.tradepulseai.paymentservice.dto.payment.PaymentResponseDTO;
import com.tradepulseai.paymentservice.model.Payment;

public class PaymentMapperDTO {

    public static PaymentResponseDTO toDTO(Payment payment) {
        return new PaymentResponseDTO(
                payment.getId(),
                payment.getOrderId(),
                payment.getTotalAmount(),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }
}

