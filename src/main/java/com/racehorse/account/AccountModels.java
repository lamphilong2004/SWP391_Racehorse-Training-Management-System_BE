package com.racehorse.account;

import com.racehorse.auth.AccountStatus;
import com.racehorse.auth.RoleCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

/** CORE-049 contract DTOs. Secret-bearing requests redact toString; these records confer no authorization. */
public final class AccountModels {
    private AccountModels() {}
    public record Profile(long id, String userCode, String fullName, String email, List<RoleCode> roles,
                          AccountStatus accountStatus, Instant lastLoginAt) {}
    public record UserDetail(long id, String userCode, String fullName, String email, List<RoleCode> roles,
                             AccountStatus accountStatus, Instant lastLoginAt, Integer experienceYears,
                             String experienceLevel, List<Skill> skills, Instant createdAt, Instant updatedAt) {}
    public record Skill(long id, String name, SkillKind kind) {}
    public enum SkillKind { TRAINER_SPECIALIZATION, RIDER_SKILL }
    public record RoleInfo(RoleCode roleCode, String displayName, String scoping) {}
    public record UpdateProfile(@NotBlank @Size(max=150) String fullName) {}
    /**
     * DEC-012 (2026-10-28): the Club Manager may set the initial password on create, or a new one on edit.
     * Omitted password keeps the DEC-008 reset-link path; the value is write-only and never logged.
     */
    public record UserInput(@NotBlank @Size(max=150) String fullName,
                            @NotBlank @Email @Size(max=254) String email,
                            @NotNull RoleCode roleCode, @Min(0) @Max(32767) Integer experienceYears,
                            @Size(max=50) String experienceLevel, List<@NotNull @Positive Long> skillIds,
                            @Size(max=64) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String password,
                            @Size(max=64) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String confirmPassword) {
        public UserInput(String fullName, String email, RoleCode roleCode, Integer experienceYears,
                         String experienceLevel, List<Long> skillIds) {
            this(fullName, email, roleCode, experienceYears, experienceLevel, skillIds, null, null);
        }
        @Override public String toString() { return "UserInput[fullName=" + fullName + ", roleCode=" + roleCode + ", password=REDACTED]"; }
    }
    public record StatusInput(@NotNull AccountStatus accountStatus) {}
    public record PasswordChange(@NotNull @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String currentPassword,
                                 @NotNull @Size(min=8,max=64) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String newPassword,
                                 @NotNull @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String confirmPassword) {
        @Override public String toString() { return "PasswordChange[REDACTED]"; }
    }
    public record Forgot(@NotBlank @Email @Size(max=254) String email) {
        @Override public String toString() { return "Forgot[REDACTED]"; }
    }
    public record Reset(@NotBlank @Size(max=1024) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String token,
                        @NotNull @Size(min=8,max=64) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String newPassword,
                        @NotNull @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String confirmPassword) {
        @Override public String toString() { return "Reset[REDACTED]"; }
    }
    public record NeutralMessage(String message) {}
}
