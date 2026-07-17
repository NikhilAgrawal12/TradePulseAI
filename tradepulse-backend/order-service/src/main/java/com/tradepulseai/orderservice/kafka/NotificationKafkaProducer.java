package com.tradepulseai.orderservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulseai.orderservice.model.TradeOrder;
import com.tradepulseai.orderservice.model.TradeOrderItem;
import com.tradepulseai.orderservice.service.StockCatalogClient;
import com.tradepulseai.orderservice.service.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NotificationKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaProducer.class);
    private static final String TOPIC = "tradepulse.notifications";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StockCatalogClient stockCatalogClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, StockCatalogClient stockCatalogClient) {
        this.kafkaTemplate = kafkaTemplate;
        this.stockCatalogClient = stockCatalogClient;
    }

    public void publishStockPurchased(Long userId, TradeOrder order) {
        publishStockPurchased(userId, null, null, order);
    }

    public void publishStockPurchased(Long userId, String firstName, String lastName, TradeOrder order) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            if (firstName != null && !firstName.isEmpty()) {
                data.put("firstName", firstName);
            }
            if (lastName != null && !lastName.isEmpty()) {
                data.put("lastName", lastName);
            }
            data.put("orderId", order.getId());
            data.put("total", order.getTotal() != null ? order.getTotal().toPlainString() : "0.00");

            // Add stock symbol and quantity from the first order item
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                TradeOrderItem item = order.getItems().getFirst();
                Long stockId = parseStockId(item.getStockId());
                if (stockId != null) {
                    StockQuote quote = stockCatalogClient.getRequiredStockQuote(stockId);
                    data.put("stockId", stockId);
                    data.put("symbol", quote.symbol());
                    if (item.getQuantity() != null) {
                        data.put("quantity", item.getQuantity().toPlainString());
                    }
                    if (item.getPrice() != null) {
                        data.put("price", item.getPrice().toPlainString());
                    }
                }
            }

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", "STOCK_PURCHASED");
            event.put("userId", userId);
            event.put("timestamp", Instant.now().toString());
            event.put("data", data);

            kafkaTemplate.send(TOPIC, objectMapper.writeValueAsString(event));
            log.info("Published STOCK_PURCHASED notification for userId={}, orderId={}", userId, order.getId());
        } catch (Exception ex) {
            log.error("Failed to publish STOCK_PURCHASED notification for userId={}: {}", userId, ex.getMessage(), ex);
        }
    }

    private Long parseStockId(String stockId) {
        if (stockId == null || stockId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(stockId);
        } catch (NumberFormatException exception) {
            log.warn("Invalid stockId format in order notification: {}", stockId);
            return null;
        }
    }
}
