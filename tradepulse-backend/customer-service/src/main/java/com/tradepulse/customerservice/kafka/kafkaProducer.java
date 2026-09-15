package com.tradepulse.customerservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulse.customerservice.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class kafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(kafkaProducer.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String notificationsTopic;

    public kafkaProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${tradepulse.kafka.topics.notifications:tradepulse.notifications.events}") String notificationsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.notificationsTopic = notificationsTopic;
    }

    public void sendEvent(Customer customer) {
        try {
            kafkaTemplate.send(notificationsTopic, buildEventJson(customer));
        } catch (Exception e) {
            log.error("Error sending ACCOUNT_CREATED notification event for userId={}", customer.getUserId(), e);
        }
    }

    public void sendEventOrThrow(Customer customer) {
        try {
            kafkaTemplate.send(notificationsTopic, buildEventJson(customer)).join();
        } catch (Exception exception) {
            throw new IllegalStateException("Error sending ACCOUNT_CREATED notification event", exception);
        }
    }

    public void sendAccountDeletedEvent(Long userId) {
        try {
            kafkaTemplate.send(notificationsTopic, buildAccountDeletedEventJson(userId));
        } catch (Exception e) {
            log.error("Error sending ACCOUNT_DELETED notification event for userId={}", userId, e);
        }
    }

    private String buildEventJson(Customer customer) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("firstName", customer.getFirstName());
        data.put("lastName", customer.getLastName());

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", "ACCOUNT_CREATED");
        event.put("userId", customer.getUserId());
        event.put("timestamp", Instant.now().toString());
        event.put("data", data);

        return objectMapper.writeValueAsString(event);
    }

    private String buildAccountDeletedEventJson(Long userId) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", "ACCOUNT_DELETED");
        event.put("userId", userId);
        event.put("timestamp", Instant.now().toString());
        event.put("data", Map.of());
        return objectMapper.writeValueAsString(event);
    }
}
