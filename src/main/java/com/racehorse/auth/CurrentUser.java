package com.racehorse.auth;

import java.util.List;

public record CurrentUser(
        long id,
        String userCode,
        String fullName,
        String email,
        List<RoleCode> roles,
        AccountStatus accountStatus) {

    public CurrentUser {
        roles = List.copyOf(roles);
    }
}
