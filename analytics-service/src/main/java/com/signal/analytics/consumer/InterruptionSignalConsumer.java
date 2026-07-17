package com.signal.analytics.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.signal.common.messaging.InterruptionSignalEvent;
import com.signal.analytics.entity.InterruptionMetric;
import com.signal.analytics.repository.InterruptionMetricRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

@Component
public class InterruptionSignalConsumer {

    private static final Logger log = LoggerFactory.getLogger(InterruptionSignalConsumer.class);

    private final InterruptionMetricRepository repository;
    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${signal.pubsub.interruption-signals-subscription:signal-analytics-consumer}")
    private String subscriptionName;

    @Autowired
    public InterruptionSignalConsumer(InterruptionMetricRepository repository,
                                       @Autowired(required = false) PubSubTemplate pubSubTemplate,
                                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initPubSubSubscriber() {
        if (pubSubTemplate != null) {
            log.info("Subscribing to Pub/Sub subscription: {}", subscriptionName);
            pubSubTemplate.subscribe(subscriptionName, message -> {
                try {
                    InterruptionSignalEvent event = objectMapper.readValue(
                            message.getPubsubMessage().getData().toStringUtf8(),
                            InterruptionSignalEvent.class
                    );
                    consume(event);
                    message.ack();
                } catch (Exception e) {
                    log.error("Failed to process Pub/Sub interruption signal event", e);
                    message.nack();
                }
            });
        }
    }

    @SqsListener("${signal.queues.interruption-signals:signal-interruption-signals}")
    @Transactional
    public void consume(InterruptionSignalEvent event) {
        log.info("Received Interruption Signal: Source={}, Org={}, Manager={}",
                event.source(), event.orgId(), event.managerId());

        try {
            ZonedDateTime zdt = event.occurredAt().atZone(ZoneId.systemDefault());
            LocalDate date = zdt.toLocalDate();
            int hour = zdt.getHour();

            Optional<InterruptionMetric> metricOpt = repository
                    .findByOrgIdAndManagerIdAndTeamMemberHashAndSourceAndHourBucketAndDate(
                            event.orgId(),
                            event.managerId(),
                            event.teamMemberHash(),
                            event.source(),
                            hour,
                            date
                    );

            InterruptionMetric metric;
            if (metricOpt.isPresent()) {
                metric = metricOpt.get();
                metric.setCount(metric.getCount() + 1);
                log.info("Incrementing existing InterruptionMetric ID: {}", metric.getId());
            } else {
                metric = new InterruptionMetric(
                        event.orgId(),
                        event.managerId(),
                        event.teamMemberHash(),
                        event.source(),
                        hour,
                        date,
                        1
                );
                log.info("Creating new InterruptionMetric");
            }

            repository.save(metric);

        } catch (Exception e) {
            log.error("Failed to process interruption signal", e);
            throw new RuntimeException("Failed to process interruption signal", e);
        }
    }
}
