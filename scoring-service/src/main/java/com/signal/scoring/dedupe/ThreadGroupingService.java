package com.signal.scoring.dedupe;

import com.signal.scoring.entity.ThreadGroup;
import com.signal.scoring.repository.ThreadGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class ThreadGroupingService {

    private static final Logger log = LoggerFactory.getLogger(ThreadGroupingService.class);

    private final ThreadGroupRepository threadGroupRepository;

    public ThreadGroupingService(ThreadGroupRepository threadGroupRepository) {
        this.threadGroupRepository = threadGroupRepository;
    }

    @Transactional
    public ThreadGroup findOrCreateGroup(UUID userId, String subjectOrPreview, String threadExternalId, Instant receivedAt) {
        // 1. Try to match by threadExternalId if provided
        if (threadExternalId != null && !threadExternalId.isBlank() && !"none".equalsIgnoreCase(threadExternalId)) {
            Optional<ThreadGroup> existingById = threadGroupRepository.findByUserIdAndThreadExternalId(userId, threadExternalId);
            if (existingById.isPresent()) {
                ThreadGroup group = existingById.get();
                log.info("Found existing ThreadGroup by threadExternalId: {}", group.getId());
                group.setLastSeenAt(receivedAt);
                return threadGroupRepository.save(group);
            }
        }

        // 2. Try to match by normalized subject topicHash within 30 mins (cross-app deduplication)
        String normalizedSubject = normalizeSubject(subjectOrPreview);
        String topicHash = hashString(normalizedSubject);

        Optional<ThreadGroup> existingByHash = threadGroupRepository.findByUserIdAndTopicHash(userId, topicHash);
        
        if (existingByHash.isPresent()) {
            ThreadGroup group = existingByHash.get();
            if (group.getLastSeenAt().plus(30, ChronoUnit.MINUTES).isAfter(receivedAt)) {
                log.info("Found existing ThreadGroup by topicHash within 30m window: {}", group.getId());
                group.setLastSeenAt(receivedAt);
                if (group.getThreadExternalId() == null && threadExternalId != null && !threadExternalId.isBlank()) {
                    group.setThreadExternalId(threadExternalId);
                }
                return threadGroupRepository.save(group);
            }
        }

        // 3. Otherwise, create a new ThreadGroup
        log.info("Creating new ThreadGroup for subject: '{}' and external thread ID: '{}'", normalizedSubject, threadExternalId);
        ThreadGroup newGroup = new ThreadGroup(userId, topicHash, threadExternalId, receivedAt, receivedAt);
        return threadGroupRepository.save(newGroup);
    }

    private String normalizeSubject(String subject) {
        if (subject == null) {
            return "";
        }
        return subject.toLowerCase()
                .replaceAll("(?i)^(\\s*(re|fw|fwd)\\s*:\\s*)+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Failed to hash topic string, using fallback hash", e);
            return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8)).toString();
        }
    }
}
