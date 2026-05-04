package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.market.PolygonPreviousCloseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

@Service
public class PolygonMarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(PolygonMarketDataClient.class);

    private final RestClient restClient;
    private final String apiKey;

    public PolygonMarketDataClient(
            @Value("${polygon.api.base-url}") String baseUrl,
            @Value("${polygon.api.key:}") String apiKey
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Optional<PolygonPreviousCloseResponse.PolygonAggregate> fetchPreviousClose(String symbol) {
        if (!isConfigured()) {
            log.warn("POLYGON_API_KEY is missing; skipping Polygon ingestion.");
            return Optional.empty();
        }

        try {
            PolygonPreviousCloseResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/aggs/ticker/{symbol}/prev")
                            .queryParam("adjusted", true)
                            .queryParam("apiKey", apiKey)
                            .build(symbol))
                    .retrieve()
                    .body(PolygonPreviousCloseResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return Optional.empty();
            }

            return Optional.ofNullable(response.results().getFirst());
        } catch (RestClientResponseException e) {
            log.warn("Polygon request failed for symbol {} with status {} and body {}",
                    symbol, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Unexpected Polygon fetch error for symbol {}", symbol, e);
        }

        return Optional.empty();
    }
}
