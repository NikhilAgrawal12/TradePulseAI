package com.tradepulseai.orderservice.service;

import com.tradepulseai.orderservice.dto.portfolio.PortfolioOrderSyncRequestDTO;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import portfolio_sync.PortfolioOrderItem;
import portfolio_sync.PortfolioSyncServiceGrpc;
import portfolio_sync.RecordCompletedOrderRequest;

@Service
public class PortfolioSyncClient {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSyncClient.class);

    private final PortfolioSyncServiceGrpc.PortfolioSyncServiceBlockingStub blockingStub;

    public PortfolioSyncClient(
            @Value("${portfolio.service.grpc.address:cust-service}") String serverAddress,
            @Value("${portfolio.service.grpc.port:9004}") int serverPort
    ) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress, serverPort)
                .usePlaintext()
                .build();
        this.blockingStub = PortfolioSyncServiceGrpc.newBlockingStub(channel);
    }

    public void syncCompletedOrder(Long userId, PortfolioOrderSyncRequestDTO request) {
        try {
            RecordCompletedOrderRequest grpcRequest = RecordCompletedOrderRequest.newBuilder()
                    .setUserId(String.valueOf(userId))
                    .addAllItems(
                            request.getItems().stream()
                                    .map(item -> PortfolioOrderItem.newBuilder()
                                            .setStockId(item.getStockId())
                                            .setSymbol(item.getSymbol())
                                            .setPrice(item.getPrice().doubleValue())
                                            .setQuantity(item.getQuantity())
                                            .build())
                                    .toList()
                    )
                    .build();

            blockingStub.recordCompletedOrder(grpcRequest);
        } catch (Exception exception) {
            log.error("Failed to sync completed order to portfolio for userId {}", userId, exception);
            throw new IllegalStateException("Payment completed, but portfolio update failed. Please retry.");
        }
    }
}

