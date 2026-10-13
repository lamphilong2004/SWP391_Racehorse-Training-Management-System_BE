package com.racehorse.core026;

import com.racehorse.core026.Contracts.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Trusted server-side adapters only. No adapter means denied access, never a
 * client header fallback.
 */
public final class IntegrationPorts {
    private IntegrationPorts() {
    }

    /**
     * CORE-007 must validate the real authentication and active account before
     * returning an actor.
     */
    public interface ActorResolver {
        Actor currentActor();
    }

    /**
     * Approved work-list source is missing in DB-01. Implement only after its scope
     * is approved.
     */
    public interface GroomWorkScope {
        boolean mayCheck(Actor actor, long sessionId);

        boolean mayReportCareTask(Actor actor, long taskId);

        List<Long> sessionIds(Actor actor, LocalDate date);
    }

    /**
     * CORE-024 supplies complete blocked state (medical + exercises + program +
     * other start rules).
     */
    public interface SessionProjection {
        SessionSummary summary(long sessionId);
    }

    /**
     * Integration event, NOT a notification-delivery receipt. Future consumers must
     * run AFTER_COMMIT.
     */
    public record IncidentReported(long incidentId, long horseId, long reporterId) {
    }

    public record CheckRecorded(long checkId, long sessionId, CheckResult result) {
    }
}
