package com.tradepulse.apigateway.filter;

import jakarta.annotation.PreDestroy;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitGatewayFilterFactory extends AbstractGatewayFilterFactory<RateLimitGatewayFilterFactory.Config> {

    private static final String HEADER_RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    private static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset";
    private static final String HEADER_RETRY_AFTER = "Retry-After";
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);
    private static final int DEFAULT_LIMIT = 60;

    private final ConcurrentMap<String, CounterWindow> windows = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "gateway-rate-limit-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    public RateLimitGatewayFilterFactory() {
        super(Config.class);
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredWindows, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("limit", "windowSeconds", "keyType");
    }

    @Override
    public GatewayFilter apply(Config config) {
        final int limit = config.getLimit() > 0 ? config.getLimit() : DEFAULT_LIMIT;
        final Duration window = config.getWindowSeconds() > 0
                ? Duration.ofSeconds(config.getWindowSeconds())
                : DEFAULT_WINDOW;
        final String keyType = normalizeKeyType(config.getKeyType());

        return (exchange, chain) -> {
            long now = System.currentTimeMillis();
            Route route = exchange.getAttribute("org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayRoute");
            String routeId = route != null ? route.getId() : "unknown-route";
            String clientKey = resolveClientKey(exchange.getRequest(), keyType);
            String counterKey = routeId + '|' + keyType + '|' + clientKey;

            CounterWindow state = windows.compute(counterKey, (ignored, current) -> {
                if (current == null || current.isExpired(now, window)) {
                    return new CounterWindow(now, 1);
                }
                current.increment();
                return current;
            });

            long resetAt = state.windowStartedAt() + window.toMillis();
            long remainingMs = Math.max(0L, resetAt - now);
            int remaining = Math.max(0, limit - state.count());

            exchange.getResponse().getHeaders().set(HEADER_RATE_LIMIT_LIMIT, String.valueOf(limit));
            exchange.getResponse().getHeaders().set(HEADER_RATE_LIMIT_REMAINING, String.valueOf(remaining));
            exchange.getResponse().getHeaders().set(HEADER_RATE_LIMIT_RESET, String.valueOf(Math.max(1L, (long) Math.ceil(remainingMs / 1000.0))));

            if (state.count() > limit) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().set(HEADER_RETRY_AFTER, String.valueOf(Math.max(1L, (long) Math.ceil(remainingMs / 1000.0))));
                return exchange.getResponse().setComplete();
            }

            return chain.filter(exchange);
        };
    }

    private String resolveClientKey(ServerHttpRequest request, String keyType) {
        if ("user".equals(keyType)) {
            String userId = request.getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return userId.trim();
            }
        }

        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] segments = forwardedFor.split(",");
            if (segments.length > 0 && !segments[0].isBlank()) {
                return segments[0].trim();
            }
        }

        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown-client";
    }

    private String normalizeKeyType(String keyType) {
        if (keyType == null || keyType.isBlank()) {
            return "ip";
        }

        String normalized = keyType.trim().toLowerCase();
        return "user".equals(normalized) ? "user" : "ip";
    }

    private void cleanupExpiredWindows() {
        long now = System.currentTimeMillis();
        List<String> keysToRemove = new ArrayList<>();
        windows.forEach((key, value) -> {
            if (value.windowStartedAt() + DEFAULT_WINDOW.multipliedBy(10).toMillis() < now) {
                keysToRemove.add(key);
            }
        });
        keysToRemove.forEach(windows::remove);
    }

    @PreDestroy
    public void shutdown() {
        cleanupExecutor.shutdownNow();
    }

    @SuppressWarnings("unused")
    public static class Config {
        private int limit = DEFAULT_LIMIT;
        private long windowSeconds = DEFAULT_WINDOW.toSeconds();
        private String keyType = "ip";

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public String getKeyType() {
            return keyType;
        }

        public void setKeyType(String keyType) {
            this.keyType = keyType;
        }
    }

    private static final class CounterWindow {
        private final long windowStartedAt;
        private final AtomicInteger count;

        private CounterWindow(long windowStartedAt, int initialCount) {
            this.windowStartedAt = windowStartedAt;
            this.count = new AtomicInteger(initialCount);
        }

        private boolean isExpired(long now, Duration window) {
            return now - windowStartedAt >= window.toMillis();
        }

        private void increment() {
            count.incrementAndGet();
        }

        private int count() {
            return count.get();
        }

        private long windowStartedAt() {
            return windowStartedAt;
        }
    }
}


