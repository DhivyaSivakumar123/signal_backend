package com.signal.analytics.entity;

import com.signal.common.dto.MessageSource;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "interruption_metrics", schema = "analytics")
public class InterruptionMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private UUID managerId;

    @Column(nullable = false)
    private String teamMemberHash; // One-way SHA-256 hash

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageSource source;

    @Column(nullable = false)
    private int hourBucket; // 0-23

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int count;

    public InterruptionMetric() {}

    public InterruptionMetric(UUID orgId, UUID managerId, String teamMemberHash,
                              MessageSource source, int hourBucket, LocalDate date, int count) {
        this.orgId = orgId;
        this.managerId = managerId;
        this.teamMemberHash = teamMemberHash;
        this.source = source;
        this.hourBucket = hourBucket;
        this.date = date;
        this.count = count;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public void setManagerId(UUID managerId) {
        this.managerId = managerId;
    }

    public String getTeamMemberHash() {
        return teamMemberHash;
    }

    public void setTeamMemberHash(String teamMemberHash) {
        this.teamMemberHash = teamMemberHash;
    }

    public MessageSource getSource() {
        return source;
    }

    public void setSource(MessageSource source) {
        this.source = source;
    }

    public int getHourBucket() {
        return hourBucket;
    }

    public void setHourBucket(int hourBucket) {
        this.hourBucket = hourBucket;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
