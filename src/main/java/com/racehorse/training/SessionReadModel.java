package com.racehorse.training;

import com.racehorse.core026.Contracts;
import com.racehorse.core026.IntegrationPorts;
import com.racehorse.integration.Db;
import com.racehorse.restriction.RestrictionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DEMO IMPLEMENTATION (Flow 2 read side, CORE-005 contract; implementation tasks owned by Tiến).
 * BLOCKED is DERIVED here and never stored: AVAILABILITY_LOCKED, PRE_CHECK_MISSING, PRE_CHECK_ABNORMAL,
 * EXERCISE_MEDICAL_BLOCK, PROGRAM_NOT_ACTIVE. Also implements the CORE-026 SessionProjection port.
 */
@Component
public class SessionReadModel implements IntegrationPorts.SessionProjection {
    public record UserRef(long id, String userCode, String fullName) {}
    public record HorseRef(long id, String horseCode, String name) {}
    public record SessionExercise(long id, long exerciseDefinitionId, String exerciseName, String exerciseType,
                                  int exerciseOrder, BigDecimal distanceM, BigDecimal durationMin, Integer repetitions,
                                  BigDecimal restIntervalMin, String intensity, String surface, String suitabilityCategory,
                                  String acknowledgementNote) {}
    public record PreCheck(long id, String overallResult, String observations, UserRef performedBy, Instant performedAt) {}
    public record SessionSummary(long id, String sessionCode, HorseRef horse, long programId, String programName,
                                 long horseProgramPhaseId, String phaseName, Instant scheduledStart, Instant scheduledEnd,
                                 String status, UserRef workRider, UserRef trainer, String preCheckResult, boolean blocked,
                                 List<String> blockedReasons, String blockedMessage, String intensity, String surface,
                                 String focus) {}
    public record SessionDetail(SessionSummary summary, String trainingAvailability, String phaseIntensityMin,
                                String phaseIntensityMax, Instant actualStart, Instant actualEnd, String interruptionReason,
                                List<SessionExercise> exercises, PreCheck preSessionCheck, List<Long> linkedIncidentIds,
                                boolean hasRiderFeedback, boolean hasEvaluation, UserRef createdBy) {}

    static final String BASE = "SELECT s.*, ph.name AS phase_name, ph.intensity_min, ph.intensity_max, p.id AS program_id, "
            + "p.name AS program_name, p.status AS program_status, h.id AS horse_id, h.horse_code, h.name AS horse_name, "
            + "wr.user_code AS wr_code, wr.full_name AS wr_name, cb.user_code AS cb_code, cb.full_name AS cb_name "
            + "FROM dbo.training_session s JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id "
            + "JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id JOIN dbo.horse h ON h.id=p.horse_id "
            + "JOIN dbo.app_user wr ON wr.id=s.work_rider_user_id JOIN dbo.app_user cb ON cb.id=s.created_by_user_id ";

    private final JdbcTemplate jdbc;
    private final RestrictionService restrictions;

    public SessionReadModel(JdbcTemplate jdbc, RestrictionService restrictions) {
        this.jdbc = jdbc;
        this.restrictions = restrictions;
    }

    public List<SessionSummary> query(String where, Object... args) {
        return jdbc.queryForList(BASE + where, args).stream().map(this::summary).toList();
    }

    public SessionSummary summaryOf(long id) {
        var rows = jdbc.queryForList(BASE + "WHERE s.id=?", id);
        if (rows.isEmpty()) throw Db.notFound("Training session");
        return summary(rows.getFirst());
    }

    public SessionDetail detail(long id) {
        var rows = jdbc.queryForList(BASE + "WHERE s.id=?", id);
        if (rows.isEmpty()) throw Db.notFound("Training session");
        var r = rows.getFirst();
        SessionSummary s = summary(r);
        List<PreCheck> checks = jdbc.query("SELECT c.*, u.user_code, u.full_name FROM dbo.pre_session_check c "
                + "JOIN dbo.app_user u ON u.id=c.performed_by_user_id WHERE c.training_session_id=?", (c, n) -> new PreCheck(
                c.getLong("id"), c.getString("overall_result"), c.getString("observations"),
                new UserRef(c.getLong("performed_by_user_id"), c.getString("user_code"), c.getString("full_name")),
                Db.instant(c, "performed_at")), id);
        List<Long> incidents = jdbc.queryForList("SELECT i.id FROM dbo.incident i LEFT JOIN dbo.pre_session_check c "
                + "ON c.id=i.pre_session_check_id WHERE i.training_session_id=? OR c.training_session_id=? ORDER BY i.id", Long.class, id, id);
        Integer fb = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.rider_feedback WHERE training_session_id=?", Integer.class, id);
        Integer ev = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.session_result WHERE training_session_id=?", Integer.class, id);
        return new SessionDetail(s, restrictions.availability(s.horse().id()).availability(),
                (String) r.get("intensity_min"), (String) r.get("intensity_max"), Db.instant(r.get("actual_start")),
                Db.instant(r.get("actual_end")), (String) r.get("interruption_reason"), exercises(id),
                checks.isEmpty() ? null : checks.getFirst(), incidents, fb != null && fb > 0, ev != null && ev > 0,
                new UserRef(Db.lng(r, "created_by_user_id"), (String) r.get("cb_code"), (String) r.get("cb_name")));
    }

