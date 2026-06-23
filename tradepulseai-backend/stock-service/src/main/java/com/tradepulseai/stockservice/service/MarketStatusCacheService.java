package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.market.MarketStatusResponseDTO;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Order(6)
public class MarketStatusCacheService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketStatusCacheService.class);
    private static final long REFRESH_INTERVAL_SECONDS = 60;
    private static final long STALE_THRESHOLD_SECONDS = 180;

    private final RestClient restClient;
    private final String apiKey;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<CachedStatus> statusRef = new AtomicReference<>(fallbackStatus());

    public MarketStatusCacheService(
            @Value("${massive.api.base-url}") String apiBaseUrl,
            @Value("${massive.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "market-status-cache-refresh");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void run(ApplicationArguments args) {
        // Prime cache immediately on startup, then keep refreshing every 60 seconds.
        refreshCache();

        scheduler.scheduleAtFixedRate(
                this::refreshCache,
                REFRESH_INTERVAL_SECONDS,
                REFRESH_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        log.info("Market status cache started with {}s refresh interval.", REFRESH_INTERVAL_SECONDS);
    }

    public MarketStatusResponseDTO getCurrentStatus() {
        CachedStatus cached = statusRef.get();
        boolean stale = cached.lastUpdated() == null
                || cached.lastUpdated().plusSeconds(STALE_THRESHOLD_SECONDS).isBefore(Instant.now());

        MarketStatusResponseDTO dto = new MarketStatusResponseDTO();
        dto.setSession(cached.session());
        dto.setLabel(cached.label());
        dto.setCssClass(cached.cssClass());
        dto.setMarket(cached.market());
        dto.setNyse(cached.nyse());
        dto.setNasdaq(cached.nasdaq());
        dto.setServerTime(cached.serverTime() != null ? cached.serverTime().toString() : null);
        dto.setLastUpdated(cached.lastUpdated() != null ? cached.lastUpdated().toString() : null);
        dto.setStale(stale);
        return dto;
    }

    private void refreshCache() {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Skipping market status refresh because massive.api.key is missing.");
            return;
        }

        try {
            JsonNode response = restClient.get()
                    .uri(UriComponentsBuilder.fromPath("/v1/marketstatus/now")
                            .queryParam("apiKey", apiKey)
                            .build()
                            .toUriString())
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                log.warn("Received null response from /v1/marketstatus/now. Keeping previous cache value.");
                return;
            }

            CachedStatus mapped = mapResponse(response);
            statusRef.set(mapped);
        } catch (Exception ex) {
            log.warn("Failed to refresh market status cache: {}", ex.getMessage());
        }
    }

    private CachedStatus mapResponse(JsonNode response) {
        String market = textValue(response.get("market"));
        boolean earlyHours = response.path("earlyHours").asBoolean(false);
        boolean afterHours = response.path("afterHours").asBoolean(false);

        JsonNode exchanges = response.path("exchanges");
        String nyse = textValue(exchanges.get("nyse"));
        String nasdaq = textValue(exchanges.get("nasdaq"));

        SessionMeta sessionMeta = resolveSession(market, nyse, nasdaq, earlyHours, afterHours);
        Instant serverTime = parseInstant(textValue(response.get("serverTime")));

        return new CachedStatus(
                sessionMeta.session(),
                sessionMeta.label(),
                sessionMeta.cssClass(),
                market,
                nyse,
                nasdaq,
                serverTime,
                Instant.now()
        );
    }

    private SessionMeta resolveSession(String market, String nyse, String nasdaq, boolean earlyHours, boolean afterHours) {
        String normalizedMarket = normalize(market);
        String normalizedNyse = normalize(nyse);
        String normalizedNasdaq = normalize(nasdaq);

        if ("open".equals(normalizedMarket) || "open".equals(normalizedNyse) || "open".equals(normalizedNasdaq)) {
            return new SessionMeta("regular", "Market Open", "session-regular");
        }

        if ("extended-hours".equals(normalizedMarket)
                || "extended-hours".equals(normalizedNyse)
                || "extended-hours".equals(normalizedNasdaq)) {
            if (earlyHours) {
                return new SessionMeta("pre-market", "Pre-Market", "session-pre-market");
            }
            if (afterHours) {
                return new SessionMeta("after-hours", "After-Hours", "session-after-hours");
            }
            // Massive reports extended-hours; default to after-hours when exact flag is absent.
            return new SessionMeta("after-hours", "After-Hours", "session-after-hours");
        }

        return new SessionMeta("closed", "Market Closed", "session-closed");
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private CachedStatus fallbackStatus() {
        return new CachedStatus(
                "closed",
                "Market Closed",
                "session-closed",
                "closed",
                null,
                null,
                null,
                null
        );
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private record SessionMeta(String session, String label, String cssClass) {
    }

    private record CachedStatus(
            String session,
            String label,
            String cssClass,
            String market,
            String nyse,
            String nasdaq,
            Instant serverTime,
            Instant lastUpdated
    ) {
    }
}

