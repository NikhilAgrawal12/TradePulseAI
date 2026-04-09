package com.tradepulseai.orderservice.grpc;

import com.tradepulseai.orderservice.model.CartItem;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import order_payment.OrderPaymentRequest;
import order_payment.OrderPaymentResponse;
import order_payment.OrderPaymentServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderPaymentGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentGrpcClient.class);

    private final OrderPaymentServiceGrpc.OrderPaymentServiceBlockingStub blockingStub;

    public OrderPaymentGrpcClient(
            @Value("${order.payment.service.address:localhost}") String serverAddress,
            @Value("${order.payment.service.grpc.port:9002}") int serverPort
    ) {
        log.info("Connecting to OrderPayment gRPC at {}:{}", serverAddress, serverPort);

        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress, serverPort)
                .usePlaintext()
                .build();

        this.blockingStub = OrderPaymentServiceGrpc.newBlockingStub(channel);
    }

    public OrderPaymentResponse completePayment(CartItem cartItem) {
        OrderPaymentRequest request = OrderPaymentRequest.newBuilder()
                .setCartItemId(cartItem.getId().toString())
                .setUserEmail(cartItem.getUserEmail())
                .setStockId(cartItem.getStockId())
                .setSymbol(cartItem.getSymbol())
                .setPrice(cartItem.getPrice().doubleValue())
                .setQuantity(cartItem.getQuantity())
                .build();

        OrderPaymentResponse response = blockingStub.completePayment(request);
        log.info("OrderPayment gRPC response: {}", response);
        return response;
    }
}
