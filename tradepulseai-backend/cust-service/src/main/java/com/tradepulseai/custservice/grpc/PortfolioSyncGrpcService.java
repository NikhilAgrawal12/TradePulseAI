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
            Long userId = Long.parseLong(request.getUserId());
            RecordPortfolioOrderRequestDTO dto = new RecordPortfolioOrderRequestDTO();
            dto.setItems(toItems(request.getItemsList()));
            portfolioService.recordCompletedOrder(userId, dto);

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

    private List<PortfolioFillItemRequestDTO> toItems(List<PortfolioOrderItem> items) {
        return items.stream()
                .map(item -> {
                    PortfolioFillItemRequestDTO dto = new PortfolioFillItemRequestDTO();
                    dto.setStockId(item.getStockId());
                    dto.setSymbol(item.getSymbol());
                    dto.setPrice(BigDecimal.valueOf(item.getPrice()));
                    dto.setQuantity(item.getQuantity());
                    return dto;
                })
                .toList();
    }
}

