package com.tradepulse.portfolioservice.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import order_payment.OrderPaymentServiceGrpc;
import order_payment.SellSettlementRequest;
import order_payment.SellSettlementResponse;
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
            @Value("${payment.service.grpc.address:payment-service}") String serverAddress,
            @Value("${payment.service.grpc.port:9002}") int serverPort
    ) {
        log.info("Connecting to payment gRPC at {}:{}", serverAddress, serverPort);

        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress, serverPort)
                .usePlaintext()
                .build();

        this.blockingStub = OrderPaymentServiceGrpc.newBlockingStub(channel);
    }

    public SellSettlementResponse settleSell(String settlementRef, Long userId,
                                             Long stockId, int quantity,
                                             BigDecimal unitPrice, BigDecimal totalAmount) {
        SellSettlementRequest request = SellSettlementRequest.newBuilder()
                .setSettlementRef(settlementRef)
                .setUserId(String.valueOf(userId))
                .setStockId(String.valueOf(stockId))
                .setQuantity(quantity)
                .setUnitPrice(unitPrice.doubleValue())
                .setTotalAmount(totalAmount.doubleValue())
                .build();

        log.info("Sending SettleSell gRPC for settlementRef={}, userId={}, stockId={}, quantity={}, totalAmount={}",
                settlementRef, userId, stockId, quantity, totalAmount);
        SellSettlementResponse response = blockingStub.settleSell(request);
        log.info("SettleSell gRPC response for settlementRef={}: status={}", settlementRef, response.getStatus());
        return response;
    }
}

