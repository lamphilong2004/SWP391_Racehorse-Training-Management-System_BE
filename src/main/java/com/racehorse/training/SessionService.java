package com.racehorse.training;

import com.racehorse.audit.AuditService;
import com.racehorse.auth.AuthenticatedUser;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.core026.Contracts;
import com.racehorse.core026.IncidentService;
import com.racehorse.core026.PreSessionStartGate;
import com.racehorse.identity.RoleCode;
import com.racehorse.integration.Db;
import com.racehorse.realtime.SessionEvents;
import com.racehorse.restriction.RestrictionService;
import com.racehorse.training.SessionReadModel.SessionDetail;
import com.racehorse.training.SessionReadModel.SessionSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DEMO IMPLEMENTATION of Flow 2 sessions (CORE-005 contract; tasks CORE-024/025/026 owned by Tiến / Đức).
 * Session status = schema CHECK (SCHEDULED, IN_PROGRESS, PAUSED, COMPLETED, INTERRUPTED); BLOCKED is derived.
 * Medical hard blocks (LOCKED; LIMITED prohibited exercise) are re-checked at scheduling AND at start (BR-14).
 * Pause / Resume / Stop are Trainer-only (P-01/P-02); Finish is Work-Rider only; INTERRUPTED never becomes COMPLETED.
 */
@Service
public class SessionService {
    public record ExerciseInput(Long exerciseDefinitionId, Integer exerciseOrder, BigDecimal distanceM, BigDecimal durationMin,
                                Integer repetitions, BigDecimal restIntervalMin, String intensity, String surface,
                                String acknowledgementNote) {}
    public record CreateSessionRequest(Long horseProgramPhaseId, Instant scheduledStart, Instant scheduledEnd, String surface,
                                       String focus, String intensity, Long workRiderUserId, List<ExerciseInput> exercises,
                                       Boolean riderWarningAcknowledged) {}
    public record SuitabilityRequest(Long horseProgramPhaseId, Long horseId, List<ExerciseInput> exercises) {}
    public record SuitabilityItem(Long exerciseDefinitionId, Integer exerciseOrder, String exerciseName, String category,
                                  String label, List<String> messages) {}
    public record Suitability(String trainingAvailability, String availabilityReason, boolean canSchedule,
                              List<SuitabilityItem> exercises) {}
    public record RiderRequest(Instant scheduledStart, Instant scheduledEnd) {}
    public record StopRequest(String reason, Boolean medicallyRelevant, String incidentType, String observedSeverity) {}
    public record ReportIssueRequest(String incidentType, String observedSeverity, String description) {}
    public record IntensityRequest(String intensity, String note) {}
    public record FeedbackRequest(Map<String, Object> responses) {}
    public record EvaluationRequest(String evaluationLabel, String evaluatedSummary, String evaluationNotes, String recommendation) {}

    public static final List<String> EVALUATION = List.of("MET", "PARTIALLY_MET", "NOT_MET");

    private final JdbcTemplate jdbc;
    private final SessionReadModel read;
    private final ProgramService programs;
    private final RestrictionService restrictions;
    private final PreSessionStartGate gate;
    private final IncidentService incidents;
    private final SessionEvents events;
    private final AuditService audit;
    private final ObjectMapper json;

    public SessionService(JdbcTemplate jdbc, SessionReadModel read, ProgramService programs, RestrictionService restrictions,
                          PreSessionStartGate gate, IncidentService incidents, SessionEvents events, AuditService audit,
                          ObjectMapper json) {
        this.jdbc = jdbc;
        this.read = read;
        this.programs = programs;
        this.restrictions = restrictions;
        this.gate = gate;
        this.incidents = incidents;
        this.events = events;
        this.audit = audit;
        this.json = json;
    }

    // ---------------- reads ----------------

