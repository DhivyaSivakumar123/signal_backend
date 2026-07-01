package com.signal.triage.entity;

import com.signal.common.dto.MessageSource;
import com.signal.common.dto.Urgency;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "triage_items", schema = "triage")
@EntityListeners(AuditingEntityListener.class)
public class TriageItem {

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

    @Column(nullable = false, length = 500)
    private String reasonSnippet;

    @Column(nullable = false)
    private String senderId;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private String status; // UNSEEN, SEEN, DISMISSED, FEEDBACK_NOT_URGENT, FEEDBACK_WAS_URGENT

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public TriageItem() {}

    public TriageItem(UUID userId, UUID threadGroupId, MessageSource source, Urgency urgency,
                      String reasonSnippet, String senderId, Instant receivedAt, String status) {
        this.userId = userId;
        this.threadGroupId = threadGroupId;
        this.source = source;
        this.urgency = urgency;
        this.reasonSnippet = reasonSnippet;
        this.senderId = senderId;
        this.receivedAt = receivedAt;
        this.status = status;
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
        this.reasonSnippet = reasonSnippet;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
