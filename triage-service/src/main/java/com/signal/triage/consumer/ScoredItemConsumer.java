package com.signal.triage.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.signal.common.messaging.ScoredItemEvent;
import com.signal.triage.entity.TriageItem;
import com.signal.triage.repository.TriageItemRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ScoredItemConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScoredItemConsumer.class);

    private final TriageItemRepository triageItemRepository;
    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${signal.pubsub.scored-items-subscription:signal-triage-consumer}")
    private String subscriptionName;

    @Autowired
    public ScoredItemConsumer(TriageItemRepository triageItemRepository,
                              @Autowired(required = false) PubSubTemplate pubSubTemplate,
                              ObjectMapper objectMapper) {
        this.triageItemRepository = triageItemRepository;
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initPubSubSubscriber() {
        if (pubSubTemplate != null) {
            log.info("Subscribing to Pub/Sub subscription: {}", subscriptionName);
            pubSubTemplate.subscribe(subscriptionName, message -> {
                try {
                    ScoredItemEvent event = objectMapper.readValue(
                            message.getPubsubMessage().getData().toStringUtf8(),
                            ScoredItemEvent.class
                    );
                    consume(event);
                    message.ack();
                } catch (Exception e) {
                    log.error("Failed to process Pub/Sub scored item event", e);
                    message.nack();
                }
            });
        }
    }

    @SqsListener("${signal.queues.scored-items:signal-scored-items}")
    public void consume(ScoredItemEvent event) {
        log.info("Received Scored Item Event: ID={}, Urgency={}, GroupID={}",
                event.scoredItemId(), event.urgency(), event.threadGroupId());
        
        try {
            TriageItem item = new TriageItem(
                    event.userId(),
                    event.threadGroupId(),
                    event.source(),
                    event.urgency(),
                    event.reasonSnippet(),
                    event.senderId(),
                    event.receivedAt(),
                    "UNSEEN"
            );
            triageItemRepository.save(item);
            log.info("TriageItem persisted: {}", item.getId());
        } catch (Exception e) {
            log.error("Failed to save triage item", e);
            throw new RuntimeException("Failed to save triage item", e);
        }
    }
}