    @Transactional(readOnly = true)
    public List<SessionSummary> list(Instant from, Instant to, Long trainerId, Long programId, Long horseId) {
        AuthenticatedUser u = Db.require(RoleCode.HEAD_TRAINER, RoleCode.TRAINER, RoleCode.WORK_RIDER, RoleCode.CLUB_MANAGER,
                RoleCode.VETERINARIAN);
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder("WHERE 1=1");
        if (from != null) { w.append(" AND s.scheduled_start>=?"); args.add(Db.utc(from)); }
        if (to != null) { w.append(" AND s.scheduled_start<?"); args.add(Db.utc(to)); }
        if (programId != null) { w.append(" AND p.id=?"); args.add(programId); }
        if (horseId != null) { w.append(" AND h.id=?"); args.add(horseId); }
        Long scopeTrainer = trainerId;
        if (u.hasRole(RoleCode.TRAINER) && !u.hasRole(RoleCode.HEAD_TRAINER)) scopeTrainer = u.id(); // Q-35 / P-04
        if (scopeTrainer != null) {
            w.append(" AND EXISTS (SELECT 1 FROM dbo.trainer_assignment a WHERE a.horse_training_program_id=p.id "
                    + "AND a.trainer_user_id=? AND a.effective_to IS NULL)");
            args.add(scopeTrainer);
        }
        if (u.hasRole(RoleCode.WORK_RIDER) && u.roles().size() == 1) { w.append(" AND s.work_rider_user_id=?"); args.add(u.id()); }
        return read.query(w + " ORDER BY s.scheduled_start, s.id", args.toArray());
    }

    @Transactional(readOnly = true)
    public SessionDetail detail(long id) {
        AuthenticatedUser u = Db.require(RoleCode.HEAD_TRAINER, RoleCode.TRAINER, RoleCode.WORK_RIDER, RoleCode.CLUB_MANAGER,
                RoleCode.VETERINARIAN, RoleCode.GROOM);
        SessionSummary s = read.summaryOf(id);
        scope(u, s, true);
        return read.detail(id);
    }

    @Transactional(readOnly = true)
    public List<SessionSummary> riderAssignments(boolean history) {
        AuthenticatedUser u = Db.require(RoleCode.WORK_RIDER);
        return read.query("WHERE s.work_rider_user_id=? AND s.status " + (history ? "IN ('COMPLETED','INTERRUPTED')"
                : "IN ('SCHEDULED','IN_PROGRESS','PAUSED')") + " ORDER BY s.scheduled_start" + (history ? " DESC" : ""), u.id());
    }

    @Transactional(readOnly = true)
    public List<ProgramService.Recommendation> riderRecommendations(RiderRequest req) {
        Db.require(RoleCode.TRAINER);
        return programs.usersWithRole("WORK_RIDER").stream().map(r -> {
            boolean conflict = req != null && req.scheduledStart() != null && riderConflict(r.id(), req.scheduledStart(),
                    req.scheduledEnd(), null);
            List<String> skills = jdbc.queryForList("SELECT s.name FROM dbo.user_skill us JOIN dbo.skill s ON s.id=us.skill_id "
                    + "WHERE us.user_id=? AND s.kind='RIDER_SKILL'", String.class, r.id());
            List<String> reasons = new ArrayList<>();
            if (!skills.isEmpty()) reasons.add("Skills: " + String.join(", ", skills));
            String level = jdbc.queryForObject("SELECT experience_level FROM dbo.app_user WHERE id=?", String.class, r.id());
            if (level != null) reasons.add("Experience: " + level);
            return new ProgramService.Recommendation(r, conflict ? "NOT_RECOMMENDED" : skills.isEmpty() ? "SUITABLE" : "RECOMMENDED",
                    reasons, conflict ? List.of("Schedule conflict (Operational Hard Block)") : List.of(), conflict);
        }).toList();
    }

