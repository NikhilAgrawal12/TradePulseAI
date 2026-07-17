package com.tradepulse.paymentservice.grpc;

import com.tradepulse.paymentservice.model.Payment;
import com.tradepulse.paymentservice.service.PaymentProcessingService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import order_payment.OrderPaymentRequest;
import order_payment.OrderPaymentResponse;
import order_payment.OrderPaymentServiceGrpc;
import order_payment.SellSettlementRequest;
import order_payment.SellSettlementResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class OrderPaymentGrpcService extends OrderPaymentServiceGrpc.OrderPaymentServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentGrpcService.class);
    private static final String PAYMENT_STATUS_COMPLETED = "COMPLETED";
    private static final String PAYMENT_STATUS_SELL_SETTLED = "SELL_SETTLED";

    private final PaymentProcessingService paymentProcessingService;

    public OrderPaymentGrpcService(PaymentProcessingService paymentProcessingService) {
        this.paymentProcessingService = paymentProcessingService;
    }

    @Override
    public void completePayment(OrderPaymentRequest request, StreamObserver<OrderPaymentResponse> responseObserver) {
        log.info("CompletePayment request received for orderId={}, userId={}, totalAmount={}",
                request.getOrderId(), request.getUserId(), request.getTotalAmount());

        try {
            Payment savedPayment = paymentProcessingService.processPayment(
                    request.getOrderId(),
                    request.getUserId(),
                    request.getTotalAmount()
            );

            String status = savedPayment.getStatus();
            if (!PAYMENT_STATUS_COMPLETED.equalsIgnoreCase(status)
                    && !"REFUNDED".equalsIgnoreCase(status)) {
                responseObserver.onError(
                        Status.FAILED_PRECONDITION
                                .withDescription("Unexpected payment status for orderId: " + request.getOrderId())
                                .asRuntimeException()
                );
                return;
            }

            String accountId = paymentProcessingService.generateAccountId(request.getUserId());
            OrderPaymentResponse response = OrderPaymentResponse.newBuilder()
                    .setAccountId(accountId)
                    .setStatus(status)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("CompletePayment response sent: accountId={}, status={}", accountId, status);
        } catch (IllegalStateException e) {
            log.warn("Wallet insufficient for orderId={}: {}", request.getOrderId(), e.getMessage());
            responseObserver.onError(
                    Status.FAILED_PRECONDITION
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } catch (Exception e) {
            log.error("Error processing payment for orderId={}: {}", request.getOrderId(), e.getMessage(), e);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Payment processing failed: " + e.getMessage())
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void settleSell(SellSettlementRequest request, StreamObserver<SellSettlementResponse> responseObserver) {
        log.info("SettleSell request received for settlementRef={}, userId={}, stockId={}, quantity={}, totalAmount={}",
                request.getSettlementRef(), request.getUserId(), request.getStockId(),
                request.getQuantity(), request.getTotalAmount());

        try {
            Payment savedPayment = paymentProcessingService.processSellSettlement(
                    request.getSettlementRef(),
                    request.getUserId(),
                    request.getStockId(),
                    request.getQuantity(),
                    request.getTotalAmount()
            );

            String status = savedPayment.getStatus();
            if (!PAYMENT_STATUS_SELL_SETTLED.equalsIgnoreCase(status)) {
                responseObserver.onError(
                        Status.FAILED_PRECONDITION
                                .withDescription("Sell settlement status is not SELL_SETTLED for ref: "
                                        + request.getSettlementRef())
                                .asRuntimeException()
                );
                return;
            }

            String accountId = paymentProcessingService.generateAccountId(request.getUserId());
            SellSettlementResponse response = SellSettlementResponse.newBuilder()
                    .setAccountId(accountId)
                    .setStatus(status)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("SettleSell response sent: accountId={}, status={}", accountId, status);
        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } catch (Exception e) {
            log.error("Error processing sell settlement for settlementRef={}: {}",
                    request.getSettlementRef(), e.getMessage(), e);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Sell settlement failed: " + e.getMessage())
                            .asRuntimeException()
            );
        }
    }

}
