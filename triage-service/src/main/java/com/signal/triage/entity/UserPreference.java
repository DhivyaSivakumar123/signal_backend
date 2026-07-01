package com.signal.triage.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "user_preferences", schema = "triage")
public class UserPreference {

    @Id
    private UUID userId;

    @Column(columnDefinition = "TEXT")
    private String statedPriorities;

    @Column
    private LocalTime digestTime;

    @Column
    private String digestChannel; // EMAIL, SLACK, NONE

    @Column
    private LocalDate lastDigestSentAt; // Tracks last sent date to avoid duplicates

    public UserPreference() {}

    public UserPreference(UUID userId, String statedPriorities, LocalTime digestTime, String digestChannel) {
        this.userId = userId;
        this.statedPriorities = statedPriorities;
        this.digestTime = digestTime;
        this.digestChannel = digestChannel;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getStatedPriorities() {
        return statedPriorities;
    }

    public void setStatedPriorities(String statedPriorities) {
        this.statedPriorities = statedPriorities;
    }

    public LocalTime getDigestTime() {
        return digestTime;
    }

    public void setDigestTime(LocalTime digestTime) {
        this.digestTime = digestTime;
    }

    public String getDigestChannel() {
        return digestChannel;
    }

    public void setDigestChannel(String digestChannel) {
        this.digestChannel = digestChannel;
    }

    public LocalDate getLastDigestSentAt() {
        return lastDigestSentAt;
    }

    public void setLastDigestSentAt(LocalDate lastDigestSentAt) {
        this.lastDigestSentAt = lastDigestSentAt;
    }
}
