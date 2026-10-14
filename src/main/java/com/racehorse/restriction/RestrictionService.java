package com.racehorse.restriction;

import com.racehorse.audit.AuditService;
import com.racehorse.identity.RoleCode;
import com.racehorse.integration.Db;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DEMO IMPLEMENTATION of the Flow 3 restriction / availability operations (flow3-medical.openapi.yaml,
 * CORE-006 contract; implementation task owned by Đức). Availability is DERIVED, never stored:
 * no active restriction → AVAILABLE, LIMITED → LIMITED, LOCKED → LOCKED. At most one active restriction per
 * horse (competing create → 409). Lift sets lifted_at; rows are never deleted (BR-D07).
 */
@Service
public class RestrictionService {
    public record Rule(Long exerciseDefinitionId, String exerciseName, String ruleType, String exerciseTypeNote) {}
    public record Restriction(long id, long horseId, String level, String reason, Instant effectiveAt,
                              Instant liftedAt, LocalDate reviewDate, Long medicalCaseId, List<Rule> exerciseRules) {}
    public record Availability(long horseId, String availability, String reason, Long activeRestrictionId,
                               List<Rule> exerciseRules) {}
    public record CreateRequest(String level, String reason, Instant effectiveAt, LocalDate reviewDate,
                                Long medicalCaseId, List<Rule> exerciseRules) {}
    public record Threshold(String metricType, BigDecimal thresholdValue, String scope, Long horseId,
                            Instant effectiveFrom, Instant effectiveTo) {}

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final MonitoringThresholds thresholds;

