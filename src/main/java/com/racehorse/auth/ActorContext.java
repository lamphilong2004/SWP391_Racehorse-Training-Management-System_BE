package com.racehorse.auth;

import java.util.List;

public record ActorContext(long userId, List<RoleCode> roles) {

    public ActorContext {
        roles = List.copyOf(roles);
    }
}