package com.racehorse.identity;

/**
 * The 7 RBAC roles (DEC-004; schema.sql ck_role__role_code). There is no SYSTEM role:
 * automatic behaviour runs inside the backend and never authenticates.
 */
public enum RoleCode {
    CLUB_MANAGER,
    HEAD_TRAINER,
    TRAINER,
    WORK_RIDER,
    VETERINARIAN,
    GROOM,
    HORSE_OWNER;

    /** Spring Security authority, used by hasRole('…') / @PreAuthorize. */
    public String authority() {
        return "ROLE_" + name();
    }
}
