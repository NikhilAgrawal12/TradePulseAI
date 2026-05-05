package com.tradepulseai.stockservice.grpc;

import com.tradepulseai.stockservice.dto.stock.StockResponseDTO;
import com.tradepulseai.stockservice.exception.StockNotFoundException;
import com.tradepulseai.stockservice.service.StockService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stock_quote.StockQuoteRequest;
import stock_quote.StockQuoteResponse;
import stock_quote.StockQuoteServiceGrpc;

@GrpcService
public class StockQuoteGrpcService extends StockQuoteServiceGrpc.StockQuoteServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(StockQuoteGrpcService.class);

    private final StockService stockService;

    public StockQuoteGrpcService(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public void getStockQuote(StockQuoteRequest request, StreamObserver<StockQuoteResponse> responseObserver) {
        try {
            Long stockId = Long.parseLong(request.getStockId());
            StockResponseDTO stock = stockService.getStockById(stockId);
            if (stock.getPrice() == null) {
                throw new StockNotFoundException("Stock quote not available yet for stockId=" + request.getStockId());
            }

            StockQuoteResponse response = StockQuoteResponse.newBuilder()
                    .setStockId(stock.getId())
                    .setSymbol(stock.getSymbol())
                    .setPrice(stock.getPrice())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NumberFormatException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Invalid stockId format: " + request.getStockId())
                            .asRuntimeException()
            );
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        } catch (StockNotFoundException exception) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        } catch (Exception exception) {
            log.error("Failed to serve stock quote for stockId={}", request.getStockId(), exception);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Unable to load stock quote")
                            .asRuntimeException()
            );
        }
    }
}