    @Transactional(readOnly = true)
    public Suitability suitability(SuitabilityRequest req) {
        Db.require(RoleCode.TRAINER, RoleCode.HEAD_TRAINER);
        long horseId;
        if (req.horseProgramPhaseId() != null) {
            horseId = jdbc.queryForObject("SELECT p.horse_id FROM dbo.horse_program_phase ph JOIN dbo.horse_training_program p "
                    + "ON p.id=ph.horse_training_program_id WHERE ph.id=?", Long.class, req.horseProgramPhaseId());
        } else if (req.horseId() != null) horseId = req.horseId();
        else throw Db.invalid("horseProgramPhaseId or horseId is required.");
        return evaluate(horseId, req.exercises() == null ? List.of() : req.exercises());
    }

    private Suitability evaluate(long horseId, List<ExerciseInput> exercises) {
        var availability = restrictions.availability(horseId);
        List<SuitabilityItem> items = new ArrayList<>();
        boolean locked = "LOCKED".equals(availability.availability());
        for (ExerciseInput e : exercises) {
            String name = jdbc.queryForList("SELECT name FROM dbo.exercise_definition WHERE id=?", String.class, e.exerciseDefinitionId())
                    .stream().findFirst().orElseThrow(() -> Db.notFound("Exercise"));
            List<String> prohibited = read.medicallyBlockedExercises(horseId, List.of(e.exerciseDefinitionId()));
            if (locked) items.add(new SuitabilityItem(e.exerciseDefinitionId(), e.exerciseOrder(), name, "A", "MEDICAL_HARD_BLOCK",
                    List.of("Training is LOCKED by the Veterinarian: " + availability.reason())));
            else if (!prohibited.isEmpty()) items.add(new SuitabilityItem(e.exerciseDefinitionId(), e.exerciseOrder(), name, "A",
                    "MEDICAL_HARD_BLOCK", List.of("Prohibited by the Veterinarian restriction (" + availability.reason()
                    + "). Remove or replace it — no override.")));
            else items.add(new SuitabilityItem(e.exerciseDefinitionId(), e.exerciseOrder(), name, null, "SUITABLE",
                    List.of("LIMITED".equals(availability.availability()) ? "Allowed under the current LIMITED restriction"
                            : "No restriction")));
        }
        boolean can = !locked && items.stream().noneMatch(i -> "A".equals(i.category()));
        return new Suitability(availability.availability(), availability.reason(), can, items);
    }

    // ---------------- create ----------------

