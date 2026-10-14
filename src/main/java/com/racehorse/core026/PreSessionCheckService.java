package com.racehorse.core026;

import com.racehorse.core026.Contracts.*;
import com.racehorse.core026.IntegrationPorts.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;
import java.time.*;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import static com.racehorse.core026.IncidentService.*;

@Service
public class PreSessionCheckService {
    private final Core026Store store;
    private final AccessPolicy access;
    private final Validator validator;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<SessionProjection> projections;

    public PreSessionCheckService(Core026Store store, AccessPolicy access, Validator validator, ObjectMapper json,
            ApplicationEventPublisher events, ObjectProvider<SessionProjection> projections) {
        this.store = store;
        this.access = access;
        this.validator = validator;
        this.json = json;
        this.events = events;
        this.projections = projections;
    }

    private record Observations(CheckItems items, String notes) {
    }

    @Transactional
    public PreSessionCheck submit(long sessionId, PreSessionCheckRequest request) {
        Actor actor = access.actor();
        GroomWorkScope scope = access.groom(actor);
        if (sessionId < 1)
            throw invalid("Positive session ID required.");
        AccessPolicy.require(scope.mayCheck(actor, sessionId));
        validate(validator, request);
        CheckResult overall = request.items().derived();
        // BR-23 automatic reporting is unapproved. Reject nested incident instead of
        // silently dropping it.
        if (request.incident() != null)
            throw invalid("Automatic incident creation is not approved; use POST /api/incidents separately.");
        var session = store.session(sessionId, true).orElseThrow(() -> missing("Session not found."));
        if (!"SCHEDULED".equals(session.status()))
            throw conflict("Only SCHEDULED sessions accept a pre-session check.");
        if (store.checkResult(sessionId).isPresent())
            throw conflict("This session already has a pre-session check.");
        String observations = json.writeValueAsString(new Observations(request.items(), request.notes()));
        if (observations.length() > 4000)
            throw invalid("Serialized observations exceed 4000 characters.");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        UserRef performer = store.user(actor.id());
        long id;
        try {
            id = store.insertCheck(sessionId, actor.id(), overall, observations, now);
        } catch (DataIntegrityViolationException e) {
            throw conflict("Pre-session check uniqueness or relationship changed concurrently.");
        }
        events.publishEvent(new CheckRecorded(id, sessionId, overall));
        return new PreSessionCheck(id, sessionId, overall, observations, performer, now, null);
    }

    @Transactional(readOnly = true)
    public List<SessionSummary> sessions(LocalDate date) {
        Actor actor = access.actor();
        GroomWorkScope scope = access.groom(actor);
        if (date == null)
            throw invalid("Date is required.");
        SessionProjection projection = projections.getIfAvailable();
        AccessPolicy.require(projection != null);
        // Trusted scope is applied BEFORE records are fetched or returned.
        return scope.sessionIds(actor, date).stream().distinct().map(id -> {
            AccessPolicy.require(scope.mayCheck(actor, id));
            return store.session(id, false).filter(s -> "SCHEDULED".equals(s.status())).map(s -> projection.summary(id))
                    .orElse(null);
        }).filter(java.util.Objects::nonNull)
                .filter(s -> s.scheduledStart().atOffset(ZoneOffset.UTC).toLocalDate().equals(date)).toList();
    }
}
