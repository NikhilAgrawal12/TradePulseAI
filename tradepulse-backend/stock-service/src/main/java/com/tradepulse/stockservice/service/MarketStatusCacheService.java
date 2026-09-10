package com.tradepulse.stockservice.service;

import com.tradepulse.stockservice.dto.market.MarketStatusResponseDTO;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Order(6)
public class MarketStatusCacheService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketStatusCacheService.class);
    private static final long REFRESH_INTERVAL_SECONDS = 60;
    private static final long STALE_THRESHOLD_SECONDS = 60;
    private static final long SSE_TIMEOUT_MS = 0L;
    private static final long SSE_HEARTBEAT_INTERVAL_SECONDS = 25;
    private static final int SSE_RECONNECT_MS = 3000;
    private static final ZoneId MARKET_TIMEZONE = ZoneId.of("America/New_York");

    private final RestClient restClient;
    private final String apiKey;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<CachedStatus> statusRef = new AtomicReference<>(currentFallbackStatus());
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

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
        scheduler.scheduleAtFixedRate(
                this::refreshCache,
                0,
                REFRESH_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
        scheduler.scheduleAtFixedRate(
                this::sendHeartbeatToClients,
                SSE_HEARTBEAT_INTERVAL_SECONDS,
                SSE_HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        log.info("Market status cache started with {}s refresh interval.", REFRESH_INTERVAL_SECONDS);
    }

    public MarketStatusResponseDTO getCurrentStatus() {
        CachedStatus cached = statusRef.get();
        boolean stale = cached.lastUpdated() == null
                || cached.lastUpdated().plusSeconds(STALE_THRESHOLD_SECONDS).isBefore(Instant.now());

        if (stale) {
            CachedStatus fallback = currentFallbackStatus();
            statusRef.compareAndSet(cached, fallback);
            return toDto(fallback, false);
        }

        return toDto(cached, stale);
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("market-status")
                    .data(getCurrentStatus())
                    .reconnectTime(SSE_RECONNECT_MS));
        } catch (Exception ex) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    private MarketStatusResponseDTO toDto(CachedStatus cached, boolean stale) {
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

    private void broadcastCurrentStatus() {
        if (emitters.isEmpty()) {
            return;
        }

        MarketStatusResponseDTO dto = getCurrentStatus();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("market-status")
                        .data(dto)
                        .reconnectTime(SSE_RECONNECT_MS));
            } catch (Exception ex) {
                emitters.remove(emitter);
            }
        }
    }

    private void sendHeartbeatToClients() {
        if (emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data(System.currentTimeMillis())
                        .reconnectTime(SSE_RECONNECT_MS));
            } catch (Exception ex) {
                emitters.remove(emitter);
            }
        }
    }

    private void refreshCache() {
        if (apiKey == null || apiKey.isBlank()) {
            CachedStatus fallback = currentFallbackStatus();
            statusRef.set(fallback);
            broadcastCurrentStatus();
            log.debug("Using local market status fallback because massive.api.key is missing.");
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
                CachedStatus fallback = currentFallbackStatus();
                statusRef.set(fallback);
                broadcastCurrentStatus();
                log.warn("Received null response from /v1/marketstatus/now. Using local market status fallback.");
                return;
            }

            CachedStatus mapped = mapResponse(response);
            statusRef.set(mapped);
            broadcastCurrentStatus();
        } catch (Exception ex) {
            CachedStatus fallback = currentFallbackStatus();
            statusRef.set(fallback);
            broadcastCurrentStatus();
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

    private CachedStatus currentFallbackStatus() {
        ZonedDateTime nowEt = ZonedDateTime.now(MARKET_TIMEZONE);
        DayOfWeek dayOfWeek = nowEt.getDayOfWeek();
        LocalTime time = nowEt.toLocalTime();

        SessionMeta sessionMeta;
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            sessionMeta = new SessionMeta("closed", "Market Closed", "session-closed");
        } else if (!time.isBefore(LocalTime.of(4, 0)) && time.isBefore(LocalTime.of(9, 30))) {
            sessionMeta = new SessionMeta("pre-market", "Pre-Market", "session-pre-market");
        } else if (!time.isBefore(LocalTime.of(9, 30)) && time.isBefore(LocalTime.of(16, 0))) {
            sessionMeta = new SessionMeta("regular", "Market Open", "session-regular");
        } else if (!time.isBefore(LocalTime.of(16, 0)) && time.isBefore(LocalTime.of(20, 0))) {
            sessionMeta = new SessionMeta("after-hours", "After-Hours", "session-after-hours");
        } else {
            sessionMeta = new SessionMeta("closed", "Market Closed", "session-closed");
        }

        return new CachedStatus(
                sessionMeta.session(),
                sessionMeta.label(),
                sessionMeta.cssClass(),
                "derived",
                sessionMeta.session(),
                sessionMeta.session(),
                nowEt.toInstant(),
                Instant.now()
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

