package com.tradepulseai.custservice.kafka;

import com.tradepulseai.custservice.model.Customer;
import customer.events.CustomerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class kafkaProducer {
    private static final Logger log = LoggerFactory.getLogger(kafkaProducer.class);
    //key is of type string and value is of type byte array
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public kafkaProducer(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEvent(Customer customer) {
        CustomerEvent event = CustomerEvent.newBuilder()
                .setCustomerId(customer.getCustomerId().toString())
                .setFirstName(customer.getFirstName())
                .setEmail(customer.getEmail())
                .setEventType("CUSTOMER_CREATED")
                .build();

        try {
            kafkaTemplate.send("customer", event.toByteArray());
        } catch (Exception e) {
            log.error("Error sending CustomerCreated event: {}", event, e);
        }

    }

}
