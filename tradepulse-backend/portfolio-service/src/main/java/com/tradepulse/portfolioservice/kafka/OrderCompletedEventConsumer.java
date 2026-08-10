package com.tradepulse.portfolioservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulse.portfolioservice.dto.PortfolioFillItemRequestDTO;
import com.tradepulse.portfolioservice.dto.RecordPortfolioOrderRequestDTO;
import com.tradepulse.portfolioservice.model.ProcessedEvent;
import com.tradepulse.portfolioservice.repository.ProcessedEventRepository;
import com.tradepulse.portfolioservice.service.PortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class OrderCompletedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletedEventConsumer.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PortfolioService portfolioService;
    private final ProcessedEventRepository processedEventRepository;

    public OrderCompletedEventConsumer(
            PortfolioService portfolioService,
            ProcessedEventRepository processedEventRepository
    ) {
        this.portfolioService = portfolioService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(
            topics = "${tradepulse.kafka.topics.orders:tradepulse.orders.events}",
            groupId = "${spring.kafka.consumer.group-id:portfolio-service}"
    )
    @Transactional
    public void consume(String message) {
        OrderCompletedEvent event;
        try {
            event = objectMapper.readValue(message, OrderCompletedEvent.class);
        } catch (Exception ex) {
            log.error("Failed to deserialize ORDER_COMPLETED event – skipping: {}", message, ex);
            return;
        }

        if (!"ORDER_COMPLETED".equalsIgnoreCase(event.getEventType())) {
            return;
        }

        if (event.getUserId() == null || event.getData() == null
                || event.getData().getItems() == null || event.getData().getItems().isEmpty()) {
            log.warn("Skipping malformed ORDER_COMPLETED event: {}", message);
            return;
        }

        // ── Idempotency guard ──────────────────────────────────────────────
        String eventId = event.getEventId();
        if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
            log.info("Skipping duplicate ORDER_COMPLETED eventId={} orderId={}",
                    eventId, event.getOrderId());
            return;
        }

        // ── Process ────────────────────────────────────────────────────────
        RecordPortfolioOrderRequestDTO request = new RecordPortfolioOrderRequestDTO();
        request.setItems(toPortfolioItems(event.getData().getItems()));
        portfolioService.applyCompletedOrder(event.getUserId(), request);

        // Mark as processed inside the same transaction
        if (eventId != null) {
            ProcessedEvent processed = new ProcessedEvent();
            processed.setEventId(eventId);
            processedEventRepository.save(processed);
        }

        log.info("Processed ORDER_COMPLETED eventId={} orderId={} userId={} items={}",
                eventId, event.getOrderId(), event.getUserId(), request.getItems().size());
    }

    private List<PortfolioFillItemRequestDTO> toPortfolioItems(List<OrderCompletedEvent.OrderCompletedItem> items) {
        return items.stream().map(item -> {
            PortfolioFillItemRequestDTO dto = new PortfolioFillItemRequestDTO();
            dto.setStockId(item.getStockId());
            dto.setPrice(new BigDecimal(item.getPrice()));
            dto.setQuantity(item.getQuantity() == null ? 0 : item.getQuantity());
            return dto;
        }).toList();
    }
}
