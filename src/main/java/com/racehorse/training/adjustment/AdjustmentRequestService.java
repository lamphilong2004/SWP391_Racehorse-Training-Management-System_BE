package com.racehorse.training.adjustment;

import com.racehorse.notification.NotificationPublisher;
import com.racehorse.notification.NotificationApi.Category;
import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.common.jdbc.GeneratedIdentity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.racehorse.training.adjustment.AdjustmentApi.*;

@Service
public class AdjustmentRequestService {
    private static final Set<String> REQUEST_TYPES = Set.of("SOURCE_TEMPLATE", "PRIMARY_GOAL", "PHASE_STRUCTURE",
            "PHASE_OBJECTIVE", "PHASE_WORKLOAD_INTENSITY", "PHASE_TIMELINE", "TOTAL_DURATION", "OTHER_STRUCTURAL");
    private final JdbcTemplate jdbc;
    private final NotificationPublisher notifications;
    private final CurrentActorProvider actors;
    private final boolean sqlServer;

    public AdjustmentRequestService(JdbcTemplate jdbc, CurrentActorProvider actors, NotificationPublisher notifications) {
        this.notifications = notifications;
        this.jdbc = jdbc; this.actors = actors;
        String databaseProduct = jdbc.execute(
                (org.springframework.jdbc.core.ConnectionCallback<String>) connection ->
                        connection.getMetaData().getDatabaseProductName());
        if (databaseProduct == null || databaseProduct.isBlank()) {
            throw new IllegalStateException("Database product metadata is required.");
        }
        this.sqlServer = databaseProduct.toLowerCase(Locale.ROOT).contains("sql server");
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AdjustmentRequest create(long programId, CreateRequest request) {
        ActorContext actor = require(RoleCode.TRAINER);
        positive(programId);
        validate(request);
        Program program = program(programId);
        if (!isAssignedTrainer(programId, actor.userId())) throw forbidden("Program is outside the Trainer's active assignment.");
        if (request.horseProgramPhaseId() != null && !phaseBelongs(request.horseProgramPhaseId(), programId))
            throw notFound("Program phase was not found.");

        long id;
        try {
            id = insertId("INSERT INTO dbo.program_adjustment_request (horse_training_program_id, horse_program_phase_id, " +
                    "requested_by_user_id, request_type, current_value, requested_value, reason, evidence_notes, status, requested_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,'PENDING',?)", ps -> {
                ps.setLong(1, programId); setLong(ps, 2, request.horseProgramPhaseId()); ps.setLong(3, actor.userId());
                ps.setString(4, request.requestType()); ps.setString(5, request.currentValue()); ps.setString(6, request.requestedValue());
                ps.setString(7, request.reason()); ps.setString(8, request.evidenceNotes()); ps.setTimestamp(9, Timestamp.from(Instant.now()));
            });
        } catch (DataIntegrityViolationException ex) {
            throw conflict("The adjustment request could not be saved for this program.");
        }
        List<Long> recipients = jdbc.queryForList("SELECT u.id FROM dbo.app_user u JOIN dbo.user_role ur ON ur.user_id=u.id " +
                "JOIN dbo.role r ON r.id=ur.role_id WHERE u.account_status='ACTIVE' AND r.role_code='HEAD_TRAINER'", Long.class);
        for (long recipient : recipients) notifications.persistAndPublish(recipient, Category.SCHEDULE,
                "ADJUSTMENT_REQUEST_SUBMITTED", "Program adjustment requested",
                "A Trainer requested an adjustment for program " + programId + ".", "TRAINING_PROGRAM", programId, false);
        return request(id);
    }

    @Transactional(readOnly = true)
    public Page list(String status, Long programId, int page, int size) {
        ActorContext actor = actor();
        boolean headTrainer = actor.roles().contains(RoleCode.HEAD_TRAINER);
        if (!headTrainer && !actor.roles().contains(RoleCode.TRAINER)) throw forbidden("Adjustment request access denied.");
        if (page < 0 || size < 1 || size > 100) throw validation("page must be non-negative and size must be between 1 and 100.");
        if (programId != null && programId < 1) throw validation("programId must be positive.");
        if (status != null && !Set.of("PENDING", "APPROVED", "REJECTED").contains(status)) throw validation("status is not supported.");
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null) { where.append(" AND ar.status=?"); args.add(status); }
        if (programId != null) { where.append(" AND ar.horse_training_program_id=?"); args.add(programId); }
        if (!headTrainer) { where.append(" AND ar.requested_by_user_id=?"); args.add(actor.userId()); }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.program_adjustment_request ar" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add((long) page * size); pageArgs.add(size);
        List<Long> ids = jdbc.queryForList("SELECT ar.id FROM dbo.program_adjustment_request ar" + where +
                " ORDER BY ar.requested_at DESC,ar.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", Long.class, pageArgs.toArray());
        List<AdjustmentRequest> items = ids.stream().map(this::request).toList();
        return new Page(items, page, size, total, total == 0 ? 0 : (int) ((total + size - 1) / size));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AdjustmentRequest decide(long id, boolean approve, String reason) {
        ActorContext actor = require(RoleCode.HEAD_TRAINER);
        positive(id);
        if (!approve && (reason == null || reason.isBlank() || reason.length() > 2000))
            throw validation("decisionReason is required when rejecting a request.");
        if (approve && reason != null && reason.length() > 2000) throw validation("decisionReason must be at most 2000 characters.");
        String targetStatus = approve ? "APPROVED" : "REJECTED";
        int changed = jdbc.update("UPDATE dbo.program_adjustment_request SET status=?, reviewed_by_user_id=?, reviewed_at=?, " +
                "decision_reason=?, updated_at=SYSUTCDATETIME() WHERE id=? AND status='PENDING'",
                targetStatus, actor.userId(), Timestamp.from(Instant.now()), reason, id);
        if (changed == 0) {
            if (!exists(id)) throw notFound("Adjustment request was not found.");
            throw conflict("Only a PENDING adjustment request can be decided.");
        }
        AdjustmentRequest result = request(id);
        notifications.persistAndPublish(result.requestedBy().id(), Category.SCHEDULE, "ADJUSTMENT_REQUEST_DECIDED",
                "Program adjustment request decided", "Your adjustment request for program " + result.programId() + " was " + targetStatus + ".",
                "TRAINING_PROGRAM", result.programId(), false);
        return result;
    }

    private AdjustmentRequest request(long id) {
        List<AdjustmentRequest> rows = jdbc.query("""
                SELECT ar.id,ar.horse_training_program_id,ar.horse_program_phase_id,ar.request_type,ar.current_value,
                       ar.requested_value,ar.reason,ar.evidence_notes,ar.status,ar.requested_by_user_id,ru.user_code requested_code,
                       ru.full_name requested_name,ar.requested_at,ar.reviewed_by_user_id,vu.user_code reviewed_code,
                       vu.full_name reviewed_name,ar.reviewed_at,ar.decision_reason,p.horse_id,h.horse_code,h.name horse_name
                FROM dbo.program_adjustment_request ar
                JOIN dbo.horse_training_program p ON p.id=ar.horse_training_program_id
                JOIN dbo.horse h ON h.id=p.horse_id
                JOIN dbo.app_user ru ON ru.id=ar.requested_by_user_id
                LEFT JOIN dbo.app_user vu ON vu.id=ar.reviewed_by_user_id
                WHERE ar.id=?
                """, (rs, n) -> {
            Long phase = nullableLong(rs, "horse_program_phase_id"), reviewer = nullableLong(rs, "reviewed_by_user_id");
            return new AdjustmentRequest(rs.getLong("id"), rs.getLong("horse_training_program_id"), phase,
                    rs.getString("request_type"), rs.getString("current_value"), rs.getString("requested_value"),
                    rs.getString("reason"), rs.getString("evidence_notes"), rs.getString("status"),
                    new UserRef(rs.getLong("requested_by_user_id"), rs.getString("requested_code"), rs.getString("requested_name")),
                    rs.getTimestamp("requested_at").toInstant(), reviewer == null ? null : new UserRef(reviewer,
                    rs.getString("reviewed_code"), rs.getString("reviewed_name")), instant(rs.getTimestamp("reviewed_at")),
                    rs.getString("decision_reason"), new HorseRef(rs.getLong("horse_id"), rs.getString("horse_code"), rs.getString("horse_name")));
        }, id);
        if (rows.isEmpty()) throw notFound("Adjustment request was not found.");
        return rows.getFirst();
    }

    private Program program(long id) {
        List<Program> rows = jdbc.query("SELECT id,horse_id,status FROM dbo.horse_training_program WHERE id=?",
                (rs, n) -> new Program(rs.getLong(1), rs.getLong(2), rs.getString(3)), id);
        if (rows.isEmpty()) throw notFound("Training program was not found.");
        return rows.getFirst();
    }
    private boolean isAssignedTrainer(long program, long trainer) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.trainer_assignment WHERE horse_training_program_id=? " +
                "AND trainer_user_id=? AND effective_from<=CAST(CURRENT_TIMESTAMP AS DATE) AND (effective_to IS NULL OR effective_to>=CAST(CURRENT_TIMESTAMP AS DATE))",
                Integer.class, program, trainer);
        return count != null && count > 0;
    }
    private boolean phaseBelongs(long phase, long program) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.horse_program_phase WHERE id=? AND horse_training_program_id=?",
                Integer.class, phase, program);
        return count != null && count > 0;
    }
    private boolean exists(long id) { Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.program_adjustment_request WHERE id=?", Integer.class, id); return count != null && count > 0; }
    private long insertId(String sql, PreparedStatementSetter binder) {
        if (sqlServer) {
            String output = sql.replaceFirst("(?i)\\bVALUES\\b", "OUTPUT INSERTED.id VALUES");
            if (output.equals(sql)) throw new IllegalArgumentException("An identity insert must use a VALUES clause.");
            Long id = jdbc.query(output, binder, rs -> rs.next() ? rs.getLong(1) : null);
            if (id == null) throw new IllegalStateException("SQL Server did not return the adjustment request identity.");
            return id;
        }
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> { PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS); binder.setValues(ps); return ps; }, holder);
        return GeneratedIdentity.requireId(holder);
    }
    private static void setLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) ps.setNull(index, java.sql.Types.BIGINT); else ps.setLong(index, value);
    }
    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException { long value=rs.getLong(column); return rs.wasNull()?null:value; }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private void validate(CreateRequest request) {
        if (request == null || request.requestType() == null || !REQUEST_TYPES.contains(request.requestType()))
            throw validation("requestType is not supported; TARGET_RACE is deferred.");
        if (request.requestedValue() == null || request.requestedValue().isBlank() || request.requestedValue().length() > 1000
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 2000
                || request.currentValue() != null && request.currentValue().length() > 1000
                || request.evidenceNotes() != null && request.evidenceNotes().length() > 2000
                || request.horseProgramPhaseId() != null && request.horseProgramPhaseId() < 1)
            throw validation("Adjustment request fields are invalid.");
    }
    private ActorContext actor() { return actors.currentActor().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required.")); }
    private ActorContext require(RoleCode role) { ActorContext actor=actor(); if (!actor.roles().contains(role)) throw forbidden("Role is not allowed for this operation."); return actor; }
    private static void positive(long id) { if (id < 1) throw validation("id must be positive."); }
    private static BusinessException forbidden(String m) { return new BusinessException(ErrorCode.FORBIDDEN,m); }
    private static BusinessException notFound(String m) { return new BusinessException(ErrorCode.NOT_FOUND,m); }
    private static BusinessException validation(String m) { return new BusinessException(ErrorCode.VALIDATION_ERROR,m); }
    private static BusinessException conflict(String m) { return new BusinessException(ErrorCode.CONFLICT,m); }
    private record Program(long id,long horseId,String status) {}
}
