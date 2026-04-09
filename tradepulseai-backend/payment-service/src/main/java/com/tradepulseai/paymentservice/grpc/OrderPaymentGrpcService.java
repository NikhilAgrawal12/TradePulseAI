package com.tradepulseai.paymentservice.grpc;

import com.tradepulseai.paymentservice.service.PaymentProcessingService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import order_payment.OrderPaymentRequest;
import order_payment.OrderPaymentResponse;
import order_payment.OrderPaymentServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class OrderPaymentGrpcService extends OrderPaymentServiceGrpc.OrderPaymentServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentGrpcService.class);

    private final PaymentProcessingService paymentProcessingService;

    public OrderPaymentGrpcService(PaymentProcessingService paymentProcessingService) {
        this.paymentProcessingService = paymentProcessingService;
    }

    @Override
    public void completePayment(OrderPaymentRequest request, StreamObserver<OrderPaymentResponse> responseObserver) {
        log.info("CompletePayment request received for cartItemId={}, userEmail={}, stockId={}, qty={}",
                request.getCartItemId(), request.getUserEmail(), request.getStockId(), request.getQuantity());

        try {
            paymentProcessingService.processPayment(
                    request.getCartItemId(),
                    request.getUserEmail(),
                    request.getStockId(),
                    request.getSymbol(),
                    request.getPrice(),
                    request.getQuantity()
            );

            String accountId = paymentProcessingService.generateAccountId(request.getUserEmail());
            OrderPaymentResponse response = OrderPaymentResponse.newBuilder()
                    .setAccountId(accountId)
                    .setStatus("COMPLETED")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("CompletePayment response sent: accountId={}, status=COMPLETED", accountId);
        } catch (Exception e) {
            log.error("Error processing payment for cartItemId={}: {}", request.getCartItemId(), e.getMessage(), e);
            responseObserver.onError(new RuntimeException("Payment processing failed: " + e.getMessage(), e));
        }
    }
}
