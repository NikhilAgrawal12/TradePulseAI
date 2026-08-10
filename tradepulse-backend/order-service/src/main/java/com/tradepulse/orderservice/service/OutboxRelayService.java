package com.tradepulse.orderservice.service;

import com.tradepulse.orderservice.model.OutboxEvent;
import com.tradepulse.orderservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Polls the outbox_events table for IN_PROGRESS rows (claimed by the scheduled method)
 * and publishes them to Kafka.  Uses {@code SKIP LOCKED} on the claim step so concurrent
 * service replicas never process the same batch.
 * <p>
 * Delivery guarantee: <b>at-least-once</b>.  Consumers must be idempotent.
 */
@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate txTemplate;

    public OutboxRelayService(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            PlatformTransactionManager txManager
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    /**
     * Step 1 – claim up to 50 PENDING rows (UPDATE … SKIP LOCKED).
     * Step 2 – publish the claimed rows outside any DB transaction.
     * Step 3 – mark each row PUBLISHED or FAILED in its own transaction.
     */
    @Scheduled(fixedDelayString = "${tradepulse.outbox.relay.fixed-delay-ms:3000}")
    public void claimAndFlush() {
        Integer claimed = txTemplate.execute(status -> outboxEventRepository.claimPendingBatch());
        if (claimed == null || claimed == 0) {
            return;
        }
        log.debug("Outbox relay: claimed {} row(s)", claimed);

        List<OutboxEvent> rows = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.Status.IN_PROGRESS.name());

        for (OutboxEvent row : rows) {
            publishOne(row);
        }
    }

    private void publishOne(OutboxEvent row) {
        try {
            kafkaTemplate.send(row.getTopic(), row.getPartitionKey(), row.getPayload()).get();
            markAs(row.getId(), OutboxEvent.Status.PUBLISHED);
            log.info("Outbox relay: published outbox_id={} eventType={} topic={}",
                    row.getId(), row.getEventType(), row.getTopic());
        } catch (Exception ex) {
            markAs(row.getId(), OutboxEvent.Status.FAILED);
            log.error("Outbox relay: FAILED to publish outbox_id={} eventType={}: {}",
                    row.getId(), row.getEventType(), ex.getMessage(), ex);
        }
    }

    private void markAs(String id, OutboxEvent.Status status) {
        txTemplate.execute(s -> {
            outboxEventRepository.markAs(id, status.name(), Instant.now());
            return null;
        });
    }
}
