package com.racehorse.registration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.racehorse.auth.RoleCode;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class RegistrationDtos {
    private RegistrationDtos() { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Register {
        @NotBlank @Size(max=150) public String fullName;
        @NotBlank @Email @Size(max=254) public String email;
        @JsonProperty("email") public void setEmail(String value) {
            email=value==null?null:java.text.Normalizer.normalize(value.trim(),java.text.Normalizer.Form.NFC).toLowerCase(java.util.Locale.ROOT);
        }
        @NotNull @Size(min=8,max=64) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) public String password;
        @NotNull @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) public String confirmPassword;
        @Override public String toString() { return "Register[redacted]"; }
    }
    public record Approve(@NotNull RoleCode roleCode) { }
    public record Reject(@Size(max=500) String reason) { }
    public enum Status { PENDING_APPROVAL, APPROVED, REJECTED }
    public record UserRef(long id, String userCode, String fullName) { }
    public record Request(long id, String fullName, String email, Status status, Instant requestedAt,
                          Instant reviewedAt, UserRef reviewedBy, RoleCode approvedRoleCode,
                          Long createdUserId, String decisionReason) { }
    public record Page<T>(int page, int size, long totalItems, long totalPages, List<T> items) { }
    public record NeutralMessage(String message) { }
}
