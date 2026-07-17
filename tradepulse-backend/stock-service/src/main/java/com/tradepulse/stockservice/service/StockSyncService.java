package com.tradepulse.stockservice.service;

import com.tradepulse.stockservice.model.Exchange;
import com.tradepulse.stockservice.model.Stock;
import com.tradepulse.stockservice.repository.ExchangeRepository;
import com.tradepulse.stockservice.repository.StockRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Order(2)
public class StockSyncService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockSyncService.class);

    private final StockRepository stockRepository;
    private final ExchangeRepository exchangeRepository;
    private final RestClient restClient;
    private final String apiBaseUrl;
    private final String apiKey;
    private final boolean syncOnStartup;
    private final int targetCount;

    public StockSyncService(
            StockRepository stockRepository,
            ExchangeRepository exchangeRepository,
            @Value("${massive.api.base-url}") String apiBaseUrl,
            @Value("${massive.api.key:}") String apiKey,
            @Value("${massive.stocks.sync-on-startup:false}") boolean syncOnStartup,
            @Value("${massive.stocks.target-count:800}") int targetCount) {
        this.stockRepository = stockRepository;
        this.exchangeRepository = exchangeRepository;
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
        this.syncOnStartup = syncOnStartup;
        this.targetCount = targetCount;
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .build();
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (!syncOnStartup) {
            return;
        }

        if (stockRepository.count() > 0) {
            log.info("Skipping Massive stock sync because stocks table already has data.");
            return;
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("massive.api.key is required when massive.stocks.sync-on-startup=true");
        }

        Map<String, Exchange> exchangeByMic = loadExchangeLookup();
        if (exchangeByMic.isEmpty()) {
            throw new IllegalStateException("No exchanges found. Populate exchanges table before syncing stocks.");
        }

        List<TickerSummary> candidates = fetchTickerCandidates();
        List<RankedTicker> rankedTickers = fetchRankedTickers(candidates, exchangeByMic);
        rankedTickers.sort(Comparator.comparing(RankedTicker::marketCap).reversed().thenComparing(RankedTicker::ticker));

        int limit = Math.min(targetCount, rankedTickers.size());
        List<Stock> stocksToSave = rankedTickers.stream()
                .limit(limit)
                .map(this::toStock)
                .toList();

        stockRepository.saveAll(stocksToSave);
        log.info("Massive stock sync completed. Saved {} stocks (requested target: {}).", stocksToSave.size(), targetCount);
    }

    private Map<String, Exchange> loadExchangeLookup() {
        Map<String, Exchange> byMic = new HashMap<>();
        exchangeRepository.findAll().forEach(exchange -> {
            String mic = normalize(text(exchange.getMic()));
            if (mic != null) {
                byMic.put(mic, exchange);
            }
        });
        return byMic;
    }

    private List<TickerSummary> fetchTickerCandidates() {
        Map<String, TickerSummary> uniqueByTicker = new LinkedHashMap<>();
        String nextPath = buildAllTickersPath();

        while (nextPath != null && !nextPath.isBlank()) {
            JsonNode response = restClient.get()
                    .uri(nextPath)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new IllegalStateException("Massive API returned empty response for tickers endpoint");
            }

            JsonNode resultsNode = response.path("results");
            if (resultsNode.isArray()) {
                for (JsonNode node : resultsNode) {
                    TickerSummary summary = parseTickerSummary(node);
                    if (summary != null && isCommonStockCandidate(summary)) {
                        uniqueByTicker.putIfAbsent(summary.ticker(), summary);
                    }
                }
            } else if (resultsNode.isObject()) {
                TickerSummary summary = parseTickerSummary(resultsNode);
                if (summary != null && isCommonStockCandidate(summary)) {
                    uniqueByTicker.putIfAbsent(summary.ticker(), summary);
                }
            }

            nextPath = normalizeNextPath(text(response.path("next_url")));
        }

        log.info("Fetched {} active US common-stock candidates from Massive.", uniqueByTicker.size());
        return new ArrayList<>(uniqueByTicker.values());
    }

    private List<RankedTicker> fetchRankedTickers(List<TickerSummary> candidates, Map<String, Exchange> exchangeByMic) {
        List<RankedTicker> ranked = new ArrayList<>();
        int processed = 0;

        for (TickerSummary summary : candidates) {
            TickerOverview overview = fetchTickerOverview(summary.ticker());
            if (overview == null || overview.marketCap() <= 0) {
                continue;
            }

            String primaryExchange = normalize(firstNonBlank(overview.primaryExchange(), summary.primaryExchange()));
            Exchange exchange = primaryExchange == null ? null : exchangeByMic.get(primaryExchange);
            if (exchange == null) {
                continue;
            }

            ranked.add(new RankedTicker(summary, overview, exchange));
            processed++;
            if (processed % 250 == 0) {
                log.info("Processed {} ticker overviews so far.", processed);
            }
        }

        return ranked;
    }

    private Stock toStock(RankedTicker rankedTicker) {
        Stock stock = new Stock();
        TickerSummary summary = rankedTicker.summary();
        TickerOverview overview = rankedTicker.overview();

        stock.setSymbol(summary.ticker());
        stock.setName(firstNonBlank(overview.name(), summary.name()));
        stock.setExchange(rankedTicker.exchange());
        stock.setMarket(firstNonBlank(overview.market(), summary.market()));
        stock.setLocale(firstNonBlank(overview.locale(), summary.locale()));
        stock.setType(firstNonBlank(overview.type(), summary.type()));
        stock.setActive(overview.active() != null ? overview.active() : summary.active());
        stock.setSicCode(overview.sicCode());
        stock.setSicDescription(overview.sicDescription());
        stock.setCik(firstNonBlank(overview.cik(), summary.cik()));
        stock.setHomepageUrl(overview.homepageUrl());
        stock.setListDate(overview.listDate());
        stock.setMarketCap(BigDecimal.valueOf(overview.marketCap()));
        return stock;
    }

    private String buildAllTickersPath() {
        return UriComponentsBuilder.fromPath("/v3/reference/tickers")
                .queryParam("market", "stocks")
                .queryParam("active", true)
                .queryParam("locale", "us")
                .queryParam("order", "asc")
                .queryParam("sort", "ticker")
                .queryParam("limit", 1000)
                .queryParam("apiKey", apiKey)
                .build()
                .toUriString();
    }

    private String buildTickerOverviewPath(String ticker) {
        return UriComponentsBuilder.fromPath("/v3/reference/tickers/{ticker}")
                .queryParam("apiKey", apiKey)
                .buildAndExpand(ticker)
                .toUriString();
    }

    private String normalizeNextPath(String nextUrl) {
        if (nextUrl == null || nextUrl.isBlank() || "null".equalsIgnoreCase(nextUrl)) {
            return null;
        }

        String path = nextUrl;
        if (nextUrl.startsWith(apiBaseUrl)) {
            path = nextUrl.substring(apiBaseUrl.length());
        }

        if (!path.contains("apiKey=")) {
            String separator = path.contains("?") ? "&" : "?";
            path = path + separator + "apiKey=" + apiKey;
        }

        return path;
    }

    private TickerSummary parseTickerSummary(JsonNode node) {
        String ticker = normalize(text(node.get("ticker")));
        if (ticker == null) {
            return null;
        }

        return new TickerSummary(
                ticker,
                text(node.get("name")),
                text(node.get("market")),
                text(node.get("locale")),
                text(node.get("type")),
                text(node.get("cik")),
                text(node.get("primary_exchange")),
                booleanOrTrue(node.get("active"))
        );
    }

    private boolean isCommonStockCandidate(TickerSummary summary) {
        return summary.active()
                && "stocks".equalsIgnoreCase(summary.market())
                && "us".equalsIgnoreCase(summary.locale())
                && "CS".equalsIgnoreCase(summary.type());
    }

    private TickerOverview fetchTickerOverview(String ticker) {
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(buildTickerOverviewPath(ticker))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound ex) {
            log.debug("Skipping ticker {} because Massive returned 404 from ticker overview endpoint.", ticker);
            return null;
        }

        if (response == null) {
            return null;
        }

        JsonNode result = response.path("results");
        if (result.isMissingNode() || result.isNull() || !result.isObject()) {
            return null;
        }

        return new TickerOverview(
                text(result.get("name")),
                text(result.get("market")),
                text(result.get("locale")),
                text(result.get("type")),
                text(result.get("cik")),
                text(result.get("primary_exchange")),
                text(result.get("sic_code")),
                text(result.get("sic_description")),
                text(result.get("homepage_url")),
                parseDate(text(result.get("list_date"))),
                booleanOrNull(result.get("active")),
                numberOrZero(result.get("market_cap"))
        );
    }

    private String firstNonBlank(String first, String second) {
        String trimmedFirst = text(first);
        if (trimmedFirst != null) {
            return trimmedFirst;
        }
        return text(second);
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isTextual() ? text(node.textValue()) : text(node.toString());
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalize(String value) {
        String text = text(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private boolean booleanOrTrue(JsonNode node) {
        if (node == null || node.isNull()) {
            return true;
        }
        return node.asBoolean(true);
    }

    private Boolean booleanOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asBoolean();
    }

    private double numberOrZero(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0d;
        }
        return node.asDouble(0d);
    }

    private LocalDate parseDate(String date) {
        String value = text(date);
        return value == null ? null : LocalDate.parse(value);
    }

    private record TickerSummary(
            String ticker,
            String name,
            String market,
            String locale,
            String type,
            String cik,
            String primaryExchange,
            boolean active
    ) {
    }

    private record TickerOverview(
            String name,
            String market,
            String locale,
            String type,
            String cik,
            String primaryExchange,
            String sicCode,
            String sicDescription,
            String homepageUrl,
            LocalDate listDate,
            Boolean active,
            double marketCap
    ) {
    }

    private record RankedTicker(
            TickerSummary summary,
            TickerOverview overview,
            Exchange exchange
    ) {
        private double marketCap() {
            return overview.marketCap();
        }

        private String ticker() {
            return summary.ticker();
        }
    }
}

