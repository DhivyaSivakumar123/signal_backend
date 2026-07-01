package com.signal.integration.publisher;

import com.signal.common.messaging.InterruptionSignalEvent;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InterruptionSignalPublisher {

    private static final Logger log = LoggerFactory.getLogger(InterruptionSignalPublisher.class);

    private final SqsTemplate sqsTemplate;
    private final String queueName;

    public InterruptionSignalPublisher(SqsTemplate sqsTemplate,
                                        @Value("${signal.queues.interruption-signals:signal-interruption-signals}") String queueName) {
        this.sqsTemplate = sqsTemplate;
        this.queueName = queueName;
    }

    public void publish(InterruptionSignalEvent event) {
        log.info("Publishing InterruptionSignalEvent to SQS. Org: {}, Manager: {}", event.orgId(), event.managerId());
        try {
            sqsTemplate.send(queueName, event);
        } catch (Exception e) {
            log.error("Failed to publish InterruptionSignalEvent to SQS", e);
        }
    }
}
