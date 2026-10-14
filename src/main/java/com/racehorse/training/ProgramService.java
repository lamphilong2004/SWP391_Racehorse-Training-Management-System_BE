package com.racehorse.training;

import com.racehorse.audit.AuditService;
import com.racehorse.auth.AuthenticatedUser;
import com.racehorse.identity.RoleCode;
import com.racehorse.integration.Db;
import com.racehorse.restriction.RestrictionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DEMO IMPLEMENTATION of Flow 2 program side (CORE-005 contract; tasks CORE-021/022 owned by Tiến):
 * Program Template Library (read), template/trainer recommendations (labels + reasons, no scores — BR-D28/29),
 * Horse Training Program creation from a pinned template version (DEC-005), exercises, progress.
 * The target race is never exposed (DEC-007 §3.3): target_race_id stays NULL; target text lives in goals.
 */
@Service
public class ProgramService {
    public static final List<String> INTENSITY = List.of("LOW", "MEDIUM", "MEDIUM_HIGH", "HIGH");

    public record UserRef(long id, String userCode, String fullName) {}
    public record TemplatePhase(long id, int phaseOrder, String name, String objective, String recommendedDuration,
                                String intensityGuidance) {}
    public record Template(long id, String templateCode, String status, long templateVersionId, int versionNo,
                           String name, String goals, String suitableHorseProfile, String recommendedDuration,
                           String workloadIntensityGuidance, String preferredSurfaces, List<TemplatePhase> phases,
                           List<String> requiredSkills) {}
    public record Recommendation(Object item, String label, List<String> reasons, List<String> warnings, boolean hardBlock) {}
    public record Exercise(long id, String name, String exerciseType, String description, String targetMetric) {}
    public record PhaseInput(Long templateVersionPhaseId, Integer phaseOrder, String name, String objective,
                             LocalDate startDate, LocalDate endDate, String intensityMin, String intensityMax, String workload) {}
    public record CreateProgramRequest(Long horseId, Long templateVersionId, String name, String goals, String targetMetrics,
                                       LocalDate periodStart, LocalDate periodEnd, String notes, Long trainerUserId,
                                       List<PhaseInput> phases) {}
    public record Phase(long id, int phaseOrder, String name, String objective, LocalDate startDate, LocalDate endDate,
                        String intensityMin, String intensityMax, String workload, boolean isCustomized, int sessionCount) {}
    public record Program(long id, Map<String, Object> horse, String name, String goals, String targetMetrics,
                          LocalDate periodStart, LocalDate periodEnd, String status, String notes, Instant activatedAt,
                          Map<String, Object> sourceTemplate, UserRef trainer, UserRef createdBy, List<Phase> phases,
                          Map<String, Object> progress, String trainingAvailability) {}

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final RestrictionService restrictions;

