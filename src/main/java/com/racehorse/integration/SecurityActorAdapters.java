package com.racehorse.integration;

import com.racehorse.auth.AuthenticatedUser;
import com.racehorse.core026.Contracts;
import com.racehorse.core026.IntegrationPorts;
import com.racehorse.core035.MedicalPorts;
import com.racehorse.identity.RoleCode;
import com.racehorse.medical.MedicalIdentityProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * DEMO INTEGRATION (demo/main-flow-hour1): binds the CORE-007 security principal to the identity ports that
 * CORE-014, CORE-026 and CORE-035 declared. Identity always comes from the verified JWT principal, never from
 * request data. Owners: CORE-007 (Long) with CORE-014/026/035 (Đức) — to be reviewed after the demo.
 */
@Component
public class SecurityActorAdapters implements MedicalIdentityProvider, IntegrationPorts.ActorResolver {

    public static AuthenticatedUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) return null;
        return user;
    }

    @Override
    public MedicalIdentityProvider.Identity currentIdentity() {
        AuthenticatedUser user = currentUser();
        if (user == null) return null;
        return new MedicalIdentityProvider.Identity(user.id(),
                user.roles().stream().map(RoleCode::name).collect(Collectors.toSet()));
    }

    @Override
    public Contracts.Actor currentActor() {
        AuthenticatedUser user = currentUser();
        if (user == null) return null;
        Set<Contracts.Role> roles = user.roles().stream().map(r -> Contracts.Role.valueOf(r.name()))
                .collect(Collectors.toSet());
        return new Contracts.Actor(user.id(), roles);
    }

    /** core035 declares its own Actor type; exposed through a separate bean to avoid a signature clash. */
    @Component
    public static class MedicalCaseActorResolver implements MedicalPorts.ActorResolver {
        @Override
        public MedicalPorts.Actor currentActor() {
            AuthenticatedUser user = currentUser();
            if (user == null) return null;
            return new MedicalPorts.Actor(user.id(), user.roles().stream()
                    .map(r -> MedicalPorts.Role.valueOf(r.name())).collect(Collectors.toSet()));
        }
    }
}
