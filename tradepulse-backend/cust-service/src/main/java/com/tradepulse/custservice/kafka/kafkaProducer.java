package com.tradepulse.custservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulse.custservice.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class kafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(kafkaProducer.class);
    private static final String TOPIC = "tradepulse.notifications";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public kafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEvent(Customer customer, String email) {
        try {
            kafkaTemplate.send(TOPIC, buildEventJson(customer));
        } catch (Exception e) {
            log.error("Error sending ACCOUNT_CREATED notification event for userId={}", customer.getUserId(), e);
        }
    }

    public void sendEventOrThrow(Customer customer, String email) {
        try {
            kafkaTemplate.send(TOPIC, buildEventJson(customer)).join();
        } catch (Exception exception) {
            throw new IllegalStateException("Error sending ACCOUNT_CREATED notification event", exception);
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
}
