package com.tradepulseai.stockservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulseai.stockservice.dto.market.StockMarketEvent;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockMarketKafkaPersistenceConsumer {

    private static final Logger log = LoggerFactory.getLogger(StockMarketKafkaPersistenceConsumer.class);

    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public StockMarketKafkaPersistenceConsumer(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Transactional
    @KafkaListener(topics = "${stock.kafka.topic:stocks}", groupId = "${stock.kafka.consumer.persistence.group-id:stock-service-db}")
    public void consumeForPersistence(byte[] payload) {
        try {
            StockMarketEvent event = objectMapper.readValue(payload, StockMarketEvent.class);

            Stock stock = stockRepository.findBySymbol(event.symbol()).orElseGet(Stock::new);
            if (stock.getStockId() == null) {
                stock.setSymbol(event.symbol());
            }

            stock.setPrice(event.price());
            stock.setChangePercent(event.changePercent());
            stock.setVolume(event.volume());
            stock.setLastUpdated(event.marketTimestamp());
            stock.setSource(event.source());

            stockRepository.save(stock);
        } catch (Exception exception) {
            log.warn("Unable to persist stock Kafka event.", exception);
        }
    }
}

