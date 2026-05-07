package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.market.PolygonExchangeResponse;
import com.tradepulseai.stockservice.dto.market.PolygonGroupedDailyMarketSummaryResponse;
import com.tradepulseai.stockservice.dto.market.PolygonTickerReferenceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.LocalDate;
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

    public Optional<PolygonGroupedDailyMarketSummaryResponse> fetchGroupedDailyMarketSummary(LocalDate marketDate) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        try {
            PolygonGroupedDailyMarketSummaryResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/aggs/grouped/locale/us/market/stocks/{date}")
                            .queryParam("adjusted", true)
                            .queryParam("apiKey", apiKey)
                            .build(marketDate))
                    .retrieve()
                    .body(PolygonGroupedDailyMarketSummaryResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(response);
        } catch (RestClientResponseException e) {
            log.warn("Polygon grouped daily summary request failed for date {} with status {} and body {}",
                    marketDate, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Unexpected Polygon grouped daily summary fetch error for date {}", marketDate, e);
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

    public List<PolygonExchangeResponse.PolygonExchange> fetchUsStockExchanges() {
        if (!isConfigured()) {
            return List.of();
        }

        try {
            PolygonExchangeResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/reference/exchanges")
                            .queryParam("asset_class", "stocks")
                            .queryParam("locale", "us")
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(PolygonExchangeResponse.class);

            if (response == null || response.results() == null) {
                return List.of();
            }

            return response.results();
        } catch (RestClientResponseException e) {
            log.warn("Polygon exchange request failed with status {} and body {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Unexpected Polygon exchange fetch error", e);
        }

        return List.of();
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
