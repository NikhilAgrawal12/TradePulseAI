package com.tradepulseai.portfolioservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulseai.portfolioservice.client.StockCatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NotificationKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaProducer.class);
    private static final String TOPIC = "tradepulse.notifications";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    public NotificationKafkaProducer(KafkaTemplate<String, String> kafkaTemplate,
                                     @Value("${stock.service.base-url:http://stock-service:4003}") String stockServiceBaseUrl) {
        this.kafkaTemplate = kafkaTemplate;
        this.restClient = RestClient.builder().baseUrl(stockServiceBaseUrl).build();
    }

    public void publishStockSold(Long userId, Long stockId, int quantity, BigDecimal price, BigDecimal total) {
        try {
            String symbol = fetchStockSymbol(stockId);
            
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("symbol", symbol);
            data.put("quantity", quantity);
            data.put("price", price.toPlainString());
            data.put("total", total.toPlainString());

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", "STOCK_SOLD");
            event.put("userId", userId);
            event.put("timestamp", Instant.now().toString());
            event.put("data", data);

            kafkaTemplate.send(TOPIC, objectMapper.writeValueAsString(event));
            log.info("Published STOCK_SOLD notification for userId={}, symbol={}", userId, symbol);
        } catch (Exception ex) {
            log.error("Failed to publish STOCK_SOLD notification for userId={}: {}", userId, ex.getMessage(), ex);
        }
    }

    private String fetchStockSymbol(Long stockId) {
        try {
            StockResponse response = restClient.get()
                    .uri("/stocks/{id}", stockId)
                    .retrieve()
                    .body(StockResponse.class);
            
            if (response != null && response.symbol != null && !response.symbol.isBlank()) {
                return response.symbol.toUpperCase();
            }
        } catch (Exception ex) {
            log.warn("Unable to fetch stock symbol for stockId={}: {}", stockId, ex.getMessage());
        }
        return "UNKNOWN";
    }

    @SuppressWarnings("unused")
    private static class StockResponse {
        public Long id;
        public String symbol;
        public String name;
    }
}
