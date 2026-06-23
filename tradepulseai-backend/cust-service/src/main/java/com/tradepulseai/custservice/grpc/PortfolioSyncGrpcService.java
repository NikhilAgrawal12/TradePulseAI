package com.tradepulseai.custservice.grpc;

import com.tradepulseai.custservice.dto.portfolio.PortfolioFillItemRequestDTO;
import com.tradepulseai.custservice.dto.portfolio.RecordPortfolioOrderRequestDTO;
import com.tradepulseai.custservice.service.PortfolioService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import portfolio_sync.PortfolioOrderItem;
import portfolio_sync.PortfolioSyncServiceGrpc;
import portfolio_sync.RecordCompletedOrderRequest;
import portfolio_sync.RecordCompletedOrderResponse;

import java.math.BigDecimal;
import java.util.List;

@GrpcService
public class PortfolioSyncGrpcService extends PortfolioSyncServiceGrpc.PortfolioSyncServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSyncGrpcService.class);

    private final PortfolioService portfolioService;

    public PortfolioSyncGrpcService(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @Override
    public void recordCompletedOrder(
            RecordCompletedOrderRequest request,
            StreamObserver<RecordCompletedOrderResponse> responseObserver
    ) {
        try {
            validateRequest(request);
            log.info("Received portfolio sync request for userId={}, items={}", request.getUserId(), request.getItemsCount());

            Long userId = Long.parseLong(request.getUserId());
            if (userId <= 0) {
                throw new IllegalArgumentException("userId must be greater than 0");
            }

            RecordPortfolioOrderRequestDTO dto = new RecordPortfolioOrderRequestDTO();
            dto.setItems(toItems(request.getItemsList()));
            portfolioService.recordCompletedOrder(userId, dto);

            log.info("Portfolio sync completed for userId={}, items={}", userId, request.getItemsCount());

            responseObserver.onNext(
                    RecordCompletedOrderResponse.newBuilder()
                            .setSuccess(true)
                            .setMessage("Portfolio updated")
                            .build()
            );
            responseObserver.onCompleted();
        } catch (NumberFormatException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Invalid userId format: " + request.getUserId())
                            .asRuntimeException()
            );
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        } catch (Exception exception) {
            log.error("Failed to sync portfolio for userId={}", request.getUserId(), exception);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Unable to record completed order")
                            .asRuntimeException()
            );
        }
    }

    private void validateRequest(RecordCompletedOrderRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

        if (request.getItemsCount() == 0) {
            throw new IllegalArgumentException("At least one portfolio item is required");
        }

        for (int index = 0; index < request.getItemsCount(); index++) {
            PortfolioOrderItem item = request.getItems(index);
            if (item.getStockId() == null || item.getStockId().isBlank()) {
                throw new IllegalArgumentException("stockId is required for item at index " + index);
            }

            if (item.getPrice() <= 0) {
                throw new IllegalArgumentException("price must be greater than 0 for stockId: " + item.getStockId());
            }

            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("quantity must be greater than 0 for stockId: " + item.getStockId());
            }
        }
    }

    private List<PortfolioFillItemRequestDTO> toItems(List<PortfolioOrderItem> items) {
        return items.stream()
                .map(item -> {
                    PortfolioFillItemRequestDTO dto = new PortfolioFillItemRequestDTO();
                    dto.setStockId(item.getStockId());
                    dto.setPrice(BigDecimal.valueOf(item.getPrice()));
                    dto.setQuantity(item.getQuantity());
                    return dto;
                })
                .toList();
    }
}

