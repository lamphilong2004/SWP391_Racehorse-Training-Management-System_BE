package com.racehorse.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.racehorse.identity.AccountStatus;
import com.racehorse.identity.AppUser;
import com.racehorse.identity.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request/response bodies of shared.openapi.yaml (CORE-003): LoginRequest, LoginResponse, CurrentUser. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** The password is write-only and excluded from toString (DEC-008 §6 R2, R11). */
    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 64) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {

        @Override
        public String toString() {
            return "LoginRequest{email=" + EmailAddresses.mask(email) + "}";
        }
    }

    /** Never contains a password, hash or "has password" indicator. */
    public record CurrentUser(Long id, String userCode, String fullName, String email, List<RoleCode> roles,
                              AccountStatus accountStatus) {

        public static CurrentUser of(AppUser user) {
            return new CurrentUser(user.getId(), user.getUserCode(), user.getFullName(), user.getEmail(),
                    user.roleCodes(), user.getAccountStatus());
        }
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, CurrentUser user) {

        /** Never prints the token. */
        @Override
        public String toString() {
            return "LoginResponse{tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", user=" + user.id() + "}";
        }
    }
}
