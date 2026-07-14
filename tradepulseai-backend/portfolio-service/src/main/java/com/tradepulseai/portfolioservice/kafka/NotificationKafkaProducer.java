package com.tradepulseai.portfolioservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

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

    public NotificationKafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishStockSold(Long userId, Long stockId, int quantity, BigDecimal price, BigDecimal total) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stockId", stockId);
            data.put("quantity", quantity);
            data.put("price", price.toPlainString());
            data.put("total", total.toPlainString());

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", "STOCK_SOLD");
            event.put("userId", userId);
            event.put("timestamp", Instant.now().toString());
            event.put("data", data);

            kafkaTemplate.send(TOPIC, objectMapper.writeValueAsString(event));
            log.info("Published STOCK_SOLD notification for userId={}, stockId={}", userId, stockId);
        } catch (Exception ex) {
            log.error("Failed to publish STOCK_SOLD notification for userId={}: {}", userId, ex.getMessage(), ex);
        }
    }
}
