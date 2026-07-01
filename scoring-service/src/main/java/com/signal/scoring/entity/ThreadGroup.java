package com.signal.scoring.entity;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "thread_groups", schema = "scoring")
@EntityListeners(AuditingEntityListener.class)
public class ThreadGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String topicHash;

    @Column
    private String threadExternalId; // Nullable to match cross-app subject duplicates

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    public ThreadGroup() {}

    public ThreadGroup(UUID userId, String topicHash, String threadExternalId, Instant firstSeenAt, Instant lastSeenAt) {
        this.userId = userId;
        this.topicHash = topicHash;
        this.threadExternalId = threadExternalId;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTopicHash() {
        return topicHash;
    }

    public void setTopicHash(String topicHash) {
        this.topicHash = topicHash;
    }

    public String getThreadExternalId() {
        return threadExternalId;
    }

    public void setThreadExternalId(String threadExternalId) {
        this.threadExternalId = threadExternalId;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
