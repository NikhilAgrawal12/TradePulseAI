package com.tradepulseai.analyticsservice.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import customer.events.CustomerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    @KafkaListener(topics="customer", groupId="analytics-service")
    public void consumeEvent(byte[] event) {
        try {
            CustomerEvent customerEvent = CustomerEvent.parseFrom(event);
            // perform any business related to analytics here

            log.info("Received Customer Event: [CustomerId={}, CustomerName={}, CustomerEmail={}]",
                    customerEvent.getCustomerId(),
                    customerEvent.getFirstName(),
                    customerEvent.getEmail());
        } catch (InvalidProtocolBufferException e) {
            log.error("invalid event", e);
        }



    }
}

