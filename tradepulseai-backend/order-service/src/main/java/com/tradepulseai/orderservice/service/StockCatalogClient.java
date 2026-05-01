package com.tradepulseai.orderservice.service;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import stock_quote.StockQuoteRequest;
import stock_quote.StockQuoteResponse;
import stock_quote.StockQuoteServiceGrpc;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class StockCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(StockCatalogClient.class);

    private final StockQuoteServiceGrpc.StockQuoteServiceBlockingStub blockingStub;

    public StockCatalogClient(
            @Value("${stock.service.grpc.address:stock-service}") String serverAddress,
            @Value("${stock.service.grpc.port:9003}") int serverPort
    ) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress, serverPort)
                .usePlaintext()
                .build();
        this.blockingStub = StockQuoteServiceGrpc.newBlockingStub(channel);
    }

    public StockQuote getStockQuote(Long stockId) {
        try {
            StockQuoteResponse response = blockingStub.getStockQuote(
                    StockQuoteRequest.newBuilder()
                            .setStockId(String.valueOf(stockId))
                            .build()
            );

            if (response == null || response.getSymbol().isBlank()) {
                throw new IllegalArgumentException("Stock not found for stockId: " + stockId);
            }

            return new StockQuote(
                    stockId,
                    response.getSymbol(),
                    BigDecimal.valueOf(response.getPrice()).setScale(4, RoundingMode.HALF_UP)
            );
        } catch (Exception exception) {
            log.warn("Falling back to default quote for stockId={}: {}", stockId, exception.getMessage());
            return new StockQuote(
                    stockId,
                    String.valueOf(stockId),
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
            );
        }
    }

}

