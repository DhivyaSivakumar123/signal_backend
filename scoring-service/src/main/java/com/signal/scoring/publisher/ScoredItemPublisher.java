package com.signal.scoring.publisher;

import com.signal.common.messaging.ScoredItemEvent;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ScoredItemPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScoredItemPublisher.class);

    private final SqsTemplate sqsTemplate;
    private final String queueName;

    public ScoredItemPublisher(SqsTemplate sqsTemplate,
                               @Value("${signal.queues.scored-items:signal-scored-items}") String queueName) {
        this.sqsTemplate = sqsTemplate;
        this.queueName = queueName;
    }

    public void publish(ScoredItemEvent event) {
        log.info("Publishing ScoredItemEvent to SQS: {}", event.scoredItemId());
        try {
            sqsTemplate.send(queueName, event);
        } catch (Exception e) {
            log.error("Failed to publish ScoredItemEvent to SQS", e);
        }
    }
}
