package com.tradepulseai.custservice.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import payment.PaymentRequest;
import payment.PaymentResponse;
import payment.PaymentServiceGrpc;

@Service
public class PaymentServiceGrpcClient {
    private static final Logger log = LoggerFactory.getLogger(PaymentServiceGrpcClient.class);
    private final PaymentServiceGrpc.PaymentServiceBlockingStub blockingStub;

    public PaymentServiceGrpcClient(
            @Value("${payment.service.address:localhost}") String serverAddress,
            @Value("${payment.service.grpc.port:9001}") int serverPort
    ) {
        PaymentServiceGrpcClient.log.info("Connecting to Payment Service GRPC at {}:{}", serverAddress, serverPort);

        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress, serverPort)
                .usePlaintext()
                .build();

        blockingStub = PaymentServiceGrpc.newBlockingStub(channel);
    }

    public void createPaymentAccount(String customerId, String firstName, String email) {

        PaymentRequest request = PaymentRequest.newBuilder()
                .setCustomerId(customerId)
                .setFirstName(firstName)
                .setEmail(email)
                .build();

        PaymentResponse response = blockingStub.createPaymentAccount(request);
        log.info("Received response from Payment Service GRPC: {}", response);

    }
}
