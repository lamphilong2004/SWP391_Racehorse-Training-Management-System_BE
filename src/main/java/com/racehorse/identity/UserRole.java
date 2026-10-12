package com.racehorse.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** dbo.user_role — one row per (user, role); uq_user_role__user_id_role_id. */
@Entity
@Table(name = "user_role", uniqueConstraints = @UniqueConstraint(name = "uq_user_role__user_id_role_id", columnNames = {"user_id", "role_id"}))
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private AppUser assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected UserRole() {
    }

    UserRole(AppUser user, Role role, AppUser assignedBy) {
        this.user = user;
        this.role = role;
        this.assignedBy = assignedBy;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (assignedAt == null) assignedAt = now;
        if (createdAt == null) createdAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public Role getRole() { return role; }
    public AppUser getAssignedBy() { return assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
}
