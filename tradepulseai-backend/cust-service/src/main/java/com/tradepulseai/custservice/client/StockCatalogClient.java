package com.tradepulseai.custservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

@Service
public class StockCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(StockCatalogClient.class);

    private final RestClient restClient;

    public StockCatalogClient(@Value("${stock.service.base-url:http://stock-service:4003}") String stockServiceBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(stockServiceBaseUrl)
                .build();
    }

    public StockQuote getStockQuote(Long stockId) {
        try {
            StockResponse response = restClient.get()
                    .uri("/stocks/{id}", stockId)
                    .retrieve()
                    .body(StockResponse.class);

            if (response == null || response.symbol == null) {
                throw new IllegalArgumentException("Stock not found for stockId: " + stockId);
            }

            return new StockQuote(
                    stockId,
                    response.symbol,
                    BigDecimal.valueOf(response.price).setScale(2, RoundingMode.HALF_UP)
            );
        } catch (Exception exception) {
            log.warn("Falling back to default quote for stockId={}: {}", stockId, exception.getMessage());
            return new StockQuote(
                    stockId,
                    String.valueOf(stockId),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            );
        }
    }

    public MarketSession getMarketSession() {
        try {
            MarketStatusResponse response = restClient.get()
                    .uri("/stocks/market-status")
                    .retrieve()
                    .body(MarketStatusResponse.class);

            if (response == null || response.session == null || response.session.isBlank()) {
                return new MarketSession("closed", true);
            }

            return new MarketSession(response.session.trim().toLowerCase(Locale.ROOT), Boolean.TRUE.equals(response.stale));
        } catch (Exception exception) {
            log.warn("Unable to fetch market session from stock-service: {}", exception.getMessage());
            // Safe fallback: treat as closed when market status cannot be resolved.
            return new MarketSession("closed", true);
        }
    }

    public record StockQuote(Long stockId, String symbol, BigDecimal unitPrice) {
    }

    public record MarketSession(String session, boolean stale) {
        public boolean canSell() {
            return "regular".equals(session) || "pre-market".equals(session) || "after-hours".equals(session);
        }
    }

    @SuppressWarnings("unused")
    private static class StockResponse {
        public String id;
        public String symbol;
        public double price;
        public Map<String, Object> metadata;
    }

    @SuppressWarnings("unused")
    private static class MarketStatusResponse {
        public String session;
        public Boolean stale;
    }
}

