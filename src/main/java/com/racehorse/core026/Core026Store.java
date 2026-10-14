package com.racehorse.core026;

import com.racehorse.core026.Contracts.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * Explicit SQL mappings preserve DB-01; no JPA shadow entities or schema
 * generation.
 */
@Repository
public class Core026Store {
    private final JdbcTemplate jdbc;

    public Core026Store(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SESSION_FROM = " FROM dbo.training_session s JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id ";

    public record SessionContext(long id, long horseId, long programId, long riderId, String status) {
    }

    public boolean horseExists(long id) {
        return count("SELECT COUNT(*) FROM dbo.horse WHERE id=?", id) != 0;
    }

    public Optional<SessionContext> session(long id, boolean lock) {
        // Serialize pre-check submission with the CORE-024 session state update on SQL
        // Server.
        String from = lock
                ? SESSION_FROM.replace("dbo.training_session s", "dbo.training_session s WITH (UPDLOCK, HOLDLOCK)")
                : SESSION_FROM;
        return jdbc.query(
                "SELECT s.id,p.horse_id,p.id AS program_id,s.work_rider_user_id,s.status" + from + "WHERE s.id=?",
                (r, n) -> new SessionContext(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4), r.getString(5)),
                id).stream().findFirst();
    }

    public boolean assignedTrainer(long actor, long horse, Long program) {
        return count(
                "SELECT COUNT(*) FROM dbo.trainer_assignment a JOIN dbo.horse_training_program p ON p.id=a.horse_training_program_id WHERE a.trainer_user_id=? AND p.horse_id=? AND a.effective_to IS NULL AND a.effective_from<=?"
                        + (program == null ? "" : " AND p.id=?"),
                program == null ? new Object[] { actor, horse, java.sql.Date.valueOf(LocalDate.now(ZoneOffset.UTC)) }
                        : new Object[] { actor, horse, java.sql.Date.valueOf(LocalDate.now(ZoneOffset.UTC)),
                                program }) != 0;
    }

    public Optional<Long> careHorse(long id) {
        return jdbc.query("SELECT horse_id FROM dbo.care_task WHERE id=?", (r, n) -> r.getLong(1), id).stream()
                .findFirst();
    }

    public Optional<Long> checkSession(long id) {
        return jdbc
                .query("SELECT training_session_id FROM dbo.pre_session_check WHERE id=?", (r, n) -> r.getLong(1), id)
                .stream().findFirst();
    }

    public Optional<CheckResult> checkResult(long sessionId) {
        return jdbc.query("SELECT overall_result FROM dbo.pre_session_check WHERE training_session_id=?",
                (r, n) -> CheckResult.valueOf(r.getString(1)), sessionId).stream().findFirst();
    }

    public boolean incidentForCheck(long id) {
        return count("SELECT COUNT(*) FROM dbo.incident WHERE pre_session_check_id=?", id) > 0;
    }

    public long insertIncident(IncidentCreateRequest r, long actor, String code, Instant now) {
        return insert(
                "INSERT INTO dbo.incident (incident_code,horse_id,reported_by_user_id,pre_session_check_id,training_session_id,care_task_id,observed_at,reported_at,incident_type,observed_severity,description,status,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                code, r.horseId(), actor, r.preSessionCheckId(), r.trainingSessionId(), r.careTaskId(),
                timestamp(r.observedAt()), timestamp(now), r.incidentType().name(), r.observedSeverity().name(),
                r.description(), "REPORTED", timestamp(now));
    }

    public long insertCheck(long session, long actor, CheckResult result, String observations, Instant now) {
        return insert(
                "INSERT INTO dbo.pre_session_check (training_session_id,performed_by_user_id,performed_at,observations,overall_result,created_at) VALUES (?,?,?,?,?,?)",
                session, actor, timestamp(now), observations, result.name(), timestamp(now));
    }

    public UserRef user(long id) {
        return jdbc.queryForObject("SELECT id,user_code,full_name FROM dbo.app_user WHERE id=?",
                (r, n) -> new UserRef(r.getLong(1), r.getString(2), r.getString(3)), id);
    }

    public Optional<Incident> incident(long id) {
        return jdbc.query("SELECT * FROM dbo.incident WHERE id=?", this::incidentRow, id).stream().findFirst();
    }

    public IncidentsPage incidents(Long reporter, int page, int size) {
        String where = reporter == null ? "" : " WHERE reported_by_user_id=?";
        Object[] filters = reporter == null ? new Object[] {} : new Object[] { reporter };
        long total = count("SELECT COUNT(*) FROM dbo.incident" + where, filters);
        Object[] args = reporter == null ? new Object[] { (long) page * size, size }
                : new Object[] { reporter, (long) page * size, size };
        return new IncidentsPage(jdbc.query(
                "SELECT * FROM dbo.incident" + where
                        + " ORDER BY reported_at DESC,id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                this::incidentRow, args), page, size, total);
    }

    private Incident incidentRow(ResultSet r, int n) throws SQLException {
        return new Incident(r.getLong("id"), r.getString("incident_code"), r.getLong("horse_id"),
                r.getLong("reported_by_user_id"), nullableLong(r, "medical_case_id"),
                nullableLong(r, "pre_session_check_id"), nullableLong(r, "training_session_id"),
                nullableLong(r, "care_task_id"), IncidentType.valueOf(r.getString("incident_type")),
                Severity.valueOf(r.getString("observed_severity")), r.getString("description"),
                instant(r, "observed_at"), instant(r, "reported_at"), IncidentStatus.valueOf(r.getString("status")));
    }

    private long count(String sql, Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++)
                statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    // Bind UTC local datetime explicitly: SQL DATETIME2 has no timezone,
    // independent of JVM timezone.
    private static LocalDateTime timestamp(Instant i) {
        return LocalDateTime.ofInstant(i.truncatedTo(java.time.temporal.ChronoUnit.MILLIS), ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        return r.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }

    private static Long nullableLong(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column);
        return r.wasNull() ? null : value;
    }
}
