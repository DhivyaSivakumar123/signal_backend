package com.signal.integration.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.signal.common.messaging.InterruptionSignalEvent;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InterruptionSignalPublisher {

    private static final Logger log = LoggerFactory.getLogger(InterruptionSignalPublisher.class);

    private final SqsTemplate sqsTemplate;
    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${signal.pubsub.interruption-signals-topic:signal-interruption-signals}")
    private String pubsubTopic;

    @Value("${signal.queues.interruption-signals:signal-interruption-signals}")
    private String queueName;

    @Autowired
    public InterruptionSignalPublisher(@Autowired(required = false) SqsTemplate sqsTemplate,
                                       @Autowired(required = false) PubSubTemplate pubSubTemplate,
                                       ObjectMapper objectMapper) {
        this.sqsTemplate = sqsTemplate;
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(InterruptionSignalEvent event) {
        log.info("Publishing InterruptionSignalEvent. Org: {}, Manager: {}", event.orgId(), event.managerId());
        try {
            if (pubSubTemplate != null) {
                String payload = objectMapper.writeValueAsString(event);
                pubSubTemplate.publish(pubsubTopic, payload);
                log.info("Published to Pub/Sub topic: {}", pubsubTopic);
            } else if (sqsTemplate != null) {
                sqsTemplate.send(queueName, event);
                log.info("Published to SQS queue: {}", queueName);
            }
        } catch (Exception e) {
            log.error("Failed to publish InterruptionSignalEvent", e);
            throw new RuntimeException("Publish failed", e);
        }
    }
}