    @Transactional
    public SessionDetail create(CreateSessionRequest req) {
        AuthenticatedUser trainer = Db.require(RoleCode.TRAINER);
        if (req == null || req.horseProgramPhaseId() == null || req.scheduledStart() == null || req.workRiderUserId() == null
                || req.exercises() == null || req.exercises().isEmpty())
            throw Db.invalid("horseProgramPhaseId, scheduledStart, workRiderUserId and at least one exercise are required.");
        var phases = jdbc.queryForList("SELECT ph.*, p.horse_id, p.status AS program_status, p.id AS program_id FROM dbo.horse_program_phase ph "
                + "JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id WHERE ph.id=?", req.horseProgramPhaseId());
        if (phases.isEmpty()) throw Db.notFound("Program phase");
        var ph = phases.getFirst();
        long programId = Db.lng(ph, "program_id");
        long horseId = Db.lng(ph, "horse_id");
        if (!programs.isAssignedTrainer(programId, trainer.id()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "The program is not assigned to you.");
        if (!"ACTIVE".equals(ph.get("program_status"))) throw Db.rule("The program is not ACTIVE.");
        if (req.scheduledEnd() != null && req.scheduledEnd().isBefore(req.scheduledStart()))
            throw Db.invalid("scheduledEnd must not be before scheduledStart.");
        if (programs.usersWithRole("WORK_RIDER").stream().noneMatch(r -> r.id() == req.workRiderUserId()))
            throw Db.invalid("workRiderUserId must be an ACTIVE Work Rider.");
        Instant end = req.scheduledEnd() == null ? req.scheduledStart().plusSeconds(3600) : req.scheduledEnd();
        if (riderConflict(req.workRiderUserId(), req.scheduledStart(), end, null))
            throw Db.rule("Operational Hard Block: the Work Rider already has a session in this time slot.");
        checkIntensity(req.intensity(), (String) ph.get("intensity_min"), (String) ph.get("intensity_max"));
        Suitability s = evaluate(horseId, req.exercises());
        if ("LOCKED".equals(s.trainingAvailability()))
            throw Db.rule("Training is LOCKED by the Veterinarian: " + s.availabilityReason());
        if (!s.canSchedule()) throw Db.rule("Medical Hard Block: " + s.exercises().stream()
                .filter(i -> "A".equals(i.category())).map(SuitabilityItem::exerciseName).toList() + " — remove or replace it.");
        Long next = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM dbo.training_session WITH (UPDLOCK, HOLDLOCK)", Long.class);
        String code = String.format("SES-%04d", next);
        long id = Db.insert(jdbc, "INSERT INTO dbo.training_session (session_code, horse_program_phase_id, created_by_user_id, "
                        + "work_rider_user_id, scheduled_start, scheduled_end, surface, focus, intensity, status, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'SCHEDULED',?)", code,
                req.horseProgramPhaseId(), trainer.id(), req.workRiderUserId(), Db.utc(req.scheduledStart()), Db.utc(end),
                req.surface(), req.focus(), req.intensity(), Db.nowUtc());
        int order = 1;
        for (ExerciseInput e : req.exercises()) {
            if (e.intensity() != null && !ProgramService.INTENSITY.contains(e.intensity()))
                throw Db.invalid("Exercise intensity must be one of " + ProgramService.INTENSITY);
            jdbc.update("INSERT INTO dbo.session_exercise (training_session_id, exercise_definition_id, exercise_order, distance, "
                            + "duration, repetitions, rest_interval, intensity, surface, suitability_category, acknowledgement_note, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,NULL,?,?)", id, e.exerciseDefinitionId(), order++, e.distanceM(), e.durationMin(),
                    e.repetitions(), e.restIntervalMin(), e.intensity(), e.surface() == null ? req.surface() : e.surface(),
                    e.acknowledgementNote(), Db.nowUtc());
        }
        programs.notify(req.workRiderUserId(), "SCHEDULE", "SESSION_ASSIGNED", "New riding assignment",
                "Session " + code + " has been assigned to you.", "training_session", id);
        audit.record("SESSION_CREATED", "training_session", id, "program=" + programId);
        return read.detail(id);
    }

    // ---------------- lifecycle ----------------

    @Transactional
    public SessionDetail start(long id) {
        AuthenticatedUser u = Db.require(RoleCode.WORK_RIDER, RoleCode.TRAINER);
        SessionSummary s = lock(id);
        scope(u, s, false);
        if (!"SCHEDULED".equals(s.status())) throw Db.conflict("Only a SCHEDULED session can be started (status " + s.status() + ").");
        if (s.blocked()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Start is blocked: " + String.join(", ", s.blockedReasons())
                    + (s.blockedMessage() == null ? "" : " — " + s.blockedMessage()));
        }
        gate.requirePass(id);
        if (s.scheduledStart() != null && Instant.now().isBefore(s.scheduledStart().minusSeconds(60)))
            throw Db.rule("The session cannot start before its scheduled time (Q-34 ASSUMPTION).");
        int n = jdbc.update("UPDATE dbo.training_session SET status='IN_PROGRESS', actual_start=?, updated_at=? WHERE id=? AND status='SCHEDULED'",
                Db.nowUtc(), Db.nowUtc(), id);
        if (n != 1) throw Db.conflict("The session was started concurrently.");
        audit.record("SESSION_STARTED", "training_session", id, null);
        return changed(id, "SCHEDULED");
    }

    @Transactional
    public SessionDetail pause(long id) {
        return transition(id, "IN_PROGRESS", "PAUSED", RoleCode.TRAINER, null, "SESSION_PAUSED");
    }

