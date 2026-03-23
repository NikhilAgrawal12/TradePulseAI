package com.tradepulseai.paymentservice.grpc;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import payment.PaymentResponse;
import payment.PaymentServiceGrpc.PaymentServiceImplBase;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class PaymentGrpcService extends PaymentServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(PaymentGrpcService.class);

    @Override
    public void createPaymentAccount(payment.PaymentRequest paymentRequest, StreamObserver<PaymentResponse> responseObserver) {
        log.info("createPaymentAccount request received {}", paymentRequest.toString());

        // Business logic eg save to database, perform calculations etc
        PaymentResponse response = PaymentResponse.newBuilder()
                .setAccountId("123456")
                .setStatus("ACTIVE")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
