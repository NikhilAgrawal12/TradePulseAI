package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.market.PolygonPreviousCloseResponse;
import com.tradepulseai.stockservice.dto.market.PolygonTickerReferenceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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

    public List<PolygonTickerReferenceResponse.PolygonTickerReference> fetchActiveUsTickers(int targetCount, int pageSize) {
        if (!isConfigured()) {
            return List.of();
        }

        List<PolygonTickerReferenceResponse.PolygonTickerReference> collected = new ArrayList<>();
        String nextUrl = null;

        try {
            while (collected.size() < targetCount) {
                PolygonTickerReferenceResponse response = requestTickerPage(nextUrl, pageSize);
                if (response == null || response.results() == null || response.results().isEmpty()) {
                    break;
                }

                for (PolygonTickerReferenceResponse.PolygonTickerReference ticker : response.results()) {
                    if (ticker == null || ticker.ticker() == null || ticker.ticker().isBlank()) {
                        continue;
                    }
                    if (!"stocks".equalsIgnoreCase(ticker.market())) {
                        continue;
                    }
                    if (!"us".equalsIgnoreCase(ticker.locale())) {
                        continue;
                    }
                    if (!ticker.active()) {
                        continue;
                    }
                    collected.add(ticker);
                    if (collected.size() >= targetCount) {
                        break;
                    }
                }

                nextUrl = response.nextUrl();
                if (nextUrl == null || nextUrl.isBlank()) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Unable to fetch ticker universe from Polygon.", e);
        }

        return collected;
    }

    private PolygonTickerReferenceResponse requestTickerPage(String nextUrl, int pageSize) {
        if (nextUrl == null || nextUrl.isBlank()) {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/reference/tickers")
                            .queryParam("market", "stocks")
                            .queryParam("active", true)
                            .queryParam("locale", "us")
                            .queryParam("limit", pageSize)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(PolygonTickerReferenceResponse.class);
        }

        String fullUrl = nextUrl.contains("apiKey=")
                ? nextUrl
                : nextUrl + (nextUrl.contains("?") ? "&" : "?") + "apiKey=" + apiKey;

        return restClient.get()
                .uri(URI.create(fullUrl))
                .retrieve()
                .body(PolygonTickerReferenceResponse.class);
    }
}
