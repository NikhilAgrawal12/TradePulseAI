package com.tradepulse.orderservice.grpc;

import com.tradepulse.orderservice.dto.portfolio.PortfolioOrderSyncRequestDTO;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import portfolio_sync.PortfolioOrderItem;
import portfolio_sync.PortfolioSyncServiceGrpc;
import portfolio_sync.RecordCompletedOrderRequest;
import portfolio_sync.RecordCompletedOrderResponse;

@Service
public class PortfolioSyncGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSyncGrpcClient.class);

    private final PortfolioSyncServiceGrpc.PortfolioSyncServiceBlockingStub blockingStub;

    public PortfolioSyncGrpcClient(
            @Value("${portfolio.sync.service.address:portfolio-service}") String serverAddress,
            @Value("${portfolio.sync.service.grpc.port:9005}") int serverPort
    ) {
        log.info("Connecting to PortfolioSync gRPC at {}:{}", serverAddress, serverPort);

        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress, serverPort)
                .usePlaintext()
                .build();

        this.blockingStub = PortfolioSyncServiceGrpc.newBlockingStub(channel);
    }

    public void syncCompletedOrder(Long userId, PortfolioOrderSyncRequestDTO request) {
        validateSyncRequest(userId, request);

        try {
            RecordCompletedOrderRequest payload = RecordCompletedOrderRequest.newBuilder()
                    .setUserId(String.valueOf(userId))
                    .addAllItems(
                            request.getItems().stream()
                                    .map(item -> PortfolioOrderItem.newBuilder()
                                            .setStockId(item.getStockId())
                                            .setPrice(item.getPrice().doubleValue())
                                            .setQuantity(item.getQuantity())
                                            .build())
                                    .toList()
                    )
                    .build();

            log.info("Sending portfolio sync request for userId={}, items={}", userId, payload.getItemsCount());

            RecordCompletedOrderResponse response = blockingStub.recordCompletedOrder(payload);
            if (!response.getSuccess()) {
                log.error("Portfolio sync returned success=false for userId={}, message={}", userId, response.getMessage());
                throw new IllegalStateException("Portfolio sync failed: " + response.getMessage());
            }

            log.info("Portfolio sync completed for userId={}, items={}", userId, payload.getItemsCount());
        } catch (StatusRuntimeException exception) {
            log.error("Portfolio gRPC sync failed for userId={}", userId, exception);
            throw new IllegalStateException("Payment completed, but portfolio update failed. Please retry.", exception);
        }
    }

    private void validateSyncRequest(Long userId, PortfolioOrderSyncRequestDTO request) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Valid userId is required for portfolio sync.");
        }

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one portfolio item is required for sync.");
        }

        for (int index = 0; index < request.getItems().size(); index++) {
            var item = request.getItems().get(index);
            if (item == null) {
                throw new IllegalArgumentException("Portfolio item at index " + index + " is missing.");
            }

            if (item.getStockId() == null || item.getStockId().isBlank()) {
                throw new IllegalArgumentException("stockId is required for portfolio item at index " + index + ".");
            }

            if (item.getPrice() == null || item.getPrice().signum() <= 0) {
                throw new IllegalArgumentException("price must be greater than 0 for stockId: " + item.getStockId());
            }

            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("quantity must be greater than 0 for stockId: " + item.getStockId());
            }
        }
    }
}

