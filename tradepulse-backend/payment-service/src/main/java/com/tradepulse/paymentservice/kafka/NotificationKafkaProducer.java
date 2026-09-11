package com.tradepulse.paymentservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class NotificationKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaProducer.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String notificationsTopic;

    public NotificationKafkaProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${tradepulse.kafka.topics.notifications:tradepulse.notifications.events}") String notificationsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.notificationsTopic = notificationsTopic;
    }

    public void publishWalletDeposit(Long userId, String transactionId, BigDecimal amount, BigDecimal newBalance) {
        publish("WALLET_DEPOSIT", userId, Map.of(
                "transactionId", transactionId,
                "amount", amount.toPlainString(),
                "newBalance", newBalance.toPlainString()
        ));
    }


    public void publishWalletWithdrawal(Long userId, String transactionId, BigDecimal amount, BigDecimal newBalance) {
        publish("WALLET_WITHDRAWAL", userId, Map.of(
                "transactionId", transactionId,
                "amount", amount.toPlainString(),
                "newBalance", newBalance.toPlainString()
        ));
    }

    private void publish(String eventType, Long userId, Map<String, Object> data) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", eventType);
            event.put("userId", userId);
            event.put("timestamp", Instant.now().toString());
            event.put("data", data);
            String payload = objectMapper.writeValueAsString(event);
            sendWithAck(userId, payload);
            log.info("Published {} notification for userId={}", eventType, userId);
        } catch (Exception ex) {
            log.error("Failed to publish {} notification for userId={}: {}", eventType, userId, ex.getMessage(), ex);
        }
    }

    private void sendWithAck(Long userId, String payload) throws Exception {
        String key = userId != null ? String.valueOf(userId) : null;
        Exception last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                kafkaTemplate.send(notificationsTopic, key, payload).get(5, TimeUnit.SECONDS);
                return;
            } catch (Exception ex) {
                last = ex;
                log.warn("Notification publish attempt {} failed for userId={}: {}", attempt, userId, ex.getMessage());
            }
        }
        throw last != null ? last : new IllegalStateException("Unknown Kafka publish failure");
    }
}
