package com.racehorse.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.Length;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * dbo.audit_log — append-only (no updated_at; the application never updates or deletes rows).
 * Never stores a password, hash, JWT, reset token or secret (schema.sql comment, MF-20, BR-45).
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Nationalized
    @Column(name = "safe_summary", length = Length.LONG32)
    private String safeSummary;

    @Column(name = "source", length = 30)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    AuditLog(Long actorUserId, String actorRole, String action, String targetType, Long targetId, String safeSummary, String source) {
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.safeSummary = safeSummary;
        this.source = source;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (occurredAt == null) occurredAt = now;
        if (createdAt == null) createdAt = now;
    }

    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getSafeSummary() { return safeSummary; }
    public String getSource() { return source; }
}
