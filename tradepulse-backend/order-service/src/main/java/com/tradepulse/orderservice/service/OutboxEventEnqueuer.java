package com.tradepulse.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulse.orderservice.dto.order.CompleteOrderItemRequestDTO;
import com.tradepulse.orderservice.dto.order.CompleteOrderRequestDTO;
import com.tradepulse.orderservice.model.OutboxEvent;
import com.tradepulse.orderservice.model.TradeOrder;
import com.tradepulse.orderservice.model.TradeOrderItem;
import com.tradepulse.orderservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes ORDER_COMPLETED and STOCK_PURCHASED outbox entries inside the active transaction,
 * so no events are ever lost when Kafka is temporarily unavailable.
 */
@Service
public class OutboxEventEnqueuer {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventEnqueuer.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String ordersTopic;
    private final String notificationsTopic;

    public OutboxEventEnqueuer(
            OutboxEventRepository outboxEventRepository,
            @Value("${tradepulse.kafka.topics.orders:tradepulse.orders.events}") String ordersTopic,
            @Value("${tradepulse.kafka.topics.notifications:tradepulse.notifications.events}") String notificationsTopic
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.ordersTopic = ordersTopic;
        this.notificationsTopic = notificationsTopic;
    }

    /**
     * Persists outbox entries for both ORDER_COMPLETED and STOCK_PURCHASED within the caller's
     * open transaction.  Must be called before the transaction commits.
     */
    public void enqueue(TradeOrder savedOrder, CompleteOrderRequestDTO lockedRequest) {
        outboxEventRepository.save(buildOrderCompletedEntry(savedOrder));
        outboxEventRepository.save(buildStockPurchasedEntry(savedOrder, lockedRequest));
    }

    // -------------------------------------------------------------------------
    // ORDER_COMPLETED
    // -------------------------------------------------------------------------

    private OutboxEvent buildOrderCompletedEntry(TradeOrder order) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", order.getTotal() != null ? order.getTotal().toPlainString() : "0.00");
            data.put("items", toOrderItems(order.getItems()));

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", "ORDER_COMPLETED");
            event.put("eventId", UUID.randomUUID().toString());
            event.put("orderId", order.getId());
            event.put("userId", order.getUserId());
            event.put("timestamp", Instant.now().toString());
            event.put("data", data);

            return newEntry(order.getId(), "ORDER_COMPLETED", ordersTopic,
                    objectMapper.writeValueAsString(event), order.getId());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build ORDER_COMPLETED outbox entry for orderId=" + order.getId(), ex);
        }
    }

    private List<Map<String, Object>> toOrderItems(List<TradeOrderItem> items) {
        return items.stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stockId", item.getStockId());
            m.put("price", item.getPrice() != null ? item.getPrice().toPlainString() : "0.00");
            m.put("quantity", toWholeQuantity(item));
            return m;
        }).toList();
    }

    private int toWholeQuantity(TradeOrderItem item) {
        try {
            return item.getQuantity().stripTrailingZeros().intValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Outbox: whole-number quantity required for stockId=" + item.getStockId(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // STOCK_PURCHASED  (notification event — uses symbol from locked request)
    // -------------------------------------------------------------------------

    private OutboxEvent buildStockPurchasedEntry(TradeOrder order, CompleteOrderRequestDTO lockedRequest) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderId", order.getId());
            data.put("total", order.getTotal() != null ? order.getTotal().toPlainString() : "0.00");

            if (lockedRequest.getItems() != null && !lockedRequest.getItems().isEmpty()) {
                CompleteOrderItemRequestDTO first = lockedRequest.getItems().getFirst();
                data.put("stockId", first.getStockId());
                data.put("symbol", first.getSymbol());
                if (first.getQuantity() != null) {
                    data.put("quantity", first.getQuantity().toPlainString());
                }
                if (first.getPrice() != null) {
                    data.put("price", first.getPrice().toPlainString());
                }
            }

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", "STOCK_PURCHASED");
            event.put("userId", order.getUserId());
            event.put("timestamp", Instant.now().toString());
            event.put("data", data);

            return newEntry(order.getId(), "STOCK_PURCHASED", notificationsTopic,
                    objectMapper.writeValueAsString(event), null);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build STOCK_PURCHASED outbox entry for orderId=" + order.getId(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OutboxEvent newEntry(String orderId, String eventType, String topic, String payload, String partitionKey) {
        OutboxEvent entry = new OutboxEvent();
        entry.setOrderId(orderId);
        entry.setEventType(eventType);
        entry.setTopic(topic);
        entry.setPayload(payload);
        entry.setPartitionKey(partitionKey);
        entry.setStatus(OutboxEvent.Status.PENDING.name());
        return entry;
    }
}

