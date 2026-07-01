package com.signal.triage.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "sender_urgency_biases", schema = "triage", 
       uniqueConstraints = {@UniqueConstraint(columnNames = {"userId", "senderId"})})
public class SenderUrgencyBias {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String senderId;

    @Column(nullable = false)
    private int biasDelta;

    public SenderUrgencyBias() {}

    public SenderUrgencyBias(UUID userId, String senderId, int biasDelta) {
        this.userId = userId;
        this.senderId = senderId;
        this.biasDelta = biasDelta;
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

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public int getBiasDelta() {
        return biasDelta;
    }

    public void setBiasDelta(int biasDelta) {
        this.biasDelta = biasDelta;
    }
}
