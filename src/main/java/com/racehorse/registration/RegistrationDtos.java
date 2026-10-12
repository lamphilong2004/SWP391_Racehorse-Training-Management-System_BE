package com.racehorse.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.racehorse.auth.EmailAddresses;
import com.racehorse.identity.AppUser;
import com.racehorse.identity.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Bodies of shared.openapi.yaml (CORE-003): RegisterRequest, NeutralMessage, RegistrationRequest, Approve/Reject. */
public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    /**
     * No role field (DEC-008 §2.1, R7): unknown properties such as role / roles / authority are ignored by Jackson
     * and never persisted. Passwords are write-only and excluded from toString (R2, R11).
     */
    public record RegisterRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
            @NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String confirmPassword) {

        @Override
        public String toString() {
            return "RegisterRequest{email=" + EmailAddresses.mask(email) + "}";
        }
    }

    public record NeutralMessage(String message) {
    }

    public record ApproveRequest(@NotNull RoleCode roleCode) {
    }

    public record RejectRequest(@Size(max = 500) String reason) {
    }

    public record UserRef(Long id, String userCode, String fullName) {
        static UserRef of(AppUser user) {
            return user == null ? null : new UserRef(user.getId(), user.getUserCode(), user.getFullName());
        }
    }

    /** Club Manager view. Never contains the hash or any "has password" indicator (R2). */
    public record RegistrationRequestView(Long id, String fullName, String email, RegistrationStatus status,
                                          Instant requestedAt, Instant reviewedAt, UserRef reviewedBy,
                                          RoleCode approvedRoleCode, Long createdUserId, String decisionReason) {

        static RegistrationRequestView of(RegistrationRequest r) {
            return new RegistrationRequestView(r.getId(), r.getFullName(), r.getEmail(), r.getStatus(), r.getRequestedAt(),
                    r.getReviewedAt(), UserRef.of(r.getReviewedBy()),
                    r.getApprovedRole() == null ? null : r.getApprovedRole().getRoleCode(),
                    r.getCreatedUserId(), r.getDecisionReason());
        }
    }
}
