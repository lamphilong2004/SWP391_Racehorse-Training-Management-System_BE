package com.racehorse.core026;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.core026.Contracts.*;
import com.racehorse.core026.IntegrationPorts.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AccessPolicy {
    private final ObjectProvider<ActorResolver> actors;
    private final ObjectProvider<GroomWorkScope> grooms;

    public AccessPolicy(ObjectProvider<ActorResolver> actors, ObjectProvider<GroomWorkScope> grooms) {
        this.actors = actors;
        this.grooms = grooms;
    }

    public Actor actor() {
        ActorResolver resolver = actors.getIfAvailable();
        Actor actor = resolver == null ? null : resolver.currentActor();
        if (actor == null)
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication integration is required.");
        return actor;
    }

    public void read(Actor a) {
        require(a.has(Role.CLUB_MANAGER) || a.has(Role.HEAD_TRAINER) || a.has(Role.VETERINARIAN) || a.has(Role.TRAINER)
                || a.has(Role.WORK_RIDER) || a.has(Role.GROOM));
    }

    public boolean allReports(Actor a) {
        return a.has(Role.CLUB_MANAGER) || a.has(Role.HEAD_TRAINER) || a.has(Role.VETERINARIAN);
    }

    public void create(Actor a) {
        require(a.has(Role.HEAD_TRAINER) || a.has(Role.TRAINER) || a.has(Role.WORK_RIDER) || a.has(Role.GROOM));
    }

    public GroomWorkScope groom(Actor a) {
        require(a.has(Role.GROOM));
        GroomWorkScope scope = grooms.getIfAvailable();
        require(scope != null);
        return scope;
    }

    static void require(boolean permitted) {
        if (!permitted)
            throw new BusinessException(ErrorCode.FORBIDDEN, "Access to this resource is denied.");
    }
}
