package com.signal.integration.service;

import com.signal.common.dto.MessageSource;
import com.signal.common.dto.UserDTO;
import com.signal.common.messaging.IncomingMessageEvent;
import com.signal.common.messaging.InterruptionSignalEvent;
import com.signal.integration.publisher.InterruptionSignalPublisher;
import com.signal.integration.publisher.RawMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PollerCoordinatorService {

    private static final Logger log = LoggerFactory.getLogger(PollerCoordinatorService.class);

    private final AuthServiceClient authServiceClient;
    private final RawMessagePublisher rawMessagePublisher;
    private final InterruptionSignalPublisher interruptionSignalPublisher;
    private final StringRedisTemplate redisTemplate;

    public PollerCoordinatorService(AuthServiceClient authServiceClient,
                                    RawMessagePublisher rawMessagePublisher,
                                    InterruptionSignalPublisher interruptionSignalPublisher,
                                    StringRedisTemplate redisTemplate) {
        this.authServiceClient = authServiceClient;
        this.rawMessagePublisher = rawMessagePublisher;
        this.interruptionSignalPublisher = interruptionSignalPublisher;
        this.redisTemplate = redisTemplate;
    }

    public void processIncomingMessage(UUID userId, MessageSource source, String externalId,
                                       String senderId, String senderName, String subject, String content) {
        
        String redisKey = "processed:msg:" + source + ":" + externalId;
        Boolean exists = redisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(exists)) {
            log.debug("Skipping duplicate message {}: {}", source, externalId);
            return;
        }

        UserDTO user = authServiceClient.getUser(userId);

        UUID orgId = user.orgId() != null ? user.orgId() : UUID.randomUUID();
        UUID managerId = user.managerId() != null ? user.managerId() : user.id();
        String memberHash = hashUserId(userId.toString());

        Instant now = Instant.now();

        // 1. Publish Raw Message
        IncomingMessageEvent msgEvent = new IncomingMessageEvent(
                UUID.randomUUID(),
                userId,
                source,
                senderId,
                senderName,
                subject,
                "ref://content/" + source + "/" + externalId,
                externalId,
                now
        );
        rawMessagePublisher.publish(msgEvent);

        // 2. Publish Interruption Signal
        InterruptionSignalEvent signalEvent = new InterruptionSignalEvent(
                orgId,
                managerId,
                memberHash,
                source,
                now
        );
        interruptionSignalPublisher.publish(signalEvent);

        // 3. Mark processed in Redis with 1 day expiry
        redisTemplate.opsForValue().set(redisKey, "processed", Duration.ofDays(1));
    }

    private String hashUserId(String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Failed to compute hash for userId", e);
            return userId;
        }
    }
}
