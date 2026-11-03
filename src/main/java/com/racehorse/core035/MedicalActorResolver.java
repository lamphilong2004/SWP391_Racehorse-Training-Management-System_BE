package com.racehorse.core035;

import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.core035.MedicalPorts.Actor;
import com.racehorse.core035.MedicalPorts.ActorResolver;
import com.racehorse.core035.MedicalPorts.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/** Bridges the active-account/JWT actor established by CORE-007 into the medical service. */
@Component
final class MedicalActorResolver implements ActorResolver {
    private final CurrentActorProvider currentActors;

    MedicalActorResolver(CurrentActorProvider currentActors) {
        this.currentActors = currentActors;
    }

    @Override
    public Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return currentActors.currentActor()
                .map(context -> new Actor(context.userId(), context.roles().stream()
                        .map(role -> Role.valueOf(role.name()))
                        .collect(Collectors.toUnmodifiableSet())))
                .orElse(null);
    }
}