    @Transactional
    public SessionDetail resume(long id) {
        SessionSummary s = read.summaryOf(id);
        if ("LOCKED".equals(restrictions.availability(s.horse().id()).availability()))
            throw Db.rule("Training is LOCKED by the Veterinarian — the session cannot resume.");
        return transition(id, "PAUSED", "IN_PROGRESS", RoleCode.TRAINER, null, "SESSION_RESUMED");
    }

    @Transactional
    public SessionDetail complete(long id) {
        return transition(id, "IN_PROGRESS", "COMPLETED", RoleCode.WORK_RIDER, null, "SESSION_COMPLETED");
    }

    @Transactional
    public SessionDetail stop(long id, StopRequest req) {
        if (req == null || req.reason() == null || req.reason().isBlank() || req.reason().length() > 1000)
            throw Db.invalid("A stop reason is required (BR-D23).");
        boolean medical = Boolean.TRUE.equals(req.medicallyRelevant());
        if (medical && (req.incidentType() == null || req.observedSeverity() == null))
            throw Db.invalid("incidentType and observedSeverity are required for a medically relevant stop.");
        SessionSummary before = read.summaryOf(id);
        String from = before.status();
        if (!"IN_PROGRESS".equals(from) && !"PAUSED".equals(from)) throw Db.conflict("Only an IN_PROGRESS or PAUSED session can be stopped.");
        SessionDetail d = transition(id, from, "INTERRUPTED", RoleCode.TRAINER, req.reason(), "SESSION_STOPPED");
        if (medical) {
            incidents.create(new Contracts.IncidentCreateRequest(before.horse().id(), null, id,
                    null, Contracts.IncidentType.valueOf(req.incidentType()), Contracts.Severity.valueOf(req.observedSeverity()),
                    req.reason(), Instant.now()));
            d = read.detail(id);
        }
        return d;
    }

    @Transactional
    public Map<String, Object> reportIssue(long id, ReportIssueRequest req) {
        AuthenticatedUser u = Db.require(RoleCode.WORK_RIDER, RoleCode.TRAINER);
        if (req == null || req.incidentType() == null || req.observedSeverity() == null || req.description() == null
                || req.description().isBlank())
            throw Db.invalid("incidentType, observedSeverity and description are required.");
        SessionSummary s = lock(id);
        scope(u, s, false);
        if (!"IN_PROGRESS".equals(s.status()) && !"PAUSED".equals(s.status()))
            throw Db.conflict("An issue can be reported only during an IN_PROGRESS or PAUSED session.");
        var incident = incidents.create(new Contracts.IncidentCreateRequest(s.horse().id(), null, id, null,
                Contracts.IncidentType.valueOf(req.incidentType()), Contracts.Severity.valueOf(req.observedSeverity()),
                req.description(), Instant.now()));
        String reason = "Issue reported (" + incident.incidentCode() + "): " + req.description();
        jdbc.update("UPDATE dbo.training_session SET status='INTERRUPTED', interruption_reason=?, actual_end=?, updated_at=? WHERE id=?",
                reason.length() > 1000 ? reason.substring(0, 1000) : reason, Db.nowUtc(), Db.nowUtc(), id);
        for (var vet : programs.usersWithRole("VETERINARIAN"))
            programs.notify(vet.id(), "MEDICAL", "INCIDENT_REPORTED", "Incident reported in session " + s.sessionCode(),
                    req.description(), "incident", incident.id());
        if (s.trainer() != null)
            programs.notify(s.trainer().id(), "MEDICAL", "INCIDENT_REPORTED", "Incident reported in session " + s.sessionCode(),
                    req.description(), "incident", incident.id());
        audit.record("SESSION_INTERRUPTED_BY_ISSUE", "training_session", id, incident.incidentCode());
        changed(id, s.status());
        return Map.of("incidentId", incident.id(), "incidentCode", incident.incidentCode(), "sessionStatus", "INTERRUPTED");
    }

