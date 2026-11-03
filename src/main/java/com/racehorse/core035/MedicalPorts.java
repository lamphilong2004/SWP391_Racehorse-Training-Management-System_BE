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
    /** CORE-045 implements this after its approved assignment migration is integrated. */
    public interface TreatmentTaskMaterializer {
        void reconcile(long treatmentPlanId, long horseId, List<TreatmentTaskSpec> requestedTasks);
    }
    public record TreatmentTaskSpec(String instruction, java.time.LocalDate taskDate,
            String scheduledSlot, long assignedGroomUserId) {}
}
