package com.racehorse.core035;

import java.util.*;

/** Trusted server adapters supplied by the owners of authentication, work scope and Incident. */
public final class MedicalPorts {
    private MedicalPorts() {}
    public enum Role { CLUB_MANAGER, HEAD_TRAINER, TRAINER, WORK_RIDER, VETERINARIAN, GROOM, HORSE_OWNER }
    public record Actor(long id, Set<Role> roles) {
        public Actor {
            if (id < 1) throw new IllegalArgumentException("Actor ID must be positive");
            roles = Set.copyOf(roles);
        }
        public boolean has(Role role) { return roles.contains(role); }
    }
    /** CORE-007 must verify JWT, active account and current grants before resolving an actor. */
    public interface ActorResolver { Actor currentActor(); }
    /** CORE-026 implementation must lock, validate same horse and link in the caller's transaction. */
    public interface IncidentLink {
        void link(long incidentId, long horseId, long caseId, Actor actor);
    }
    /** An approved assignment source must supply only task IDs assigned to this Groom. */
    public interface GroomTaskScope { Set<Long> assignedTaskIds(Actor actor); }
}
