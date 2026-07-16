package com.tradepulseai.orderservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulseai.orderservice.model.TradeOrder;
import com.tradepulseai.orderservice.model.TradeOrderItem;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationKafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishStockPurchased(Long userId, TradeOrder order) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderId", order.getId());
            data.put("total", order.getTotal() != null ? order.getTotal().toPlainString() : "0.00");

            // Add stock symbol and quantity from the first order item
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                TradeOrderItem item = order.getItems().getFirst();
                String stockId = item.getStockId();
                if (stockId != null) {
                    data.put("stockId", stockId);
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
}
