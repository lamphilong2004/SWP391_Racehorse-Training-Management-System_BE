package com.racehorse.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CurrentActorProvider {

    public Optional<ActorContext> currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Jwt jwt = jwtAuthentication.getToken();
        long userId = Long.parseLong(jwt.getSubject());
        List<RoleCode> roles = jwt.getClaimAsStringList("roles").stream()
                .map(RoleCode::valueOf)
                .toList();
        return Optional.of(new ActorContext(userId, roles));
    }
}