    public List<SessionExercise> exercises(long sessionId) {
        return jdbc.query("SELECT se.*, e.name, e.exercise_type FROM dbo.session_exercise se JOIN dbo.exercise_definition e "
                + "ON e.id=se.exercise_definition_id WHERE se.training_session_id=? ORDER BY se.exercise_order", (r, n) ->
                new SessionExercise(r.getLong("id"), r.getLong("exercise_definition_id"), r.getString("name"),
                        r.getString("exercise_type"), r.getInt("exercise_order"), r.getBigDecimal("distance"),
                        r.getBigDecimal("duration"), (Integer) r.getObject("repetitions", Integer.class),
                        r.getBigDecimal("rest_interval"), r.getString("intensity"), r.getString("surface"),
                        r.getString("suitability_category"), r.getString("acknowledgement_note")), sessionId);
    }

    /** Medical category-A check of the session's exercises against the CURRENT availability (BR-14, BR-D19). */
    public List<String> medicallyBlockedExercises(long horseId, List<Long> exerciseIds) {
        var a = restrictions.availability(horseId);
        List<String> blocked = new ArrayList<>();
        if (!"LIMITED".equals(a.availability())) return blocked;
        for (Long exId : exerciseIds) {
            for (var rule : a.exerciseRules()) {
                if ("PROHIBITED".equals(rule.ruleType()) && exId.equals(rule.exerciseDefinitionId())) {
                    blocked.add(rule.exerciseName() == null ? "exercise " + exId : rule.exerciseName());
                }
            }
        }
        return blocked;
    }

    private SessionSummary summary(Map<String, Object> r) {
        long id = Db.lng(r, "id");
        long horseId = Db.lng(r, "horse_id");
        long programId = Db.lng(r, "program_id");
        String status = (String) r.get("status");
        List<String> checks = jdbc.queryForList("SELECT overall_result FROM dbo.pre_session_check WHERE training_session_id=?",
                String.class, id);
        String pre = checks.isEmpty() ? null : checks.getFirst();
        List<String> reasons = new ArrayList<>();
        String message = null;
        if ("SCHEDULED".equals(status)) {
            var availability = restrictions.availability(horseId);
            if ("LOCKED".equals(availability.availability())) {
                reasons.add("AVAILABILITY_LOCKED");
                message = "Training is LOCKED by the Veterinarian: " + availability.reason();
            }
            List<Long> exIds = jdbc.queryForList("SELECT exercise_definition_id FROM dbo.session_exercise WHERE training_session_id=?",
                    Long.class, id);
            List<String> prohibited = medicallyBlockedExercises(horseId, exIds);
            if (!prohibited.isEmpty()) {
                reasons.add("EXERCISE_MEDICAL_BLOCK");
                if (message == null) message = "Prohibited by the Veterinarian restriction: " + String.join(", ", prohibited);
            }
            if (!"ACTIVE".equals(r.get("program_status"))) reasons.add("PROGRAM_NOT_ACTIVE");
            if (pre == null) reasons.add("PRE_CHECK_MISSING");
            else if ("ABNORMAL".equals(pre)) {
                reasons.add("PRE_CHECK_ABNORMAL");
                if (message == null) message = "Pre-session check found an abnormality — the session must not start.";
            }
        }
        List<UserRef> trainer = jdbc.query("SELECT TOP 1 u.id, u.user_code, u.full_name FROM dbo.trainer_assignment a JOIN dbo.app_user u "
                        + "ON u.id=a.trainer_user_id WHERE a.horse_training_program_id=? AND a.effective_to IS NULL ORDER BY a.id DESC",
                (t, n) -> new UserRef(t.getLong(1), t.getString(2), t.getString(3)), programId);
        return new SessionSummary(id, (String) r.get("session_code"),
                new HorseRef(horseId, (String) r.get("horse_code"), (String) r.get("horse_name")), programId,
                (String) r.get("program_name"), Db.lng(r, "horse_program_phase_id"), (String) r.get("phase_name"),
                Db.instant(r.get("scheduled_start")), Db.instant(r.get("scheduled_end")), status,
                new UserRef(Db.lng(r, "work_rider_user_id"), (String) r.get("wr_code"), (String) r.get("wr_name")),
                trainer.isEmpty() ? null : trainer.getFirst(), pre, !reasons.isEmpty(), reasons, message,
                (String) r.get("intensity"), (String) r.get("surface"), (String) r.get("focus"));
    }

    /** CORE-026 port: Groom Today list items. */
    @Override
    public Contracts.SessionSummary summary(long sessionId) {
        SessionSummary s = summaryOf(sessionId);
        return new Contracts.SessionSummary(s.id(), s.sessionCode(),
                new Contracts.HorseRef(s.horse().id(), s.horse().horseCode(), s.horse().name()), s.programId(),
                s.phaseName(), s.scheduledStart(), s.scheduledEnd(), s.status(),
                new Contracts.UserRef(s.workRider().id(), s.workRider().userCode(), s.workRider().fullName()),
                s.trainer() == null ? null : new Contracts.UserRef(s.trainer().id(), s.trainer().userCode(), s.trainer().fullName()),
                s.preCheckResult() == null ? null : Contracts.CheckResult.valueOf(s.preCheckResult()),
                s.blocked(), s.blockedReasons(), s.blockedMessage());
    }
}
