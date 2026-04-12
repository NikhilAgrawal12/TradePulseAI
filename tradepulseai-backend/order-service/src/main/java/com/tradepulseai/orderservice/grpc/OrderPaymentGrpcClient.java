package com.tradepulseai.orderservice.grpc;

import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.service.StockQuote;
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

    public OrderPaymentResponse completePayment(Long orderId, CartItem cartItem, String userEmail, StockQuote stockQuote) {
        OrderPaymentRequest request = OrderPaymentRequest.newBuilder()
                .setCartItemId(String.valueOf(orderId))
                .setUserEmail(userEmail)
                .setStockId(String.valueOf(cartItem.getStockId()))
                .setSymbol(stockQuote.symbol())
                .setPrice(stockQuote.unitPrice().doubleValue())
                .setQuantity(toGrpcQuantity(cartItem))
                .build();

        OrderPaymentResponse response = blockingStub.completePayment(request);
        log.info("OrderPayment gRPC response: {}", response);
        return response;
    }

    public OrderPaymentResponse completePayment(Long orderId, CartItem cartItem, String userEmail) {
        return completePayment(
                orderId,
                cartItem,
                userEmail,
                new StockQuote(cartItem.getStockId(), String.valueOf(cartItem.getStockId()), java.math.BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP))
        );
    }

    private int toGrpcQuantity(CartItem cartItem) {
        try {
            return cartItem.getQuantity().intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Payment supports whole-number quantity only for stockId: " + cartItem.getStockId(), exception);
        }
    }
}
