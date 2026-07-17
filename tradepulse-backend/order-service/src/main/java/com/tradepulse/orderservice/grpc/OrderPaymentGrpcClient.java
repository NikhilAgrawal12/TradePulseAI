package com.tradepulse.orderservice.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import order_payment.OrderPaymentRequest;
import order_payment.OrderPaymentResponse;
import order_payment.OrderPaymentServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderPaymentGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentGrpcClient.class);

    private final OrderPaymentServiceGrpc.OrderPaymentServiceBlockingStub blockingStub;

    public OrderPaymentGrpcClient(
            @Value("${order.payment.service.address:payment-service}") String serverAddress,
            @Value("${order.payment.service.grpc.port:9002}") int serverPort
    ) {
        log.info("Connecting to OrderPayment gRPC at {}:{}", serverAddress, serverPort);

        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress, serverPort)
                .usePlaintext()
                .build();

        this.blockingStub = OrderPaymentServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Sends a single payment request for an entire order.
     *
     * @param orderId    the persisted order id
     * @param totalAmount the complete order total (subtotal + tax)
     * @param userId  the buyer's user id
     */
    public OrderPaymentResponse completeOrderPayment(String orderId, BigDecimal totalAmount, Long userId) {
        OrderPaymentRequest request = OrderPaymentRequest.newBuilder()
                .setOrderId(orderId)
                .setUserId(String.valueOf(userId))
                .setTotalAmount(totalAmount.doubleValue())
                .build();

        log.info("Sending completeOrderPayment gRPC for orderId={}, totalAmount={}", orderId, totalAmount);
        OrderPaymentResponse response = blockingStub.completePayment(request);
        log.info("OrderPayment gRPC response: {}", response);
        return response;
    }

    public OrderPaymentResponse refundOrderPayment(String orderId, BigDecimal totalAmount, Long userId) {
        OrderPaymentRequest request = OrderPaymentRequest.newBuilder()
                .setOrderId("refund-" + orderId)
                .setUserId(String.valueOf(userId))
                .setTotalAmount(totalAmount.negate().doubleValue())
                .build();

        log.warn("Sending refundOrderPayment gRPC for orderId={}, userId={}, amount={}",
                orderId, userId, totalAmount);
        OrderPaymentResponse response = blockingStub.completePayment(request);
        log.warn("Refund gRPC response for orderId={}: status={}", orderId, response.getStatus());
        return response;
    }
}