    @Transactional
    public SessionDetail reduceIntensity(long id, IntensityRequest req) {
        AuthenticatedUser u = Db.require(RoleCode.TRAINER);
        SessionSummary s = lock(id);
        scope(u, s, false);
        if (!"IN_PROGRESS".equals(s.status()) && !"PAUSED".equals(s.status())) throw Db.conflict("The session is not live.");
        if (req == null || req.intensity() == null || !ProgramService.INTENSITY.contains(req.intensity()))
            throw Db.invalid("intensity must be one of " + ProgramService.INTENSITY);
        var d = read.detail(id);
        String current = s.intensity();
        if (current != null && ProgramService.INTENSITY.indexOf(req.intensity()) >= ProgramService.INTENSITY.indexOf(current))
            throw Db.rule("The new intensity must be lower than the current " + current + ".");
        if (d.phaseIntensityMin() != null && ProgramService.INTENSITY.indexOf(req.intensity()) < ProgramService.INTENSITY.indexOf(d.phaseIntensityMin()))
            throw Db.rule("The new intensity is below the approved phase range (" + d.phaseIntensityMin() + "–" + d.phaseIntensityMax() + ").");
        jdbc.update("UPDATE dbo.training_session SET intensity=?, updated_at=? WHERE id=?", req.intensity(), Db.nowUtc(), id);
        audit.record("SESSION_INTENSITY_REDUCED", "training_session", id, current + " -> " + req.intensity());
        return changed(id, s.status());
    }

    // ---------------- after the ride ----------------

    @Transactional
    public Map<String, Object> feedback(long id, FeedbackRequest req) {
        AuthenticatedUser u = Db.require(RoleCode.WORK_RIDER);
        SessionSummary s = read.summaryOf(id);
        scope(u, s, false);
        if (!"COMPLETED".equals(s.status()) && !"INTERRUPTED".equals(s.status()))
            throw Db.conflict("Rider feedback is collected after a COMPLETED or INTERRUPTED session.");
        if (req == null || req.responses() == null || req.responses().isEmpty()) throw Db.invalid("responses are required.");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.rider_feedback WHERE training_session_id=?", Integer.class, id);
        if (exists != null && exists > 0) throw Db.conflict("Rider feedback was already submitted.");
        String body = json.writeValueAsString(req.responses());
        if (body.length() > 4000) throw Db.invalid("Feedback is too long.");
        long fid = Db.insert(jdbc, "INSERT INTO dbo.rider_feedback (training_session_id, submitted_by_user_id, submitted_at, responses, created_at) "
                + "VALUES (?,?,?,?,?)", id, u.id(), Db.nowUtc(), body, Db.nowUtc());
        return Map.of("id", fid, "sessionId", id, "responses", req.responses(), "sessionStatus", s.status());
    }