    public RestrictionService(JdbcTemplate jdbc, AuditService audit, MonitoringThresholds thresholds) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.thresholds = thresholds;
    }

    @Transactional(readOnly = true)
    public Availability availability(long horseId) {
        requireHorse(horseId);
        return active(horseId)
                .map(r -> new Availability(horseId, r.level(), r.reason(), r.id(), r.exerciseRules()))
                .orElse(new Availability(horseId, "AVAILABLE", null, null, List.of()));
    }

    public java.util.Optional<Restriction> active(long horseId) {
        return jdbc.query("SELECT * FROM dbo.medical_restriction WHERE horse_id=? AND lifted_at IS NULL "
                + "ORDER BY effective_from DESC, id DESC", (r, n) -> row(r), horseId).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<Restriction> list(Long horseId, boolean activeOnly) {
        Db.require(RoleCode.CLUB_MANAGER, RoleCode.HEAD_TRAINER, RoleCode.TRAINER, RoleCode.VETERINARIAN);
        String sql = "SELECT * FROM dbo.medical_restriction WHERE 1=1"
                + (horseId != null ? " AND horse_id=" + horseId : "")
                + (activeOnly ? " AND lifted_at IS NULL" : "") + " ORDER BY effective_from DESC, id DESC";
        return jdbc.query(sql, (r, n) -> row(r));
    }

    @Transactional
    public Restriction create(long horseId, CreateRequest req) {
        var vet = Db.require(RoleCode.VETERINARIAN);
        if (req == null || req.level() == null || !List.of("LIMITED", "LOCKED").contains(req.level()))
            throw Db.invalid("level must be LIMITED or LOCKED.");
        if (req.reason() == null || req.reason().isBlank() || req.reason().length() > 1000)
            throw Db.invalid("reason is required (max 1000).");
        requireHorse(horseId);
        jdbc.queryForList("SELECT id FROM dbo.horse WITH (UPDLOCK, HOLDLOCK) WHERE id=?", horseId);
        if (active(horseId).isPresent())
            throw Db.conflict("The horse already has an active restriction. Lift it before setting a new one.");
        if (req.medicalCaseId() != null) {
            Integer ok = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.medical_case WHERE id=? AND horse_id=? AND status='OPEN'",
                    Integer.class, req.medicalCaseId(), horseId);
            if (ok == null || ok == 0) throw Db.invalid("medicalCaseId must be an OPEN case of this horse.");
        }
        Instant effective = req.effectiveAt() == null ? Instant.now() : req.effectiveAt();
        long id = Db.insert(jdbc, "INSERT INTO dbo.medical_restriction (horse_id, medical_case_id, set_by_user_id, "
                        + "restriction_level, reason, effective_from, review_date, created_at) VALUES (?,?,?,?,?,?,?,?)",
                horseId, req.medicalCaseId(), vet.id(), req.level(), req.reason(), Db.utc(effective),
                req.reviewDate(), Db.nowUtc());
        if (req.exerciseRules() != null) replaceRules(id, req.exerciseRules());
        audit.record("MEDICAL_RESTRICTION_SET", "medical_restriction", id, "level=" + req.level() + " horse=" + horseId);
        notifyTraining(horseId, "Training availability changed to " + req.level(), req.reason());
        return get(id);
    }

    @Transactional
    public Restriction lift(long restrictionId) {
        Db.require(RoleCode.VETERINARIAN);
        Restriction r = get(restrictionId);
        if (r.liftedAt() != null) throw Db.conflict("Restriction is already lifted.");
        jdbc.update("UPDATE dbo.medical_restriction SET lifted_at=?, updated_at=? WHERE id=?",
                Db.nowUtc(), Db.nowUtc(), restrictionId);
        audit.record("MEDICAL_RESTRICTION_LIFTED", "medical_restriction", restrictionId, "horse=" + r.horseId());
        notifyTraining(r.horseId(), "Training availability changed to AVAILABLE", "Restriction lifted by the Veterinarian.");
        return get(restrictionId);
    }

    @Transactional
    public Restriction putRules(long restrictionId, List<Rule> rules) {
        Db.require(RoleCode.VETERINARIAN);
        Restriction r = get(restrictionId);
        if (r.liftedAt() != null) throw Db.conflict("Restriction is lifted.");
        replaceRules(restrictionId, rules == null ? List.of() : rules);
        return get(restrictionId);
    }

    public Restriction get(long id) {
        return jdbc.query("SELECT * FROM dbo.medical_restriction WHERE id=?", (r, n) -> row(r), id).stream()
                .findFirst().orElseThrow(() -> Db.notFound("Restriction"));
    }

    @Transactional(readOnly = true)
    public List<Threshold> thresholds(Long horseId) {
        Db.require(RoleCode.HEAD_TRAINER, RoleCode.TRAINER, RoleCode.VETERINARIAN);
        var global = new Threshold("HEART_RATE", thresholds.globalCritical(), "GLOBAL", null, null, null);
        if (horseId == null) return List.of(global);
        return thresholds.override(horseId)
                .map(o -> List.of(global, new Threshold("HEART_RATE", o, "HORSE_OVERRIDE", horseId, null, null)))
                .orElse(List.of(global));
    }

    private void replaceRules(long restrictionId, List<Rule> rules) {
        // History-sensitive: rules of a restriction are replaced only while it is active (vet edit).
        jdbc.update("DELETE FROM dbo.restriction_exercise_rule WHERE medical_restriction_id=?", restrictionId);
        for (Rule rule : rules) {
            if (rule.ruleType() == null || !List.of("ALLOWED", "PROHIBITED").contains(rule.ruleType()))
                throw Db.invalid("ruleType must be ALLOWED or PROHIBITED.");
            if (rule.exerciseDefinitionId() == null && (rule.exerciseTypeNote() == null || rule.exerciseTypeNote().isBlank()))
                throw Db.invalid("Each rule needs exerciseDefinitionId or exerciseTypeNote.");
            jdbc.update("INSERT INTO dbo.restriction_exercise_rule (medical_restriction_id, exercise_definition_id, "
                            + "rule_type, exercise_type_note, created_at) VALUES (?,?,?,?,?)",
                    restrictionId, rule.exerciseDefinitionId(), rule.ruleType(), rule.exerciseTypeNote(), Db.nowUtc());
        }
    }

    private List<Rule> rules(long restrictionId) {
        return jdbc.query("SELECT r.exercise_definition_id, e.name, r.rule_type, r.exercise_type_note "
                        + "FROM dbo.restriction_exercise_rule r LEFT JOIN dbo.exercise_definition e ON e.id=r.exercise_definition_id "
                        + "WHERE r.medical_restriction_id=? ORDER BY r.id",
                (r, n) -> new Rule(Db.lng(r, "exercise_definition_id"), r.getString("name"), r.getString("rule_type"),
                        r.getString("exercise_type_note")), restrictionId);
    }

    private Restriction row(java.sql.ResultSet r) throws java.sql.SQLException {
        long id = r.getLong("id");
        java.sql.Date review = r.getDate("review_date");
        return new Restriction(id, r.getLong("horse_id"), r.getString("restriction_level"), r.getString("reason"),
                Db.instant(r, "effective_from"), Db.instant(r, "lifted_at"),
                review == null ? null : review.toLocalDate(), Db.lng(r, "medical_case_id"), rules(id));
    }

    private void requireHorse(long horseId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.horse WHERE id=?", Integer.class, horseId);
        if (c == null || c == 0) throw Db.notFound("Horse");
    }

    /** Notifies the Trainer(s) of the horse's active programs (RESTRICTION_CHANGED, shared contract). */
    private void notifyTraining(long horseId, String title, String message) {
        List<Map<String, Object>> trainers = jdbc.queryForList("SELECT DISTINCT a.trainer_user_id FROM dbo.trainer_assignment a "
                + "JOIN dbo.horse_training_program p ON p.id=a.horse_training_program_id "
                + "WHERE p.horse_id=? AND a.effective_to IS NULL", horseId);
        for (var t : trainers) {
            jdbc.update("INSERT INTO dbo.notification (recipient_user_id, category, notification_type, title, message, "
                            + "target_type, target_id, is_simulated, created_at) VALUES (?,?,?,?,?,?,?,0,?)",
                    Db.lng(t, "trainer_user_id"), "MEDICAL", "RESTRICTION_CHANGED", title,
                    message == null ? "" : message.substring(0, Math.min(message.length(), 1000)), "horse", horseId, Db.nowUtc());
        }
    }
}
