package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.model.Exchange;
import com.tradepulseai.stockservice.repository.ExchangeRepository;
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

import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
public class ExchangeSyncService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExchangeSyncService.class);

    private final ExchangeRepository exchangeRepository;
    private final RestClient restClient;
    private final String apiBaseUrl;
    private final String apiKey;
    private final boolean syncOnStartup;

    public ExchangeSyncService(
            ExchangeRepository exchangeRepository,
            @Value("${massive.api.base-url}") String apiBaseUrl,
            @Value("${massive.api.key:}") String apiKey,
            @Value("${massive.exchanges.sync-on-startup:false}") boolean syncOnStartup) {
        this.exchangeRepository = exchangeRepository;
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
        this.syncOnStartup = syncOnStartup;
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .build();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!syncOnStartup) {
            return;
        }

        if (exchangeRepository.count() > 0) {
            log.info("Skipping Massive exchange sync because exchanges table already has data.");
            return;
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("massive.api.key is required when massive.exchanges.sync-on-startup=true");
        }

        int syncedRecords = syncExchanges();
        log.info("Massive exchange sync completed. Upserted {} exchange records.", syncedRecords);
    }

    private int syncExchanges() {
        String nextPath = buildInitialPath();
        int upserted = 0;

        while (nextPath != null && !nextPath.isBlank()) {
            JsonNode response = restClient.get()
                    .uri(nextPath)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new IllegalStateException("Massive API returned empty response for exchanges endpoint");
            }

            List<Exchange> exchanges = parseExchanges(response.path("results"));
            if (!exchanges.isEmpty()) {
                exchangeRepository.saveAll(exchanges);
                upserted += exchanges.size();
            }

            nextPath = normalizeNextPath(response.path("next_url").asText(null));
        }

        return upserted;
    }

    private String buildInitialPath() {
        return UriComponentsBuilder.fromPath("/v3/reference/exchanges")
                .queryParam("asset_class", "stocks")
                .queryParam("locale", "us")
                .queryParam("apiKey", apiKey)
                .build()
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

    private List<Exchange> parseExchanges(JsonNode resultsNode) {
        List<Exchange> exchanges = new ArrayList<>();
        if (resultsNode == null || resultsNode.isMissingNode() || resultsNode.isNull()) {
            return exchanges;
        }

        if (resultsNode.isArray()) {
            for (JsonNode exchangeNode : resultsNode) {
                Exchange exchange = toExchange(exchangeNode);
                if (exchange != null) {
                    exchanges.add(exchange);
                }
            }
            return exchanges;
        }

        if (resultsNode.isObject()) {
            Exchange exchange = toExchange(resultsNode);
            if (exchange != null) {
                exchanges.add(exchange);
            }
        }

        return exchanges;
    }

    private Exchange toExchange(JsonNode node) {
        JsonNode idNode = node.get("id");
        if (idNode == null || idNode.isNull()) {
            return null;
        }

        Exchange exchange = new Exchange();
        exchange.setExchangeId(idNode.asInt());
        exchange.setAcronym(textValue(node, "acronym"));
        exchange.setAssetClass(textValue(node, "asset_class"));
        exchange.setLocale(textValue(node, "locale"));
        exchange.setMic(textValue(node, "mic"));
        exchange.setName(textValue(node, "name"));
        exchange.setStatus(textValue(node, "status"));
        exchange.setOperatingMic(textValue(node, "operating_mic"));
        exchange.setParticipantId(textValue(node, "participant_id"));
        exchange.setType(textValue(node, "type"));
        exchange.setUrl(textValue(node, "url"));
        return exchange;
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        return field.asText();
    }
}

