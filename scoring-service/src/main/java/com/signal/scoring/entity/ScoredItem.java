package com.signal.scoring.entity;

import com.signal.common.dto.MessageSource;
import com.signal.common.dto.Urgency;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scored_items", schema = "scoring")
@EntityListeners(AuditingEntityListener.class)
public class ScoredItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID threadGroupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Urgency urgency;

    @Column(nullable = false, length = 200)
    private String reasonSnippet;

    @Column(nullable = false)
    private String externalRef;

    @Column(nullable = false)
    private Instant receivedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant scoredAt;

    public ScoredItem() {}

    public ScoredItem(UUID userId, UUID threadGroupId, MessageSource source, Urgency urgency,
                      String reasonSnippet, String externalRef, Instant receivedAt) {
        this.userId = userId;
        this.threadGroupId = threadGroupId;
        this.source = source;
        this.urgency = urgency;
        this.reasonSnippet = reasonSnippet != null && reasonSnippet.length() > 200
                ? reasonSnippet.substring(0, 197) + "..."
                : reasonSnippet;
        this.externalRef = externalRef;
        this.receivedAt = receivedAt;
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

    public UUID getThreadGroupId() {
        return threadGroupId;
    }

    public void setThreadGroupId(UUID threadGroupId) {
        this.threadGroupId = threadGroupId;
    }

    public MessageSource getSource() {
        return source;
    }

    public void setSource(MessageSource source) {
        this.source = source;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public void setUrgency(Urgency urgency) {
        this.urgency = urgency;
    }

    public String getReasonSnippet() {
        return reasonSnippet;
    }

    public void setReasonSnippet(String reasonSnippet) {
        this.reasonSnippet = reasonSnippet != null && reasonSnippet.length() > 200
                ? reasonSnippet.substring(0, 197) + "..."
                : reasonSnippet;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getScoredAt() {
        return scoredAt;
    }

    public void setScoredAt(Instant scoredAt) {
        this.scoredAt = scoredAt;
    }
}
