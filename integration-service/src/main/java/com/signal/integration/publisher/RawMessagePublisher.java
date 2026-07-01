package com.signal.integration.publisher;

import com.signal.common.messaging.IncomingMessageEvent;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RawMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(RawMessagePublisher.class);

    private final SqsTemplate sqsTemplate;
    private final String queueName;

    public RawMessagePublisher(SqsTemplate sqsTemplate, 
                                @Value("${signal.queues.raw-messages:signal-raw-messages}") String queueName) {
        this.sqsTemplate = sqsTemplate;
        this.queueName = queueName;
    }

    public void publish(IncomingMessageEvent event) {
        log.info("Publishing IncomingMessageEvent to SQS: {}", event.messageId());
        try {
            sqsTemplate.send(queueName, event);
        } catch (Exception e) {
            log.error("Failed to publish IncomingMessageEvent to SQS", e);
        }
    }
}
