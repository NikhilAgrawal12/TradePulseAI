package com.tradepulse.orderservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulse.orderservice.model.TradeOrder;
import com.tradepulse.orderservice.model.TradeOrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderEventKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String ordersTopic;

    public OrderEventKafkaProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${tradepulse.kafka.topics.orders:tradepulse.orders.events}") String ordersTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.ordersTopic = ordersTopic;
    }

    public void publishOrderCompleted(TradeOrder order) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", order.getTotal() != null ? order.getTotal().toPlainString() : "0.00");
            data.put("items", toItems(order.getItems()));

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", "ORDER_COMPLETED");
            event.put("eventId", UUID.randomUUID().toString());
            event.put("orderId", order.getId());
            event.put("userId", order.getUserId());
            event.put("timestamp", Instant.now().toString());
            event.put("data", data);

            kafkaTemplate.send(ordersTopic, order.getId(), objectMapper.writeValueAsString(event));
            log.info("Published ORDER_COMPLETED event for orderId={}, userId={}", order.getId(), order.getUserId());
        } catch (Exception exception) {
            log.error("Failed to publish ORDER_COMPLETED event for orderId={}: {}",
                    order.getId(), exception.getMessage(), exception);
        }
    }

    private List<Map<String, Object>> toItems(List<TradeOrderItem> items) {
        return items.stream()
                .map(item -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("stockId", item.getStockId());
                    payload.put("price", item.getPrice() != null ? item.getPrice().toPlainString() : "0.00");
                    payload.put("quantity", toWholeQuantity(item));
                    return payload;
                })
                .toList();
    }

    private int toWholeQuantity(TradeOrderItem item) {
        try {
            return item.getQuantity().stripTrailingZeros().intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Order event supports whole-number quantity only for stockId: " + item.getStockId(),
                    exception
            );
        }
    }
}

