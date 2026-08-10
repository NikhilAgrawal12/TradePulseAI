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
import java.util.concurrent.TimeUnit;

@Service
public class OrderPaymentGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentGrpcClient.class);

    private final OrderPaymentServiceGrpc.OrderPaymentServiceBlockingStub blockingStub;
    private final long paymentDeadlineMs;
    private final long refundDeadlineMs;

    public OrderPaymentGrpcClient(
            @Value("${order.payment.service.address:payment-service}") String serverAddress,
            @Value("${order.payment.service.grpc.port:9002}") int serverPort,
            @Value("${order.payment.service.grpc.deadline-ms:8000}") long paymentDeadlineMs,
            @Value("${order.payment.service.grpc.refund-deadline-ms:8000}") long refundDeadlineMs
    ) {
        log.info("Connecting to OrderPayment gRPC at {}:{}", serverAddress, serverPort);

        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress, serverPort)
                .usePlaintext()
                .build();

        this.blockingStub = OrderPaymentServiceGrpc.newBlockingStub(channel);
        this.paymentDeadlineMs = paymentDeadlineMs;
        this.refundDeadlineMs = refundDeadlineMs;
    }

    /**
     * @param orderId    the persisted order id
     * @param totalAmount the complete order total
     * @param userId  the buyer's user id
     */
    public OrderPaymentResponse completeOrderPayment(String orderId, BigDecimal totalAmount, Long userId) {
        OrderPaymentRequest request = OrderPaymentRequest.newBuilder()
                .setOrderId(orderId)
                .setUserId(String.valueOf(userId))
                .setTotalAmount(totalAmount.doubleValue())
                .build();

        log.info("Sending completeOrderPayment gRPC for orderId={}, totalAmount={}, deadlineMs={}", orderId, totalAmount, paymentDeadlineMs);
        OrderPaymentResponse response = blockingStub
                .withDeadlineAfter(paymentDeadlineMs, TimeUnit.MILLISECONDS)
                .completePayment(request);
        log.info("OrderPayment gRPC response: {}", response);
        return response;
    }

    public OrderPaymentResponse refundOrderPayment(String orderId, BigDecimal totalAmount, Long userId) {
        OrderPaymentRequest request = OrderPaymentRequest.newBuilder()
                .setOrderId("refund-" + orderId)
                .setUserId(String.valueOf(userId))
                .setTotalAmount(totalAmount.negate().doubleValue())
                .build();

        log.warn("Sending refundOrderPayment gRPC for orderId={}, userId={}, amount={}, deadlineMs={}",
                orderId, userId, totalAmount, refundDeadlineMs);
        OrderPaymentResponse response = blockingStub
                .withDeadlineAfter(refundDeadlineMs, TimeUnit.MILLISECONDS)
                .completePayment(request);
        log.warn("Refund gRPC response for orderId={}: status={}", orderId, response.getStatus());
        return response;
    }
}

