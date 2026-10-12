package com.racehorse.identity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * dbo.app_user. {@code passwordHash} is a BCrypt hash, never returned by any API and never logged
 * (DEC-008 §6 R2/R6); it is NULL for users created through MF-17 until they set a password.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_code", nullable = false, length = 20, unique = true)
    private String userCode;

    @Nationalized
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Nationalized
    @Column(name = "email", nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "experience_years")
    private Short experienceYears;

    @Nationalized
    @Column(name = "experience_level", length = 50)
    private String experienceLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.PERSIST)
    private List<UserRole> userRoles = new ArrayList<>();

    protected AppUser() {
    }

    public AppUser(String userCode, String fullName, String email, String passwordHash, AccountStatus accountStatus) {
        this.userCode = userCode;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.accountStatus = accountStatus;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UserRole addRole(Role role, AppUser assignedBy) {
        UserRole userRole = new UserRole(this, role, assignedBy);
        userRoles.add(userRole);
        return userRole;
    }

    public List<RoleCode> roleCodes() {
        return userRoles.stream().map(ur -> ur.getRole().getRoleCode()).sorted().toList();
    }

    /** DEC-008 §6 R5: only an ACTIVE user with a password hash may receive or keep using a JWT. */
    public boolean canAuthenticate() {
        return accountStatus == AccountStatus.ACTIVE && passwordHash != null;
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    public Long getId() { return id; }
    public String getUserCode() { return userCode; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public AccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(AccountStatus accountStatus) { this.accountStatus = accountStatus; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Short getExperienceYears() { return experienceYears; }
    public String getExperienceLevel() { return experienceLevel; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<UserRole> getUserRoles() { return Collections.unmodifiableList(userRoles); }

    /** Never includes the password hash. */
    @Override
    public String toString() {
        return "AppUser{id=" + id + ", userCode=" + userCode + ", accountStatus=" + accountStatus + "}";
    }
}
