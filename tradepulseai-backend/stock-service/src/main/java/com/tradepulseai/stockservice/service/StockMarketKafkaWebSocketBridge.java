package com.tradepulseai.stockservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulseai.stockservice.dto.market.StockMarketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class StockMarketKafkaWebSocketBridge {

    private static final Logger log = LoggerFactory.getLogger(StockMarketKafkaWebSocketBridge.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public StockMarketKafkaWebSocketBridge(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = "${stock.kafka.topic:stocks}", groupId = "${stock.kafka.consumer.websocket.group-id:stock-service-ws}")
    public void consumeStockUpdate(byte[] payload) {
        try {
            StockMarketEvent event = objectMapper.readValue(payload, StockMarketEvent.class);
            messagingTemplate.convertAndSend("/topic/stocks", event);
        } catch (Exception e) {
            log.warn("Unable to bridge stock Kafka event to websocket.", e);
        }
    }
}
