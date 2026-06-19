package com.tradepulseai.custservice.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import com.tradepulseai.custservice.dto.portfolio.PortfolioFillItemRequestDTO;
import com.tradepulseai.custservice.dto.portfolio.RecordPortfolioOrderRequestDTO;
import com.tradepulseai.custservice.service.PortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import portfolio_sync.PortfolioOrderItem;
import portfolio_sync.RecordCompletedOrderRequest;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PortfolioSyncKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSyncKafkaConsumer.class);

    private final PortfolioService portfolioService;

    public PortfolioSyncKafkaConsumer(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @KafkaListener(topics = "${portfolio.sync.kafka.topic:portfolio-sync}", groupId = "${spring.kafka.consumer.group-id:cust-service}")
    public void consumePortfolioSyncEvent(byte[] payload) {
        try {
            RecordCompletedOrderRequest event = RecordCompletedOrderRequest.parseFrom(payload);
            Long userId = Long.parseLong(event.getUserId());

            RecordPortfolioOrderRequestDTO requestDTO = new RecordPortfolioOrderRequestDTO();
            requestDTO.setItems(toItems(event.getItemsList()));

            portfolioService.recordCompletedOrder(userId, requestDTO);
            log.info("Processed portfolio sync event for userId={}, items={}", userId, requestDTO.getItems().size());
        } catch (InvalidProtocolBufferException exception) {
            log.error("Invalid portfolio sync protobuf payload", exception);
        } catch (Exception exception) {
            log.error("Failed to process portfolio sync event", exception);
            throw exception;
        }
    }

    private List<PortfolioFillItemRequestDTO> toItems(List<PortfolioOrderItem> items) {
        return items.stream()
                .map(item -> {
                    PortfolioFillItemRequestDTO dto = new PortfolioFillItemRequestDTO();
                    dto.setStockId(item.getStockId());
                    dto.setPrice(BigDecimal.valueOf(item.getPrice()));
                    dto.setQuantity(item.getQuantity());
                    return dto;
                })
                .toList();
    }
}


