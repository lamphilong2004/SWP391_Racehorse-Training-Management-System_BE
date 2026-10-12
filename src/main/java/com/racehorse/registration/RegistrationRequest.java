package com.racehorse.registration;

import com.racehorse.identity.AppUser;
import com.racehorse.identity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * Technical table registration_request (database/technical/registration_request.sql, DEC-008 §2.3) — an access
 * request, never an account. {@code passwordHash} exists only while PENDING_APPROVAL (R12); it is never returned,
 * logged or printed. Decisions are applied with conditional updates in {@link RegistrationRequestRepository}.
 */
@Entity
@Table(name = "registration_request")
public class RegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Nationalized
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Nationalized
    @Column(name = "email_normalized", nullable = false, length = 254)
    private String emailNormalized;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RegistrationStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private AppUser reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Nationalized
    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_role_id")
    private Role approvedRole;

    @Column(name = "created_user_id")
    private Long createdUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected RegistrationRequest() {
    }

    RegistrationRequest(String fullName, String email, String emailNormalized, String passwordHash, Instant requestedAt) {
        this.fullName = fullName;
        this.email = email;
        this.emailNormalized = emailNormalized;
        this.passwordHash = passwordHash;
        this.status = RegistrationStatus.PENDING_APPROVAL;
        this.requestedAt = requestedAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getEmailNormalized() { return emailNormalized; }
    String getPasswordHash() { return passwordHash; }
    public RegistrationStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public AppUser getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getDecisionReason() { return decisionReason; }
    public Role getApprovedRole() { return approvedRole; }
    public Long getCreatedUserId() { return createdUserId; }

    /** Never includes the hash or the email. */
    @Override
    public String toString() {
        return "RegistrationRequest{id=" + id + ", status=" + status + "}";
    }
}
