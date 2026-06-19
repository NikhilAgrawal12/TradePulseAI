package com.tradepulseai.orderservice.service;

import com.tradepulseai.orderservice.dto.portfolio.PortfolioOrderSyncRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import portfolio_sync.PortfolioOrderItem;
import portfolio_sync.RecordCompletedOrderRequest;

import java.util.concurrent.TimeUnit;

@Service
public class PortfolioSyncClient {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSyncClient.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final String portfolioSyncTopic;

    public PortfolioSyncClient(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            @Value("${portfolio.sync.kafka.topic:portfolio-sync}") String portfolioSyncTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.portfolioSyncTopic = portfolioSyncTopic;
    }

    public void syncCompletedOrder(Long userId, PortfolioOrderSyncRequestDTO request) {
        try {
            RecordCompletedOrderRequest event = RecordCompletedOrderRequest.newBuilder()
                    .setUserId(String.valueOf(userId))
                    .addAllItems(
                            request.getItems().stream()
                                    .map(item -> PortfolioOrderItem.newBuilder()
                                            .setStockId(item.getStockId())
                                            .setPrice(item.getPrice().doubleValue())
                                            .setQuantity(item.getQuantity())
                                            .build())
                                    .toList()
                    )
                    .build();

            kafkaTemplate.send(portfolioSyncTopic, String.valueOf(userId), event.toByteArray())
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.error("Failed to sync completed order to portfolio for userId {}", userId, exception);
            throw new IllegalStateException("Payment completed, but portfolio update failed. Please retry.");
        }
    }
}

