package com.signal.triage.consumer;

import com.signal.common.messaging.ScoredItemEvent;
import com.signal.triage.entity.TriageItem;
import com.signal.triage.repository.TriageItemRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ScoredItemConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScoredItemConsumer.class);

    private final TriageItemRepository triageItemRepository;

    public ScoredItemConsumer(TriageItemRepository triageItemRepository) {
        this.triageItemRepository = triageItemRepository;
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
        }
    }
}
