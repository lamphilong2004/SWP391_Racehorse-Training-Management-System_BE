package com.racehorse.auth;

import com.racehorse.identity.AccountStatus;
import com.racehorse.identity.AppUser;
import com.racehorse.identity.AppUserRepository;
import com.racehorse.identity.Role;
import com.racehorse.identity.RoleCode;
import com.racehorse.identity.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.atomic.AtomicInteger;

/** Creates users through the real repositories (H2, test profile). Test credentials only. */
final class AuthTestSupport {

    static final String PASSWORD = "Racehorse2026";

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    AuthTestSupport(AppUserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
    }

    Role role(RoleCode code) {
        return roles.findByRoleCode(code).orElseGet(() -> roles.save(new Role(code, code.name())));
    }

    AppUser user(String email, RoleCode code, AccountStatus status, boolean withPassword) {
        int n = SEQ.incrementAndGet();
        AppUser user = new AppUser("T-" + n, "Test User " + n, email, withPassword ? encoder.encode(PASSWORD) : null, status);
        user.addRole(role(code), null);
        return users.save(user);
    }
}
