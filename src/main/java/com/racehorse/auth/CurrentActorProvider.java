package com.racehorse.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CurrentActorProvider {

    public Optional<ActorContext> currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return Optional.empty();
        }

        return Optional.of(new ActorContext(currentUser.id(), currentUser.roles()));
    }
}