    @Transactional
    public Map<String, Object> evaluation(long id, EvaluationRequest req) {
        AuthenticatedUser u = Db.require(RoleCode.TRAINER);
        SessionSummary s = read.summaryOf(id);
        scope(u, s, false);
        if (!"COMPLETED".equals(s.status()) && !"INTERRUPTED".equals(s.status()))
            throw Db.conflict("Only a COMPLETED or INTERRUPTED session can be evaluated.");
        if (req == null || req.evaluationLabel() == null || !EVALUATION.contains(req.evaluationLabel()))
            throw Db.invalid("evaluationLabel must be one of " + EVALUATION);
        if (req.evaluationNotes() == null || req.evaluationNotes().isBlank()) throw Db.invalid("Professional notes are required.");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.session_result WHERE training_session_id=?", Integer.class, id);
        if (exists != null && exists > 0) throw Db.conflict("This session was already evaluated.");
        long rid = Db.insert(jdbc, "INSERT INTO dbo.session_result (training_session_id, evaluated_by_user_id, evaluated_summary, "
                        + "evaluation_label, evaluation_notes, recommendation, evaluated_at, created_at) VALUES (?,?,?,?,?,?,?,?)",
                id, u.id(), req.evaluatedSummary(), req.evaluationLabel(), req.evaluationNotes(), req.recommendation(),
                Db.nowUtc(), Db.nowUtc());
        audit.record("SESSION_EVALUATED", "training_session", id, req.evaluationLabel());
        return result(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> result(long id) {
        AuthenticatedUser u = Db.require(RoleCode.HEAD_TRAINER, RoleCode.TRAINER, RoleCode.WORK_RIDER, RoleCode.CLUB_MANAGER,
                RoleCode.VETERINARIAN);
        SessionSummary s = read.summaryOf(id);
        scope(u, s, true);
        var samples = jdbc.queryForList("SELECT recorded_at, heart_rate, speed, distance, source FROM dbo.training_metric_sample "
                + "WHERE training_session_id=? ORDER BY recorded_at, id", id).stream().map(r -> Map.<String, Object>of(
                "recordedAt", Db.instant(r.get("recorded_at")), "heartRate", r.get("heart_rate") == null ? "" : r.get("heart_rate"),
                "speedKmh", r.get("speed") == null ? "" : r.get("speed"), "distanceM", r.get("distance") == null ? "" : r.get("distance"),
                "source", r.get("source"))).toList();
        Map<String, Object> actual = jdbc.queryForMap("SELECT MAX(heart_rate) AS maxHeartRate, AVG(CAST(heart_rate AS DECIMAL(9,2))) AS avgHeartRate, "
                + "MAX(speed) AS maxSpeedKmh, AVG(speed) AS avgSpeedKmh, MAX(distance) AS totalDistanceM, COUNT(*) AS sampleCount, "
                + "SUM(CASE WHEN source='SIMULATOR' THEN 1 ELSE 0 END) AS simulatedCount FROM dbo.training_metric_sample WHERE training_session_id=?", id);
        var alerts = jdbc.queryForList("SELECT a.*, u.full_name AS ack_name FROM dbo.monitoring_alert a LEFT JOIN dbo.app_user u "
                + "ON u.id=a.acknowledged_by_user_id WHERE a.training_session_id=? ORDER BY a.raised_at", id);
        var fb = jdbc.queryForList("SELECT f.*, u.full_name FROM dbo.rider_feedback f JOIN dbo.app_user u ON u.id=f.submitted_by_user_id "
                + "WHERE f.training_session_id=?", id);
        var ev = jdbc.queryForList("SELECT r.*, u.full_name FROM dbo.session_result r JOIN dbo.app_user u ON u.id=r.evaluated_by_user_id "
                + "WHERE r.training_session_id=?", id);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("session", read.detail(id));
        out.put("actual", actual);
        out.put("samples", samples);
        out.put("alerts", alerts.stream().map(a -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", a.get("id"));
            m.put("metric", a.get("metric"));
            m.put("metricValue", a.get("metric_value"));
            m.put("thresholdApplied", a.get("threshold_applied"));
            m.put("severity", a.get("severity"));
            m.put("raisedAt", Db.instant(a.get("raised_at")));
            m.put("acknowledgedAt", Db.instant(a.get("acknowledged_at")));
            m.put("acknowledgedBy", a.get("ack_name"));
            m.put("actionTaken", a.get("action_taken"));
            m.put("escalatedToVetAt", Db.instant(a.get("escalated_to_vet_at")));
            return m;
        }).toList());
        out.put("riderFeedback", fb.isEmpty() ? null : Map.of("submittedBy", fb.getFirst().get("full_name"),
                "submittedAt", Db.instant(fb.getFirst().get("submitted_at")), "responses", json.readValue((String) fb.getFirst().get("responses"), Map.class)));
        out.put("evaluation", ev.isEmpty() ? null : Map.of("evaluationLabel", Objects.toString(ev.getFirst().get("evaluation_label"), ""),
                "evaluationNotes", Objects.toString(ev.getFirst().get("evaluation_notes"), ""),
                "recommendation", Objects.toString(ev.getFirst().get("recommendation"), ""),
                "evaluatedSummary", Objects.toString(ev.getFirst().get("evaluated_summary"), ""),
                "evaluatedBy", ev.getFirst().get("full_name"), "evaluatedAt", Db.instant(ev.getFirst().get("evaluated_at"))));
        return out;
    }

    // ---------------- helpers ----------------

    private SessionDetail transition(long id, String from, String to, RoleCode role, String reason, String auditAction) {
        AuthenticatedUser u = Db.require(role);
        SessionSummary s = lock(id);
        scope(u, s, false);
        if (!from.equals(s.status())) throw Db.conflict("Invalid transition " + s.status() + " → " + to + ".");
        boolean ends = "COMPLETED".equals(to) || "INTERRUPTED".equals(to);
        int n = jdbc.update("UPDATE dbo.training_session SET status=?, interruption_reason=COALESCE(?, interruption_reason), "
                        + "actual_end=" + (ends ? "?" : "actual_end") + ", updated_at=? WHERE id=? AND status=?",
                ends ? new Object[]{to, reason, Db.nowUtc(), Db.nowUtc(), id, from} : new Object[]{to, reason, Db.nowUtc(), id, from});
        if (n != 1) throw Db.conflict("The session changed concurrently.");
        audit.record(auditAction, "training_session", id, reason == null ? null : reason.substring(0, Math.min(200, reason.length())));
        return changed(id, from);
    }

    private SessionDetail changed(long id, String previous) {
        SessionDetail d = read.detail(id);
        events.state(d, previous);
        return d;
    }

    private SessionSummary lock(long id) {
        jdbc.queryForList("SELECT id FROM dbo.training_session WITH (UPDLOCK, HOLDLOCK) WHERE id=?", id);
        return read.summaryOf(id);
    }

    /** Row scoping (role-feature-matrix §3a): Trainer = assigned programs, Work Rider = own sessions. */
    void scope(AuthenticatedUser u, SessionSummary s, boolean readOnly) {
        if (u.hasRole(RoleCode.HEAD_TRAINER) || u.hasRole(RoleCode.VETERINARIAN) || u.hasRole(RoleCode.CLUB_MANAGER)) {
            if (readOnly) return;
        }
        if (u.hasRole(RoleCode.TRAINER) && programs.isAssignedTrainer(s.programId(), u.id())) return;
        if (u.hasRole(RoleCode.WORK_RIDER) && s.workRider().id() == u.id()) return;
        if (readOnly && u.hasRole(RoleCode.GROOM)) return;
        throw new BusinessException(ErrorCode.FORBIDDEN, "The session is outside your assigned scope.");
    }

    private boolean riderConflict(long riderId, Instant start, Instant end, Long excludeSession) {
        Instant e = end == null ? start.plusSeconds(3600) : end;
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.training_session WHERE work_rider_user_id=? "
                        + "AND status IN ('SCHEDULED','IN_PROGRESS','PAUSED') AND scheduled_start<? "
                        + "AND COALESCE(scheduled_end, DATEADD(HOUR,1,scheduled_start))>?" + (excludeSession == null ? "" : " AND id<>?"),
                Integer.class, excludeSession == null ? new Object[]{riderId, Db.utc(e), Db.utc(start)}
                        : new Object[]{riderId, Db.utc(e), Db.utc(start), excludeSession});
        return c != null && c > 0;
    }

    private static void checkIntensity(String value, String min, String max) {
        if (value == null) return;
        var scale = ProgramService.INTENSITY;
        if (!scale.contains(value)) throw Db.invalid("intensity must be one of " + scale);
        if (min != null && scale.indexOf(value) < scale.indexOf(min) || max != null && scale.indexOf(value) > scale.indexOf(max))
            throw Db.rule("Session intensity must stay inside the approved phase range " + min + "–" + max + " (BR-D30).");
    }
}