    public ProgramService(JdbcTemplate jdbc, AuditService audit, RestrictionService restrictions) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.restrictions = restrictions;
    }

    // -------- templates / recommendations / exercises --------

    @Transactional(readOnly = true)
    public List<Template> templates(boolean includeArchived) {
        Db.require(RoleCode.HEAD_TRAINER, RoleCode.TRAINER);
        return jdbc.queryForList("SELECT id FROM dbo.training_program_template"
                + (includeArchived ? "" : " WHERE status='ACTIVE'") + " ORDER BY template_code", Long.class)
                .stream().map(this::template).toList();
    }

    @Transactional(readOnly = true)
    public Template templateDetail(long id) {
        Db.require(RoleCode.HEAD_TRAINER, RoleCode.TRAINER);
        return template(id);
    }

    @Transactional(readOnly = true)
    public List<Recommendation> templateRecommendations(long horseId) {
        Db.require(RoleCode.HEAD_TRAINER);
        String availability = restrictions.availability(horseId).availability();
        return templates(false).stream().map(t -> {
            List<String> reasons = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            String label = "SUITABLE";
            if (t.name().toLowerCase().contains("race")) { label = "RECOMMENDED"; reasons.add("Matches race preparation goal"); }
            if (t.name().toLowerCase().contains("recovery")) {
                label = "LIMITED".equals(availability) ? "RECOMMENDED" : "SUITABLE";
                reasons.add("Low-intensity reconditioning");
            }
            if ("LIMITED".equals(availability) && !t.name().toLowerCase().contains("recovery")) {
                label = "SUITABLE_WITH_CAUTION";
                warnings.add("Horse is LIMITED — each exercise is checked against the Veterinarian restriction");
            }
            if (reasons.isEmpty()) reasons.add("Active club template");
            return new Recommendation(t, label, reasons, warnings, false);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Recommendation> trainerRecommendations(long horseId, Long templateId) {
        Db.require(RoleCode.HEAD_TRAINER);
        List<String> required = templateId == null ? List.of() : template(templateId).requiredSkills();
        return usersWithRole("TRAINER").stream().map(u -> {
            List<String> skills = jdbc.queryForList("SELECT s.name FROM dbo.user_skill us JOIN dbo.skill s ON s.id=us.skill_id "
                    + "WHERE us.user_id=? AND s.kind='TRAINER_SPECIALIZATION'", String.class, u.id());
            Integer load = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.trainer_assignment a JOIN dbo.horse_training_program p "
                    + "ON p.id=a.horse_training_program_id WHERE a.trainer_user_id=? AND a.effective_to IS NULL AND p.status='ACTIVE'",
                    Integer.class, u.id());
            List<String> reasons = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            boolean match = !required.isEmpty() && skills.stream().anyMatch(required::contains);
            if (match) reasons.add("Specialization matches the template: " + String.join(", ", skills));
            else if (!skills.isEmpty()) reasons.add("Specializations: " + String.join(", ", skills));
            reasons.add("Current active programs: " + load);
            if (load != null && load >= 5) warnings.add("High current workload");
            String label = match ? "RECOMMENDED" : warnings.isEmpty() ? "SUITABLE" : "SUITABLE_WITH_CAUTION";
            return new Recommendation(u, label, reasons, warnings, false);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Exercise> exercises() {
        Db.require(RoleCode.HEAD_TRAINER, RoleCode.TRAINER, RoleCode.VETERINARIAN);
        return jdbc.query("SELECT * FROM dbo.exercise_definition ORDER BY name", (r, n) -> new Exercise(r.getLong("id"),
                r.getString("name"), r.getString("exercise_type"), r.getString("description"), r.getString("target_metric")));
    }

    public List<UserRef> usersWithRole(String role) {
        return jdbc.query("SELECT u.id, u.user_code, u.full_name FROM dbo.app_user u JOIN dbo.user_role ur ON ur.user_id=u.id "
                + "JOIN dbo.role r ON r.id=ur.role_id WHERE r.role_code=? AND u.account_status='ACTIVE' ORDER BY u.full_name",
                (r, n) -> new UserRef(r.getLong(1), r.getString(2), r.getString(3)), role);
    }

    // -------- programs --------

    @Transactional
    public Program create(CreateProgramRequest req) {
        AuthenticatedUser ht = Db.require(RoleCode.HEAD_TRAINER);
        if (req == null || req.horseId() == null || req.templateVersionId() == null || req.trainerUserId() == null
                || req.name() == null || req.name().isBlank() || req.periodStart() == null || req.periodEnd() == null)
            throw Db.invalid("horseId, templateVersionId, trainerUserId, name, periodStart and periodEnd are required.");
        if (req.periodEnd().isBefore(req.periodStart())) throw Db.invalid("periodEnd must not be before periodStart (BR-19).");
        var horse = jdbc.queryForList("SELECT lifecycle FROM dbo.horse WITH (UPDLOCK) WHERE id=?", String.class, req.horseId());
        if (horse.isEmpty()) throw Db.notFound("Horse");
        if (!"ACTIVE".equals(horse.getFirst())) throw Db.rule("The horse must be ACTIVE before a program is created (BR-12).");
        var availability = restrictions.availability(req.horseId());
        if ("LOCKED".equals(availability.availability()))
            throw Db.rule("Training is LOCKED by the Veterinarian: " + availability.reason());
        var tv = jdbc.queryForList("SELECT v.id, t.status FROM dbo.template_version v JOIN dbo.training_program_template t "
                + "ON t.id=v.training_program_template_id WHERE v.id=?", req.templateVersionId());
        if (tv.isEmpty()) throw Db.notFound("Template version");
        if (!"ACTIVE".equals(tv.getFirst().get("status"))) throw Db.rule("The template is ARCHIVED.");
        if (usersWithRole("TRAINER").stream().noneMatch(u -> u.id() == req.trainerUserId()))
            throw Db.invalid("trainerUserId must be an ACTIVE Trainer.");
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.horse_training_program WHERE horse_id=? AND status='ACTIVE'",
                Integer.class, req.horseId());
        if (active != null && active > 0) throw Db.conflict("The horse already has an ACTIVE training program.");
        List<PhaseInput> phases = req.phases() == null || req.phases().isEmpty() ? defaultPhases(req) : req.phases();
        LocalDate prevEnd = null;
        for (PhaseInput p : phases) {
            if (p.name() == null || p.startDate() == null || p.endDate() == null || p.endDate().isBefore(p.startDate()))
                throw Db.invalid("Each phase needs a name and a valid date range.");
            if (p.startDate().isBefore(req.periodStart()) || p.endDate().isAfter(req.periodEnd()))
                throw Db.invalid("Phases must lie inside the program period.");
            if (prevEnd != null && !p.startDate().isAfter(prevEnd)) throw Db.invalid("Phases must not overlap.");
            checkRange(p.intensityMin(), p.intensityMax());
            prevEnd = p.endDate();
        }
        long id = Db.insert(jdbc, "INSERT INTO dbo.horse_training_program (horse_id, template_version_id, target_race_id, "
                        + "created_by_user_id, name, goals, target_metrics, period_start, period_end, status, notes, activated_at, created_at) "
                        + "VALUES (?,?,NULL,?,?,?,?,?,?,'ACTIVE',?,?,?)", req.horseId(), req.templateVersionId(), ht.id(),
                req.name().trim(), req.goals(), req.targetMetrics(), req.periodStart(), req.periodEnd(), req.notes(),
                Db.nowUtc(), Db.nowUtc());
        int order = 1;
        for (PhaseInput p : phases) {
            Db.insert(jdbc, "INSERT INTO dbo.horse_program_phase (horse_training_program_id, template_version_phase_id, phase_order, "
                            + "name, objective, start_date, end_date, intensity_min, intensity_max, workload, is_customized, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", id, p.templateVersionPhaseId(), order++, p.name(), p.objective(),
                    p.startDate(), p.endDate(), p.intensityMin(), p.intensityMax(), p.workload(),
                    p.templateVersionPhaseId() == null ? 1 : 0, Db.nowUtc());
        }
        jdbc.update("INSERT INTO dbo.trainer_assignment (horse_training_program_id, trainer_user_id, assigned_by_user_id, "
                + "effective_from, reason, created_at) VALUES (?,?,?,?,?,?)", id, req.trainerUserId(), ht.id(),
                req.periodStart().isAfter(LocalDate.now()) ? LocalDate.now() : req.periodStart(), "Initial assignment", Db.nowUtc());
        notify(req.trainerUserId(), "SCHEDULE", "PROGRAM_ASSIGNED", "New training program assigned",
                req.name() + " has been assigned to you.", "horse_training_program", id);
        audit.record("PROGRAM_CREATED", "horse_training_program", id, "horse=" + req.horseId() + " trainer=" + req.trainerUserId());
        return program(id);
    }

    @Transactional(readOnly = true)
    public List<Program> list(Long horseId, boolean assignedToMe) {
        AuthenticatedUser u = Db.require(RoleCode.HEAD_TRAINER, RoleCode.TRAINER, RoleCode.CLUB_MANAGER, RoleCode.VETERINARIAN);
        List<Object> args = new ArrayList<>();
        String where = " WHERE 1=1";
        if (horseId != null) { where += " AND p.horse_id=?"; args.add(horseId); }
        if (assignedToMe || (u.hasRole(RoleCode.TRAINER) && !u.hasRole(RoleCode.HEAD_TRAINER))) {
            where += " AND EXISTS (SELECT 1 FROM dbo.trainer_assignment a WHERE a.horse_training_program_id=p.id "
                    + "AND a.trainer_user_id=? AND a.effective_to IS NULL)";
            args.add(u.id());
        }
        return jdbc.queryForList("SELECT p.id FROM dbo.horse_training_program p" + where + " ORDER BY p.created_at DESC, p.id DESC",
                Long.class, args.toArray()).stream().map(this::program).toList();
    }

    @Transactional(readOnly = true)
    public Program detail(long id) {
        AuthenticatedUser u = Db.require(RoleCode.HEAD_TRAINER, RoleCode.TRAINER, RoleCode.CLUB_MANAGER, RoleCode.VETERINARIAN);
        if (u.hasRole(RoleCode.TRAINER) && !u.hasRole(RoleCode.HEAD_TRAINER) && !isAssignedTrainer(id, u.id()))
            throw new com.racehorse.common.error.BusinessException(com.racehorse.common.error.ErrorCode.FORBIDDEN,
                    "The program is not assigned to you.");
        return program(id);
    }

    public boolean isAssignedTrainer(long programId, long userId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.trainer_assignment WHERE horse_training_program_id=? "
                + "AND trainer_user_id=? AND effective_to IS NULL", Integer.class, programId, userId);
        return c != null && c > 0;
    }

    public Program program(long id) {
        var rows = jdbc.queryForList("SELECT p.*, h.horse_code, h.name AS horse_name, v.version_no, t.template_code, v.name AS template_name, "
                + "v.training_program_template_id, cb.user_code AS cb_code, cb.full_name AS cb_name FROM dbo.horse_training_program p "
                + "JOIN dbo.horse h ON h.id=p.horse_id JOIN dbo.template_version v ON v.id=p.template_version_id "
                + "JOIN dbo.training_program_template t ON t.id=v.training_program_template_id "
                + "JOIN dbo.app_user cb ON cb.id=p.created_by_user_id WHERE p.id=?", id);
        if (rows.isEmpty()) throw Db.notFound("Training program");
        var r = rows.getFirst();
        List<UserRef> trainer = jdbc.query("SELECT TOP 1 u.id, u.user_code, u.full_name FROM dbo.trainer_assignment a JOIN dbo.app_user u "
                        + "ON u.id=a.trainer_user_id WHERE a.horse_training_program_id=? AND a.effective_to IS NULL ORDER BY a.id DESC",
                (t, n) -> new UserRef(t.getLong(1), t.getString(2), t.getString(3)), id);
        List<Phase> phases = jdbc.query("SELECT ph.*, (SELECT COUNT(*) FROM dbo.training_session s WHERE s.horse_program_phase_id=ph.id) AS sc "
                + "FROM dbo.horse_program_phase ph WHERE ph.horse_training_program_id=? ORDER BY ph.phase_order", (p, n) -> new Phase(
                p.getLong("id"), p.getInt("phase_order"), p.getString("name"), p.getString("objective"),
                p.getDate("start_date").toLocalDate(), p.getDate("end_date").toLocalDate(), p.getString("intensity_min"),
                p.getString("intensity_max"), p.getString("workload"), p.getBoolean("is_customized"), p.getInt("sc")), id);
        Map<String, Object> counts = jdbc.queryForMap("SELECT COUNT(*) AS total, "
                + "SUM(CASE WHEN s.status='COMPLETED' THEN 1 ELSE 0 END) AS completed, "
                + "SUM(CASE WHEN s.status='INTERRUPTED' THEN 1 ELSE 0 END) AS interrupted, "
                + "SUM(CASE WHEN s.status IN ('SCHEDULED','IN_PROGRESS','PAUSED') THEN 1 ELSE 0 END) AS upcoming "
                + "FROM dbo.training_session s JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id "
                + "WHERE ph.horse_training_program_id=?", id);
        Integer evaluated = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.session_result sr JOIN dbo.training_session s ON s.id=sr.training_session_id "
                + "JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id WHERE ph.horse_training_program_id=?", Integer.class, id);
        counts.put("evaluated", evaluated);
        long horseId = Db.lng(r, "horse_id");
        return new Program(id, Map.of("id", horseId, "horseCode", r.get("horse_code"), "name", r.get("horse_name")),
                (String) r.get("name"), (String) r.get("goals"), (String) r.get("target_metrics"),
                ((java.sql.Date) r.get("period_start")).toLocalDate(), ((java.sql.Date) r.get("period_end")).toLocalDate(),
                (String) r.get("status"), (String) r.get("notes"), Db.instant(r.get("activated_at")),
                Map.of("templateId", r.get("training_program_template_id"), "templateCode", r.get("template_code"),
                        "templateVersionId", r.get("template_version_id"), "versionNo", r.get("version_no"), "name", r.get("template_name")),
                trainer.isEmpty() ? null : trainer.getFirst(),
                new UserRef(Db.lng(r, "created_by_user_id"), (String) r.get("cb_code"), (String) r.get("cb_name")), phases, counts,
                restrictions.availability(horseId).availability());
    }

    // -------- helpers --------

    private Template template(long id) {
        var rows = jdbc.queryForList("SELECT t.template_code, t.status, v.id AS version_id, v.version_no, v.name, v.goals, "
                + "v.suitable_horse_profile, v.recommended_duration, v.workload_intensity_guidance, v.preferred_surfaces "
                + "FROM dbo.training_program_template t JOIN dbo.template_version v ON v.training_program_template_id=t.id "
                + "WHERE t.id=? AND v.version_no=(SELECT MAX(version_no) FROM dbo.template_version WHERE training_program_template_id=t.id)", id);
        if (rows.isEmpty()) throw Db.notFound("Program template");
        var r = rows.getFirst();
        long versionId = ((Number) r.get("version_id")).longValue();
        List<TemplatePhase> phases = jdbc.query("SELECT * FROM dbo.template_version_phase WHERE template_version_id=? ORDER BY phase_order",
                (p, n) -> new TemplatePhase(p.getLong("id"), p.getInt("phase_order"), p.getString("name"), p.getString("objective"),
                        p.getString("recommended_duration"), p.getString("intensity_guidance")), versionId);
        List<String> skills = jdbc.queryForList("SELECT s.name FROM dbo.template_required_skill rs JOIN dbo.skill s ON s.id=rs.skill_id "
                + "WHERE rs.template_version_id=?", String.class, versionId);
        return new Template(id, (String) r.get("template_code"), (String) r.get("status"), versionId,
                ((Number) r.get("version_no")).intValue(), (String) r.get("name"), (String) r.get("goals"),
                (String) r.get("suitable_horse_profile"), (String) r.get("recommended_duration"),
                (String) r.get("workload_intensity_guidance"), (String) r.get("preferred_surfaces"), phases, skills);
    }

    private List<PhaseInput> defaultPhases(CreateProgramRequest req) {
        List<Map<String, Object>> tp = jdbc.queryForList("SELECT * FROM dbo.template_version_phase WHERE template_version_id=? "
                + "ORDER BY phase_order", req.templateVersionId());
        if (tp.isEmpty()) throw Db.invalid("phases are required (the template has none).");
        long days = java.time.temporal.ChronoUnit.DAYS.between(req.periodStart(), req.periodEnd()) + 1;
        long per = Math.max(1, days / tp.size());
        List<PhaseInput> out = new ArrayList<>();
        LocalDate start = req.periodStart();
        for (int i = 0; i < tp.size(); i++) {
            var p = tp.get(i);
            LocalDate end = i == tp.size() - 1 ? req.periodEnd() : start.plusDays(per - 1);
            out.add(new PhaseInput(Db.lng(p, "id"), i + 1, (String) p.get("name"), (String) p.get("objective"), start, end,
                    "LOW", i == 0 ? "MEDIUM" : "HIGH", (String) p.get("recommended_duration")));
            start = end.plusDays(1);
        }
        return out;
    }

    static void checkRange(String min, String max) {
        if (min != null && !INTENSITY.contains(min)) throw Db.invalid("intensityMin must be one of " + INTENSITY);
        if (max != null && !INTENSITY.contains(max)) throw Db.invalid("intensityMax must be one of " + INTENSITY);
        if (min != null && max != null && INTENSITY.indexOf(min) > INTENSITY.indexOf(max))
            throw Db.invalid("intensityMin must not exceed intensityMax.");
    }

    void notify(long userId, String category, String type, String title, String message, String targetType, Long targetId) {
        jdbc.update("INSERT INTO dbo.notification (recipient_user_id, category, notification_type, title, message, target_type, "
                + "target_id, is_simulated, created_at) VALUES (?,?,?,?,?,?,?,0,?)", userId, category, type, title,
                message.length() > 1000 ? message.substring(0, 1000) : message, targetType, targetId, Db.nowUtc());
    }
}
