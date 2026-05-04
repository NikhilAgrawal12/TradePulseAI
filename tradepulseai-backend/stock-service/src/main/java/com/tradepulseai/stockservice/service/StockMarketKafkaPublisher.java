package com.tradepulseai.stockservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulseai.stockservice.dto.market.StockMarketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class StockMarketKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(StockMarketKafkaPublisher.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final String topic;

    public StockMarketKafkaPublisher(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            @Value("${stock.kafka.topic:stocks}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(StockMarketEvent event) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            kafkaTemplate.send(topic, event.symbol(), payload);
            log.info("Published stock event for symbol {} to topic {}", event.symbol(), topic);
        } catch (Exception e) {
            log.error("Failed to publish stock event for symbol {}", event.symbol(), e);
        }
    }
}
