package com.signal.integration.entity;

import com.signal.common.dto.MessageSource;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "integrations", schema = "integration")
@EntityListeners(AuditingEntityListener.class)
public class Integration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageSource provider;

    @Column(nullable = false)
    private String status; // ACTIVE, EXPIRED, REVOKED

    @Column(nullable = false)
    private String secretRef; // Secrets Manager ARN or simulated placeholder

    @Column
    private Instant lastSyncedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Integration() {}

    public Integration(UUID userId, MessageSource provider, String status, String secretRef) {
        this.userId = userId;
        this.provider = provider;
        this.status = status;
        this.secretRef = secretRef;
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

    public MessageSource getProvider() {
        return provider;
    }

    public void setProvider(MessageSource provider) {
        this.provider = provider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public void setSecretRef(String secretRef) {
        this.secretRef = secretRef;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
