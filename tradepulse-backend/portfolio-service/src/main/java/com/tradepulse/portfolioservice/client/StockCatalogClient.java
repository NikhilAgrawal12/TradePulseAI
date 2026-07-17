package com.tradepulse.portfolioservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Locale;

@Service
public class StockCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(StockCatalogClient.class);

    private final RestClient restClient;

    public StockCatalogClient(@Value("${stock.service.base-url:http://stock-service:4003}") String stockServiceBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(stockServiceBaseUrl).build();
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
            return new MarketSession("closed", true);
        }
    }


    public record MarketSession(String session, boolean stale) {
        public boolean canSell() {
            return "regular".equals(session) || "pre-market".equals(session) || "after-hours".equals(session);
        }
    }


    @SuppressWarnings("unused")
    private static class MarketStatusResponse {
        public String session;
        public Boolean stale;
    }
}

