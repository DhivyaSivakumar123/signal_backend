package com.signal.analytics.consumer;

import com.signal.common.messaging.InterruptionSignalEvent;
import com.signal.analytics.entity.InterruptionMetric;
import com.signal.analytics.repository.InterruptionMetricRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public InterruptionSignalConsumer(InterruptionMetricRepository repository) {
        this.repository = repository;
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
        }
    }
}
