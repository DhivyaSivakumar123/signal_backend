package com.signal.scoring.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.signal.common.dto.Urgency;
import com.signal.common.dto.UserDTO;
import com.signal.common.messaging.IncomingMessageEvent;
import com.signal.common.messaging.ScoredItemEvent;
import com.signal.scoring.dedupe.ThreadGroupingService;
import com.signal.scoring.entity.ScoredItem;
import com.signal.scoring.entity.ThreadGroup;
import com.signal.scoring.llm.ScoringContext;
import com.signal.scoring.llm.UrgencyResult;
import com.signal.scoring.llm.UrgencyScorer;
import com.signal.scoring.publisher.ScoredItemPublisher;
import com.signal.scoring.repository.ScoredItemRepository;
import com.signal.scoring.rules.PreFilterRuleEngine;
import com.signal.scoring.service.AuthServiceClient;
import com.signal.scoring.service.TriageServiceClient;
import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class RawMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(RawMessageConsumer.class);

    private final ThreadGroupingService threadGroupingService;
    private final PreFilterRuleEngine preFilterRuleEngine;
    private final UrgencyScorer urgencyScorer;
    private final ScoredItemRepository scoredItemRepository;
    private final ScoredItemPublisher scoredItemPublisher;
    private final AuthServiceClient authServiceClient;
    private final TriageServiceClient triageServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${signal.pubsub.raw-messages-subscription:signal-scoring-consumer}")
    private String subscriptionName;

    @Autowired
    public RawMessageConsumer(ThreadGroupingService threadGroupingService,
                               PreFilterRuleEngine preFilterRuleEngine,
                               UrgencyScorer urgencyScorer,
                               ScoredItemRepository scoredItemRepository,
                               ScoredItemPublisher scoredItemPublisher,
                               AuthServiceClient authServiceClient,
                               TriageServiceClient triageServiceClient,
                               StringRedisTemplate redisTemplate,
                               @Autowired(required = false) PubSubTemplate pubSubTemplate,
                               ObjectMapper objectMapper) {
        this.threadGroupingService = threadGroupingService;
        this.preFilterRuleEngine = preFilterRuleEngine;
        this.urgencyScorer = urgencyScorer;
        this.scoredItemRepository = scoredItemRepository;
        this.scoredItemPublisher = scoredItemPublisher;
        this.authServiceClient = authServiceClient;
        this.triageServiceClient = triageServiceClient;
        this.redisTemplate = redisTemplate;
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initPubSubSubscriber() {
        if (pubSubTemplate != null) {
            log.info("Subscribing to Pub/Sub subscription: {}", subscriptionName);
            pubSubTemplate.subscribe(subscriptionName, message -> {
                try {
                    IncomingMessageEvent event = objectMapper.readValue(
                            message.getPubsubMessage().getData().toStringUtf8(),
                            IncomingMessageEvent.class
                    );
                    consume(event);
                    message.ack();
                } catch (Exception e) {
                    log.error("Failed to process Pub/Sub raw message event", e);
                    message.nack();
                }
            });
        }
    }

    @SqsListener("${signal.queues.raw-messages:signal-raw-messages}")
    public void consume(IncomingMessageEvent event) {
        log.info("Received Raw Message to score: ID={}, ThreadExternalID={}", event.messageId(), event.threadExternalId());

        // Idempotency: Check if already scored in Redis or DB
        String cacheKey = "scored:msg:" + event.messageId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            log.info("Message {} already scored (Redis cache hit). Skipping rescore.", event.messageId());
            return;
        }
        if (scoredItemRepository.findByExternalRef(event.messageId().toString()).isPresent()) {
            log.info("Message {} already exists in DB (externalRef hit). Skipping rescore.", event.messageId());
            // Write back to Redis to restore cache
            redisTemplate.opsForValue().set(cacheKey, event.messageId().toString(), Duration.ofDays(7));
            return;
        }

        try {
            // 1. Thread grouping
            ThreadGroup threadGroup = threadGroupingService.findOrCreateGroup(
                    event.userId(),
                    event.subjectOrPreview(),
                    event.threadExternalId(),
                    event.receivedAt()
            );

            // 2. Fetch user information
            UserDTO user = authServiceClient.getUser(event.userId());

            // Determine manager/skip-level relationship
            String relationship = "UNKNOWN";
            try {
                if (user.managerId() != null) {
                    UserDTO manager = authServiceClient.getUser(user.managerId());
                    if (manager != null) {
                        if (manager.email().equalsIgnoreCase(event.senderId())) {
                            relationship = "MANAGER";
                        } else if (manager.managerId() != null) {
                            UserDTO skipLevel = authServiceClient.getUser(manager.managerId());
                            if (skipLevel != null && skipLevel.email().equalsIgnoreCase(event.senderId())) {
                                relationship = "SKIP_LEVEL";
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to check manager/skip-level details: {}", e.getMessage());
            }

            // 3. Fetch user preference (statedPriorities)
            String priorities = triageServiceClient.getStatedPriorities(event.userId());

            // 4. Create Scoring Context
            ScoringContext context = new ScoringContext(
                    event.subjectOrPreview(), // content preview
                    event.senderName(),
                    event.senderId(),
                    relationship,
                    event.source().name() + " Message",
                    "", // Thread history placeholder
                    priorities
            );

            // 5. Run Layer 1 Pre-Filter Rules
            UrgencyResult result;
            Optional<UrgencyResult> ruleResult = preFilterRuleEngine.evaluate(context);
            if (ruleResult.isPresent()) {
                result = ruleResult.get();
                log.info("Layer 1 Pre-Filter resolved urgency: {} for message {}", result.urgency(), event.messageId());
            } else {
                // 6. Fallback to LLM Scorer
                result = urgencyScorer.score(context);
                log.info("LLM Scorer resolved urgency: {} for message {}", result.urgency(), event.messageId());
            }

            Urgency finalUrgency = result.urgency();

            // 7. Post-Scoring Adjustments

            // Rule 3: If sender is manager/skip-level AND direct mention/DM, floor is TODAY
            if ("MANAGER".equals(relationship) || "SKIP_LEVEL".equals(relationship)) {
                log.info("Applying Rule 3 Manager/Skip-Level Floor (TODAY) adjustment");
                finalUrgency = enforceFloor(finalUrgency, Urgency.TODAY);
            }

            // Rule 5: 2nd+ message in thread within 30 minutes -> bump urgency floor
            Instant since = Instant.now().minus(30, ChronoUnit.MINUTES);
            long count = scoredItemRepository.countByThreadGroupIdAndScoredAtAfter(threadGroup.getId(), since);
            if (count > 0) {
                log.info("Applying Rule 5 Escalation bump (+1) because count is: {}", count);
                finalUrgency = bumpUrgency(finalUrgency);
            }

            // Rule 6: Adjust by User's SenderUrgencyBias feedback delta
            int biasDelta = triageServiceClient.getSenderBias(event.userId(), event.senderId());
            if (biasDelta != 0) {
                log.info("Applying Rule 6 Urgency Bias adjustment: delta={}", biasDelta);
                finalUrgency = adjustUrgency(finalUrgency, biasDelta);
            }

            // 8. Persist ScoredItem
            ScoredItem scoredItem = new ScoredItem(
                    event.userId(),
                    threadGroup.getId(),
                    event.source(),
                    finalUrgency,
                    result.reason(),
                    event.messageId().toString(),
                    event.receivedAt()
            );
            scoredItem = scoredItemRepository.save(scoredItem);

            // 9. Cache in Redis to avoid reprocessing
            redisTemplate.opsForValue().set(cacheKey, scoredItem.getId().toString(), Duration.ofDays(7));

            // 10. Publish event to signal-scored-items
            ScoredItemEvent scoredItemEvent = new ScoredItemEvent(
                    scoredItem.getId(),
                    event.userId(),
                    threadGroup.getId(),
                    finalUrgency,
                    scoredItem.getReasonSnippet(),
                    event.source(),
                    event.senderId(),
                    event.receivedAt()
            );
            scoredItemPublisher.publish(scoredItemEvent);

        } catch (Exception e) {
            log.error("Failed to process and score raw message: {}", event.messageId(), e);
            throw new RuntimeException("Scoring failed for message: " + event.messageId(), e);
        }
    }

    private Urgency adjustUrgency(Urgency original, int delta) {
        int score = getScoreValue(original);
        score = Math.max(0, Math.min(3, score + delta));
        return getUrgencyFromScore(score);
    }

    private Urgency bumpUrgency(Urgency original) {
        return adjustUrgency(original, 1);
    }

    private Urgency enforceFloor(Urgency original, Urgency floor) {
        if (getScoreValue(original) < getScoreValue(floor)) {
            return floor;
        }
        return original;
    }

    private int getScoreValue(Urgency u) {
        return switch (u) {
            case FYI -> 0;
            case THIS_WEEK -> 1;
            case TODAY -> 2;
            case NOW -> 3;
        };
    }

    private Urgency getUrgencyFromScore(int score) {
        return switch (score) {
            case 0 -> Urgency.FYI;
            case 1 -> Urgency.THIS_WEEK;
            case 2 -> Urgency.TODAY;
            case 3 -> Urgency.NOW;
            default -> Urgency.THIS_WEEK;
        };
    }
}
