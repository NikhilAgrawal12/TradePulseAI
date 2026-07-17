package com.tradepulse.notificationservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulse.notificationservice.client.AuthServiceClient;
import com.tradepulse.notificationservice.event.NotificationEvent;
import com.tradepulse.notificationservice.service.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final AuthServiceClient authServiceClient;
    private final EmailNotificationService emailNotificationService;

    public NotificationEventConsumer(
            ObjectMapper objectMapper,
            AuthServiceClient authServiceClient,
            EmailNotificationService emailNotificationService
    ) {
        this.objectMapper = objectMapper;
        this.authServiceClient = authServiceClient;
        this.emailNotificationService = emailNotificationService;
    }

    @KafkaListener(topics = "tradepulse.notifications", groupId = "notification-service")
    public void consume(String message) {
        log.debug("Received notification event: {}", message);
        try {
            NotificationEvent event = objectMapper.readValue(message, NotificationEvent.class);
            log.info("Processing notification event: eventType={}, userId={}", event.getEventType(), event.getUserId());

            if (event.getUserId() == null) {
                log.warn("Notification event has no userId, skipping: {}", message);
                return;
            }

            String email = authServiceClient.getEmailByUserId(event.getUserId());
            if (email == null) {
                log.warn("Could not resolve email for userId={}, skipping notification for eventType={}",
                        event.getUserId(), event.getEventType());
                return;
            }

            emailNotificationService.sendNotification(event, email);
        } catch (Exception ex) {
            log.error("Failed to process notification event: {}", message, ex);
        }
    }
}

