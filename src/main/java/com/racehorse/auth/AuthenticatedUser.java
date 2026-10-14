package com.racehorse.auth;

import com.racehorse.identity.RoleCode;

import java.util.List;

/**
 * Principal of an authenticated request. Built from the database on every request (not from token claims),
 * so deactivation and role changes take effect immediately. Feature code reads it with
 * {@code @AuthenticationPrincipal AuthenticatedUser user} for row/assignment scoping.
 */
public record AuthenticatedUser(Long id, String userCode, List<RoleCode> roles) {

    public AuthenticatedUser {
        roles = List.copyOf(roles);
    }

    public boolean hasRole(RoleCode role) {
        return roles.contains(role);
    }

    /** The role recorded as audit_log.actor_role. Users normally hold exactly one role (DEC-008 §2.1, MF-17 form). */
    public RoleCode primaryRole() {
        return roles.isEmpty() ? null : roles.get(0);
    }
}
