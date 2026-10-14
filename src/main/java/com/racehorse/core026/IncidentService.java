package com.racehorse.core026;

import com.racehorse.common.error.*;
import com.racehorse.core026.Contracts.*;
import com.racehorse.core026.IntegrationPorts.IncidentReported;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.UUID;

@Service
public class IncidentService {
    private final Core026Store store;
    private final AccessPolicy access;
    private final Validator validator;
    private final ApplicationEventPublisher events;

    public IncidentService(Core026Store store, AccessPolicy access, Validator validator,
            ApplicationEventPublisher events) {
        this.store = store;
        this.access = access;
        this.validator = validator;
        this.events = events;
    }

    @Transactional
    public Incident create(IncidentCreateRequest request) {
        Actor actor = access.actor();
        access.create(actor);
        validate(validator, request);
        int sources = (request.preSessionCheckId() == null ? 0 : 1) + (request.trainingSessionId() == null ? 0 : 1)
                + (request.careTaskId() == null ? 0 : 1);
        if (sources > 1)
            throw invalid("At most one source context is allowed.");
        if (!store.horseExists(request.horseId()))
            throw missing("Horse not found.");
        Core026Store.SessionContext session = null;
        if (request.trainingSessionId() != null)
            session = store.session(request.trainingSessionId(), false)
                    .orElseThrow(() -> missing("Session not found."));
        if (request.preSessionCheckId() != null) {
            long id = store.checkSession(request.preSessionCheckId())
                    .orElseThrow(() -> missing("Pre-session check not found."));
            session = store.session(id, false).orElseThrow(() -> missing("Session not found."));
        }
        if (session != null && session.horseId() != request.horseId())
            throw invalid("Source must reference the same horse.");
        if (request.careTaskId() != null && store.careHorse(request.careTaskId())
                .orElseThrow(() -> missing("Care task not found.")) != request.horseId().longValue())
            throw invalid("Source must reference the same horse.");
        boolean allowed = actor.has(Role.HEAD_TRAINER);
        if (actor.has(Role.TRAINER))
            allowed |= store.assignedTrainer(actor.id(), request.horseId(),
                    session == null ? null : session.programId());
        if (actor.has(Role.WORK_RIDER))
            allowed |= session != null && request.trainingSessionId() != null && session.riderId() == actor.id()
                    && "IN_PROGRESS".equals(session.status());
        if (!allowed && actor.has(Role.GROOM)) {
            if (request.careTaskId() != null)
                allowed |= access.groom(actor).mayReportCareTask(actor, request.careTaskId());
            else if (session != null)
                allowed |= access.groom(actor).mayCheck(actor, session.id());
            else
                allowed = true; // Matrix grants independent Groom reports; work contexts remain scoped.
        }
        AccessPolicy.require(allowed);
        if (request.preSessionCheckId() != null && store.incidentForCheck(request.preSessionCheckId()))
            throw conflict("An incident already references this check.");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        long id;
        try {
            id = store.insertIncident(request, actor.id(), "INC-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(java.util.Locale.ROOT),
                    now);
        } catch (DataIntegrityViolationException e) {
            throw conflict("Incident uniqueness or source relationship changed concurrently.");
        }
        Incident incident = store.incident(id)
                .orElseThrow(() -> new IllegalStateException("Inserted incident missing"));
        events.publishEvent(new IncidentReported(id, request.horseId(), actor.id()));
        return incident;
    }

    @Transactional(readOnly = true)
    public IncidentsPage list(int page, int size) {
        Actor actor = access.actor();
        access.read(actor);
        if (page < 0 || size < 1 || size > 100)
            throw invalid("Invalid page or size.");
        return store.incidents(access.allReports(actor) ? null : actor.id(), page, size);
    }

    @Transactional(readOnly = true)
    public Incident detail(long id) {
        Actor actor = access.actor();
        access.read(actor);
        if (id < 1)
            throw invalid("Positive incident ID required.");
        Incident incident = store.incident(id).orElseThrow(() -> missing("Incident not found."));
        AccessPolicy.require(access.allReports(actor) || incident.reportedByUserId() == actor.id());
        return incident;
    }

    static void validate(Validator validator, Object value) {
        if (value == null)
            throw invalid("Request is required.");
        var errors = validator.validate(value);
        if (!errors.isEmpty())
            throw new jakarta.validation.ConstraintViolationException(errors);
    }

    static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    static BusinessException missing(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }

    static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}
