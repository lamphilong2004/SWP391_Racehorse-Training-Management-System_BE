package com.racehorse.training.program;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.training.program.ProgramApi.CreateProgramRequest;
import com.racehorse.training.program.ProgramApi.ProgramDetail;
import com.racehorse.training.program.ProgramApi.ProgramPhase;
import com.racehorse.training.program.ProgramApi.ProgramPhaseInput;
import com.racehorse.training.program.ProgramApi.ProgramSummary;
import com.racehorse.training.program.ProgramApi.TemplateDetail;
import com.racehorse.training.program.ProgramApi.TemplatePhase;
import com.racehorse.training.program.ProgramApi.TemplatePhaseInput;
import com.racehorse.training.program.ProgramApi.TemplateSummary;
import com.racehorse.training.program.ProgramApi.TemplateVersion;
import com.racehorse.training.program.ProgramApi.TemplateVersionRequest;
import com.racehorse.training.program.ProgramApi.TrainerAssignmentRequest;
import com.racehorse.training.program.ProgramApi.UserRef;
import com.racehorse.training.program.ProgramApi.HorseRef;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class TrainingProgramService {

    private static final Set<String> PROGRAM_STATUSES = Set.of("ACTIVE", "PAUSED", "COMPLETED");
    private static final Set<String> INTENSITIES = Set.of("LOW", "MEDIUM", "MEDIUM_HIGH", "HIGH");

    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actorProvider;

    public TrainingProgramService(JdbcTemplate jdbc, CurrentActorProvider actorProvider) {
        this.jdbc = jdbc;
        this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public List<ProgramApi.TemplateRecommendation> templateRecommendations(long horseId) {
        requireRole(RoleCode.HEAD_TRAINER);
        if (horseId <= 0) throw validation("Horse id must be positive.");
        List<Map<String,Object>> horses=jdbc.query("SELECT breed,sex FROM dbo.horse WHERE id=?",(rs,n)->row("breed",rs.getString("breed"),"sex",rs.getString("sex")),horseId);
        if (horses.isEmpty()) throw notFound("Horse was not found.");
        Map<String,Object> horse=horses.getFirst();
        List<String> fields=new ArrayList<>();
        if (horse.get("breed")!=null&&!horse.get("breed").toString().isBlank()) fields.add("breed");
        if (horse.get("sex")!=null&&!horse.get("sex").toString().isBlank()) fields.add("sex");
        if (fields.isEmpty()) return List.of();
        List<Map<String,Object>> templates=jdbc.query("""
                SELECT t.id,t.template_code,v.id AS version_id,v.version_no,v.name,v.suitable_horse_profile,
                       h.breed,h.sex
                FROM dbo.training_program_template t
                JOIN dbo.template_version v ON v.training_program_template_id=t.id
                  AND v.version_no=(SELECT MAX(v2.version_no) FROM dbo.template_version v2 WHERE v2.training_program_template_id=t.id)
                CROSS JOIN dbo.horse h
                WHERE t.status='ACTIVE' AND h.id=?
                """,(rs,n)->row("templateId",rs.getLong("id"),"templateCode",rs.getString("template_code"),
                "versionId",rs.getLong("version_id"),"versionNo",rs.getInt("version_no"),"name",rs.getString("name"),
                "profile",rs.getString("suitable_horse_profile"),"breed",rs.getString("breed"),"sex",rs.getString("sex")),horseId);
        List<ProgramApi.TemplateRecommendation> out=new ArrayList<>();
        for(Map<String,Object> template:templates){
            String profile=(String)template.get("profile");if(profile==null||profile.isBlank())continue;
            List<String> reasons=new ArrayList<>();
            for(String field:fields){Object value=horse.get(field);if(value!=null&&profile.toLowerCase(java.util.Locale.ROOT).contains(value.toString().toLowerCase(java.util.Locale.ROOT)))
                reasons.add("Horse "+field+" matches the template's suitableHorseProfile.");}
            if(!reasons.isEmpty())out.add(new ProgramApi.TemplateRecommendation((Long)template.get("templateId"),(String)template.get("templateCode"),
                    (Long)template.get("versionId"),(Integer)template.get("versionNo"),(String)template.get("name"),"SUITABLE",List.copyOf(reasons)));
        }
        return List.copyOf(out);
    }

    @Transactional(readOnly = true)
    public ProgramApi.CandidateList trainerRecommendations(long horseId,long templateVersionId) {
        requireRole(RoleCode.HEAD_TRAINER);
        if(horseId<=0||templateVersionId<=0)throw validation("Horse id and templateVersionId must be positive.");
        Integer horse=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.horse WHERE id=?",Integer.class,horseId);
        if(horse==null||horse==0)throw notFound("Horse was not found.");
        Integer version=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.template_version v JOIN dbo.training_program_template t ON t.id=v.training_program_template_id WHERE v.id=? AND t.status='ACTIVE'",Integer.class,templateVersionId);
        if(version==null||version==0)throw notFound("An active template version was not found.");
        List<ProgramApi.SkillRef> required=jdbc.query("SELECT s.id,s.name FROM dbo.template_required_skill r JOIN dbo.skill s ON s.id=r.skill_id WHERE r.template_version_id=?",(rs,n)->new ProgramApi.SkillRef(rs.getLong("id"),rs.getString("name")),templateVersionId);
        if(required.isEmpty())return new ProgramApi.CandidateList(List.of(),"The selected template version has no required skills for profile matching.");
        String sql="""
                SELECT u.id,u.user_code,u.full_name,u.experience_level,s.id AS skill_id,s.name AS skill_name
                FROM dbo.app_user u
                JOIN dbo.user_role ur ON ur.user_id=u.id JOIN dbo.role ro ON ro.id=ur.role_id AND ro.role_code='TRAINER'
                JOIN dbo.user_skill us ON us.user_id=u.id JOIN dbo.skill s ON s.id=us.skill_id
                JOIN dbo.template_required_skill rs ON rs.skill_id=s.id AND rs.template_version_id=?
                WHERE u.account_status='ACTIVE' AND s.kind='TRAINER_SPECIALIZATION'
                """;
        Map<Long,Map<String,Object>> users=new LinkedHashMap<>();Map<Long,List<String>> matched=new LinkedHashMap<>();
        jdbc.query(sql,rs->{long id=rs.getLong("id");users.putIfAbsent(id,row("id",id,"userCode",rs.getString("user_code"),"name",rs.getString("full_name"),"experience",rs.getString("experience_level")));matched.computeIfAbsent(id,k->new ArrayList<>()).add(rs.getString("skill_name"));},templateVersionId);
        List<ProgramApi.RecommendationCandidate> candidates=new ArrayList<>();
        for(var entry:users.entrySet()){Map<String,Object> u=entry.getValue();List<String> skills=List.copyOf(new java.util.LinkedHashSet<>(matched.get(entry.getKey())));
            List<String> reasons=skills.stream().map(s->"Trainer specialization matches a required template skill: "+s+".").toList();
            candidates.add(new ProgramApi.RecommendationCandidate(new UserRef(entry.getKey(),(String)u.get("userCode"),(String)u.get("name")),
                    (String)u.get("experience"),skills,"SUITABLE",reasons,List.of(),null));}
        String empty=candidates.isEmpty()?"No active Trainer has a profile matching the template's required skills.":null;
        return new ProgramApi.CandidateList(List.copyOf(candidates),empty);
    }

    @Transactional(readOnly = true)
    public ProgramApi.Page<TemplateSummary> listTemplates(String status, String q, int page, int size) {
        ActorContext actor = actor();
        validatePage(page, size);
        if (status != null && !Set.of("ACTIVE", "ARCHIVED").contains(status)) {
            throw validation("status must be ACTIVE or ARCHIVED.");
        }
        if (q != null && q.length() > 100) throw validation("q must not exceed 100 characters.");
        boolean trainer = actor.roles().contains(RoleCode.TRAINER);
        String trainerScope = trainer ? " AND EXISTS (SELECT 1 FROM dbo.horse_training_program hp JOIN dbo.trainer_assignment ta ON ta.horse_training_program_id = hp.id WHERE hp.template_version_id = v.id AND ta.trainer_user_id = ? AND ta.effective_to IS NULL)" : "";
        String base = " FROM dbo.training_program_template t JOIN dbo.template_version v ON v.training_program_template_id=t.id AND v.version_no=(SELECT MAX(v2.version_no) FROM dbo.template_version v2 WHERE v2.training_program_template_id=t.id) WHERE (? IS NULL OR t.status=?) AND (? IS NULL OR UPPER(v.name) LIKE UPPER(?))" + trainerScope;
        List<Object> args = new ArrayList<>();
        args.add(status); args.add(status); args.add(q); args.add(q == null ? null : "%" + q + "%");
        if (trainer) args.add(actor.userId());
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + base, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(page * size); pageArgs.add(size);
        List<TemplateSummary> items = jdbc.query("""
                SELECT t.id, t.template_code, t.status, t.duplicated_from_template_id,
                       v.version_no, v.name, v.goals,
                       (SELECT COUNT(*) FROM dbo.horse_training_program hp WHERE hp.template_version_id IN
                          (SELECT vx.id FROM dbo.template_version vx WHERE vx.training_program_template_id=t.id)) AS used_count
                """ + base + " ORDER BY t.template_code OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, n) -> new TemplateSummary(rs.getLong("id"), rs.getString("template_code"), rs.getString("status"),
                        rs.getInt("version_no"), rs.getString("name"), rs.getString("goals"),
                        (Long) rs.getObject("duplicated_from_template_id"), rs.getLong("used_count")), pageArgs.toArray());
        return page(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public TemplateDetail templateDetail(long id, Integer versionNo) {
        requirePositiveId(id, "id");
        ActorContext actor = actor();
        Map<String, Object> templateRow = templateRow(id);
        if (actor.roles().contains(RoleCode.TRAINER)) {
            Boolean assigned = jdbc.queryForObject("""
                    SELECT CASE WHEN EXISTS (
                      SELECT 1 FROM dbo.template_version v JOIN dbo.horse_training_program hp ON hp.template_version_id=v.id
                      JOIN dbo.trainer_assignment ta ON ta.horse_training_program_id=hp.id
                      WHERE v.training_program_template_id=? AND ta.trainer_user_id=? AND ta.effective_to IS NULL
                    ) THEN 1 ELSE 0 END
                    """, Boolean.class, id, actor.userId());
            if (!Boolean.TRUE.equals(assigned)) throw forbidden("The template is outside the authenticated Trainer's assigned programs.");
        }
        int latest = jdbc.queryForObject("SELECT MAX(version_no) FROM dbo.template_version WHERE training_program_template_id=?", Integer.class, id);
        int selected = versionNo == null ? latest : versionNo;
        if (selected < 1 || selected > latest) throw notFound("Template version was not found.");
        Long versionId = jdbc.queryForObject("SELECT id FROM dbo.template_version WHERE training_program_template_id=? AND version_no=?", Long.class, id, selected);
        return new TemplateDetail(templateSummary(templateRow), templateVersion(versionId), versionRefs(id));
    }

    @Transactional
    public TemplateDetail createTemplate(TemplateVersionRequest request) {
        ActorContext actor = requireRole(RoleCode.HEAD_TRAINER);
        validateTemplate(request);
        String code = "TPL" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        long templateId = insertId("INSERT INTO dbo.training_program_template (template_code,status) VALUES (?, 'ACTIVE')", code);
        long versionId = insertVersion(templateId, 1, actor.userId(), request);
        audit(actor, "TEMPLATE_CREATED", "TRAINING_PROGRAM_TEMPLATE", templateId, "Template version 1 created.");
        return new TemplateDetail(templateSummary(templateRow(templateId)), templateVersion(versionId), versionRefs(templateId));
    }

    @Transactional
    public TemplateDetail saveTemplateVersion(long id, TemplateVersionRequest request) {
        ActorContext actor = requireRole(RoleCode.HEAD_TRAINER);
        validateTemplate(request);
        Map<String, Object> row = templateRow(id);
        if ("ARCHIVED".equals(row.get("status"))) throw conflict("An archived template cannot receive a new version.");
        Integer current = jdbc.queryForObject("SELECT MAX(version_no) FROM dbo.template_version WHERE training_program_template_id=?", Integer.class, id);
        int next = current + 1;
        long versionId = insertVersion(id, next, actor.userId(), request);
        audit(actor, "TEMPLATE_VERSION_SAVED", "TRAINING_PROGRAM_TEMPLATE", id, "Template version " + next + " saved.");
        return new TemplateDetail(templateSummary(templateRow(id)), templateVersion(versionId), versionRefs(id));
    }

    @Transactional
    public TemplateDetail duplicateTemplate(long id, ProgramApi.DuplicateTemplateRequest request) {
        ActorContext actor = requireRole(RoleCode.HEAD_TRAINER);
        if (request == null || request.name() == null || request.name().isBlank() || request.name().length() > 150) {
            throw validation("name is required and must not exceed 150 characters.");
        }
        templateRow(id);
        TemplateDetail source = templateDetail(id, request.sourceVersionNo());
        TemplateVersion old = source.version();
        String code = "TPL" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        long duplicateId = insertId("INSERT INTO dbo.training_program_template (template_code,status,duplicated_from_template_id) VALUES (?, 'ACTIVE', ?)", code, id);
        TemplateVersionRequest newVersion = new TemplateVersionRequest(request.name(), old.goals(), old.suitableHorseProfile(),
                old.recommendedDistanceUseCase(), old.recommendedDuration(), old.workloadIntensityGuidance(),
                old.preferredSurfaces(), old.recoveryGuidance(), old.requiredSkills().stream().map(ProgramApi.SkillRef::id).toList(),
                old.phases().stream().map(p -> new TemplatePhaseInput(p.phaseOrder(), p.name(), p.objective(), p.recommendedDuration(), p.intensityGuidance())).toList());
        long versionId = insertVersion(duplicateId, 1, actor.userId(), newVersion);
        audit(actor, "TEMPLATE_CREATED", "TRAINING_PROGRAM_TEMPLATE", duplicateId, "Template duplicated from " + id + ".");
        return new TemplateDetail(templateSummary(templateRow(duplicateId)), templateVersion(versionId), versionRefs(duplicateId));
    }

    @Transactional
    public TemplateSummary archiveTemplate(long id) {
        ActorContext actor = requireRole(RoleCode.HEAD_TRAINER);
        Map<String, Object> row = templateRow(id);
        if ("ARCHIVED".equals(row.get("status"))) throw conflict("Template is already archived.");
        jdbc.update("UPDATE dbo.training_program_template SET status='ARCHIVED', updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        audit(actor, "TEMPLATE_ARCHIVED", "TRAINING_PROGRAM_TEMPLATE", id, "Template archived.");
        return templateSummary(templateRow(id));
    }

    @Transactional(readOnly = true)
    public ProgramApi.Page<ProgramSummary> listPrograms(Long horseId, String status, Long trainerId,
                                                        Boolean assignedToMe, int page, int size) {
        ActorContext actor = actor();
        validatePage(page, size);
        if (status != null && !PROGRAM_STATUSES.contains(status)) throw validation("status is not a supported program status.");
        if (horseId != null && horseId <= 0 || trainerId != null && trainerId <= 0) throw validation("Filter ids must be positive.");
        if (trainerId != null && !actor.roles().contains(RoleCode.HEAD_TRAINER)) throw forbidden("Only Head Trainers may select another Trainer filter.");
        if (Boolean.TRUE.equals(assignedToMe) && !actor.roles().contains(RoleCode.TRAINER)) throw forbidden("assignedToMe is only available to Trainers.");
        String scope = actor.roles().contains(RoleCode.TRAINER)
                ? " AND EXISTS (SELECT 1 FROM dbo.trainer_assignment ta WHERE ta.horse_training_program_id=p.id AND ta.trainer_user_id=? AND ta.effective_to IS NULL)"
                : "";
        String where = " FROM dbo.horse_training_program p WHERE (? IS NULL OR p.horse_id=?) AND (? IS NULL OR p.status=?)"
                + (trainerId == null ? "" : " AND EXISTS (SELECT 1 FROM dbo.trainer_assignment tx WHERE tx.horse_training_program_id=p.id AND tx.trainer_user_id=? AND tx.effective_to IS NULL)") + scope;
        List<Object> args = new ArrayList<>();
        args.add(horseId); args.add(horseId); args.add(status); args.add(status);
        if (trainerId != null) args.add(trainerId);
        if (!scope.isEmpty()) args.add(actor.userId());
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(page * size); pageArgs.add(size);
        List<Long> ids = jdbc.queryForList("SELECT p.id" + where + " ORDER BY p.activated_at DESC, p.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", Long.class, pageArgs.toArray());
        List<ProgramSummary> items = ids.stream().map(this::programSummary).toList();
        return page(items, page, size, total);
    }

    @Transactional
    public ProgramDetail createProgram(CreateProgramRequest request) {
        ActorContext actor = requireRole(RoleCode.HEAD_TRAINER);
        validateProgramRequest(request);
        Map<String, Object> horse = jdbc.query("SELECT lifecycle, horse_code, name FROM dbo.horse WHERE id=?",
                rs -> rs.next() ? row("lifecycle",rs.getString("lifecycle"),"horse_code",rs.getString("horse_code"),"name",rs.getString("name")) : null,
                request.horseId());
        if (horse == null) throw notFound("Horse was not found.");
        if (!"ACTIVE".equals(horse.get("lifecycle"))) throw conflict("Only ACTIVE horses can receive a training program.");
        Map<String, Object> version = jdbc.query("""
                SELECT v.training_program_template_id, t.status FROM dbo.template_version v
                JOIN dbo.training_program_template t ON t.id=v.training_program_template_id WHERE v.id=?
                """, rs -> rs.next() ? row("training_program_template_id",rs.getLong("training_program_template_id"),"status",rs.getString("status")) : null,
                request.templateVersionId());
        if (version == null || !"ACTIVE".equals(version.get("status"))) throw conflict("The selected template version is unavailable or archived.");
        validateTrainer(request.trainer().trainerUserId());
        requireNotLocked(request.horseId());
        validatePhases(request.phases(), request.periodStart(), request.periodEnd());
        for (ProgramPhaseInput phase : request.phases()) {
            if (phase.templateVersionPhaseId() != null) {
                Integer phaseBelongs = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.template_version_phase WHERE id=? AND template_version_id=?",
                        Integer.class, phase.templateVersionPhaseId(), request.templateVersionId());
                if (phaseBelongs == null || phaseBelongs != 1) throw notFound("A selected template phase does not belong to the pinned template version.");
            }
        }
        Instant now = Instant.now();
        long programId = insertId("""
                INSERT INTO dbo.horse_training_program (horse_id,template_version_id,target_race_id,created_by_user_id,name,goals,target_metrics,period_start,period_end,status,notes,activated_at)
                VALUES (?,?,NULL,?,?,?,?,?,?,'ACTIVE',?,?)
                """, request.horseId(), request.templateVersionId(), actor.userId(), request.name(), request.goals(),
                request.targetMetrics(), request.periodStart(), request.periodEnd(), request.notes(), now);
        for (ProgramPhaseInput phase : request.phases()) {
            jdbc.update("""
                    INSERT INTO dbo.horse_program_phase (horse_training_program_id,template_version_phase_id,phase_order,name,objective,start_date,end_date,intensity_min,intensity_max,workload,is_customized)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, programId, phase.templateVersionPhaseId(), phase.phaseOrder(), phase.name(), phase.objective(),
                    phase.startDate(), phase.endDate(), phase.intensityMin(), phase.intensityMax(), phase.workload(),
                    phase.templateVersionPhaseId() == null);
        }
        TrainerAssignmentRequest assignment = request.trainer();
        jdbc.update("""
                INSERT INTO dbo.trainer_assignment (horse_training_program_id,trainer_user_id,assigned_by_user_id,effective_from,reason)
                VALUES (?,?,?,?,?)
                """, programId, assignment.trainerUserId(), actor.userId(), assignment.effectiveFrom(), assignment.reason());
        notifyTrainer(assignment.trainerUserId(), programId, request.name());
        audit(actor, "PROGRAM_CREATED", "TRAINING_PROGRAM", programId, "Training program created.");
        audit(actor, "TRAINER_ASSIGNED", "TRAINING_PROGRAM", programId, "Trainer assigned.");
        return programDetail(programId);
    }

    @Transactional(readOnly = true)
    public ProgramDetail programDetail(long id) {
        requirePositiveId(id, "id");
        ActorContext actor = actor();
        if (!programExists(id)) throw notFound("Training program was not found.");
        if (actor.roles().contains(RoleCode.TRAINER) && !trainerAssigned(id, actor.userId())) {
            throw forbidden("The program is outside the authenticated Trainer's assignment scope.");
        }
        return programDetailView(id);
    }

    @Transactional(readOnly = true)
    public ProgramApi.ProgramProgress programProgress(long id) {
        programDetail(id);
        List<ProgramApi.PhaseProgress> phases = jdbc.query("""
                SELECT ph.id,ph.phase_order,ph.name,ph.start_date,ph.end_date,
                  (SELECT COUNT(*) FROM dbo.training_session s WHERE s.horse_program_phase_id=ph.id) AS scheduled_count,
                  (SELECT COUNT(*) FROM dbo.training_session s WHERE s.horse_program_phase_id=ph.id AND s.status='COMPLETED') AS completed_count,
                  (SELECT COUNT(*) FROM dbo.training_session s WHERE s.horse_program_phase_id=ph.id AND s.status='INTERRUPTED') AS interrupted_count,
                  (SELECT TOP 1 sr.evaluation_label FROM dbo.training_session s JOIN dbo.session_result sr ON sr.training_session_id=s.id WHERE s.horse_program_phase_id=ph.id ORDER BY sr.evaluated_at DESC) AS last_evaluation
                FROM dbo.horse_program_phase ph WHERE ph.horse_training_program_id=? ORDER BY ph.phase_order
                """, (rs,n) -> new ProgramApi.PhaseProgress(rs.getLong("id"),rs.getInt("phase_order"),rs.getString("name"),
                rs.getDate("start_date").toLocalDate(),rs.getDate("end_date").toLocalDate(),rs.getInt("scheduled_count"),
                rs.getInt("completed_count"),rs.getInt("interrupted_count"),null,rs.getString("last_evaluation")),id);
        return new ProgramApi.ProgramProgress(id, phases);
    }

    @Transactional(readOnly = true)
    public List<ProgramApi.HistoryEntry> programHistory(long id) {
        programDetail(id);
        return jdbc.query("""
                SELECT occurred_at,action,actor_user_id,safe_summary
                FROM dbo.audit_log WHERE target_type='TRAINING_PROGRAM' AND target_id=?
                  AND action IN ('PROGRAM_CREATED','TRAINER_ASSIGNED','PROGRAM_STATUS_CHANGED','PROGRAM_PHASES_ADJUSTED',
                                 'ADJUSTMENT_REQUESTED','ADJUSTMENT_APPROVED','ADJUSTMENT_REJECTED')
                ORDER BY occurred_at DESC,id DESC
                """, (rs,n) -> {
            Long actorId=(Long)rs.getObject("actor_user_id");
            return new ProgramApi.HistoryEntry(rs.getTimestamp("occurred_at").toInstant(),historyType(rs.getString("action")),
                    actorId==null?null:userRef(actorId),rs.getString("safe_summary"));
        },id);
    }

    @Transactional
    public ProgramDetail setProgramStatus(long id, ProgramApi.SetProgramStatusRequest request) {
        ActorContext actor=requireRole(RoleCode.HEAD_TRAINER);
        requirePositiveId(id,"id");
        if(request==null||!PROGRAM_STATUSES.contains(request.status())) throw validation("status must be ACTIVE, PAUSED, or COMPLETED.");
        Map<String,Object> current=jdbc.query("SELECT status,horse_id FROM dbo.horse_training_program WITH (UPDLOCK, HOLDLOCK) WHERE id=?",
                rs->rs.next()?row("status",rs.getString("status"),"horse_id",rs.getLong("horse_id")):null,id);
        if(current==null) throw notFound("Training program was not found.");
        String before=(String)current.get("status");
        String after=request.status();
        boolean valid=("ACTIVE".equals(before)&&Set.of("PAUSED","COMPLETED").contains(after))
                ||("PAUSED".equals(before)&&Set.of("ACTIVE","COMPLETED").contains(after));
        if(!valid) throw conflict("The requested program status transition is not allowed.");
        if("PAUSED".equals(before)&&"ACTIVE".equals(after)) requireNotLocked((Long)current.get("horse_id"));
        jdbc.update("UPDATE dbo.horse_training_program SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",after,id);
        audit(actor,"PROGRAM_STATUS_CHANGED","TRAINING_PROGRAM",id,"Program status changed from "+before+" to "+after+(request.reason()==null?".":". "+request.reason()));
        return programDetailView(id);
    }

    @Transactional
    public ProgramDetail adjustPhases(long id, ProgramApi.AdjustProgramPhasesRequest request) {
        ActorContext actor=requireRole(RoleCode.HEAD_TRAINER);
        requirePositiveId(id,"id");
        if(request==null||request.phases()==null||request.phases().isEmpty()) throw validation("At least one phase is required.");
        Map<String,Object> program=jdbc.query("SELECT period_start,period_end FROM dbo.horse_training_program WITH (UPDLOCK, HOLDLOCK) WHERE id=?",
                rs->rs.next()?row("period_start",rs.getDate("period_start").toLocalDate(),"period_end",rs.getDate("period_end").toLocalDate()):null,id);
        if(program==null) throw notFound("Training program was not found.");
        validatePhases(request.phases(),(LocalDate)program.get("period_start"),(LocalDate)program.get("period_end"));
        if(request.adjustmentRequestId()!=null) {
            Integer approved=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.program_adjustment_request WHERE id=? AND horse_training_program_id=? AND status='APPROVED'",Integer.class,request.adjustmentRequestId(),id);
            if(approved==null||approved!=1) throw conflict("Only an approved adjustment request can be applied.");
        }
        Set<Long> retained=new HashSet<>();
        for(ProgramPhaseInput phase:request.phases()) if(phase.id()!=null&&!retained.add(phase.id())) throw validation("Phase ids must be unique.");
        List<Long> existing=jdbc.queryForList("SELECT id FROM dbo.horse_program_phase WHERE horse_training_program_id=?",Long.class,id);
        for(Long phaseId:existing) if(!retained.contains(phaseId)) {
            Integer hasSessions=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.training_session WHERE horse_program_phase_id=?",Integer.class,phaseId);
            if(hasSessions!=null&&hasSessions>0) throw conflict("A phase with Training Sessions cannot be removed.");
            jdbc.update("DELETE FROM dbo.horse_program_phase WHERE id=? AND horse_training_program_id=?",phaseId,id);
        }
        for(ProgramPhaseInput phase:request.phases()) {
            if(phase.id()==null) {
                jdbc.update("""
                        INSERT INTO dbo.horse_program_phase (horse_training_program_id,template_version_phase_id,phase_order,name,objective,start_date,end_date,intensity_min,intensity_max,workload,is_customized)
                        VALUES (?,?,?,?,?,?,?,?,?,?,1)
                        """,id,phase.templateVersionPhaseId(),phase.phaseOrder(),phase.name(),phase.objective(),phase.startDate(),phase.endDate(),phase.intensityMin(),phase.intensityMax(),phase.workload());
            } else {
                Integer belongs=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.horse_program_phase WHERE id=? AND horse_training_program_id=?",Integer.class,phase.id(),id);
                if(belongs==null||belongs!=1) throw notFound("A supplied phase id is not part of this program.");
                jdbc.update("""
                        UPDATE dbo.horse_program_phase SET template_version_phase_id=?,phase_order=?,name=?,objective=?,start_date=?,end_date=?,intensity_min=?,intensity_max=?,workload=?,is_customized=1,updated_at=CURRENT_TIMESTAMP
                        WHERE id=? AND horse_training_program_id=?
                        """,phase.templateVersionPhaseId(),phase.phaseOrder(),phase.name(),phase.objective(),phase.startDate(),phase.endDate(),phase.intensityMin(),phase.intensityMax(),phase.workload(),phase.id(),id);
            }
        }
        audit(actor,"PROGRAM_PHASES_ADJUSTED","TRAINING_PROGRAM",id,"Program phase structure adjusted."+(request.adjustmentRequestId()==null?"":" Applied approved request "+request.adjustmentRequestId()+"."));
        return programDetailView(id);
    }

    @Transactional
    public ProgramDetail reassignTrainer(long id, TrainerAssignmentRequest request) {
        ActorContext actor = requireRole(RoleCode.HEAD_TRAINER);
        requirePositiveId(id, "id");
        if (request == null || request.trainerUserId() <= 0 || request.effectiveFrom() == null) throw validation("Trainer and effectiveFrom are required.");
        Map<String, Object> program = jdbc.query("SELECT status FROM dbo.horse_training_program WITH (UPDLOCK, HOLDLOCK) WHERE id=?",
                rs -> rs.next() ? row("status",rs.getString("status")) : null, id);
        if (program == null) throw notFound("Training program was not found.");
        validateTrainer(request.trainerUserId());
        Map<String, Object> active = jdbc.query("SELECT id,effective_from,trainer_user_id FROM dbo.trainer_assignment WITH (UPDLOCK, HOLDLOCK) WHERE horse_training_program_id=? AND effective_to IS NULL",
                rs -> rs.next() ? row("id",rs.getLong("id"),"effective_from",rs.getDate("effective_from").toLocalDate(),"trainer_user_id",rs.getLong("trainer_user_id")) : null, id);
        if (active == null) throw conflict("The program has no active Trainer assignment.");
        if (Objects.equals(active.get("trainer_user_id"), request.trainerUserId())) throw conflict("The requested Trainer is already assigned.");
        LocalDate currentStart = (LocalDate) active.get("effective_from");
        if (!request.effectiveFrom().isAfter(currentStart)) throw conflict("The new assignment date must follow the current assignment start.");
        jdbc.update("UPDATE dbo.trainer_assignment SET effective_to=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                request.effectiveFrom().minusDays(1), active.get("id"));
        jdbc.update("INSERT INTO dbo.trainer_assignment (horse_training_program_id,trainer_user_id,assigned_by_user_id,effective_from,reason) VALUES (?,?,?,?,?)",
                id, request.trainerUserId(), actor.userId(), request.effectiveFrom(), request.reason());
        notifyTrainer(request.trainerUserId(), id, "Training program");
        audit(actor, "TRAINER_ASSIGNED", "TRAINING_PROGRAM", id, "Trainer assignment changed.");
        return programDetailView(id);
    }

    private ProgramDetail programDetailView(long id) {
        ProgramSummary summary = programSummary(id);
        Map<String, Object> row = jdbc.query("""
                SELECT t.template_code,v.name AS template_name,p.goals,p.target_metrics,p.notes,p.activated_at,p.created_by_user_id,
                       (SELECT COUNT(*) FROM dbo.program_adjustment_request ar WHERE ar.horse_training_program_id=p.id AND ar.status='PENDING') AS pending_count
                FROM dbo.horse_training_program p JOIN dbo.template_version v ON v.id=p.template_version_id
                JOIN dbo.training_program_template t ON t.id=v.training_program_template_id WHERE p.id=?
                """, rs -> rs.next() ? row("template_code",rs.getString("template_code"),"template_name",rs.getString("template_name"),"goals",rs.getString("goals"),
                        "target_metrics",rs.getString("target_metrics"),"notes",rs.getString("notes"),"activated_at",rs.getTimestamp("activated_at").toInstant(),
                        "created_by_user_id",rs.getLong("created_by_user_id"),"pending_count",rs.getInt("pending_count")) : null, id);
        List<ProgramPhase> phases = jdbc.query("""
                SELECT id,template_version_phase_id,phase_order,name,objective,start_date,end_date,intensity_min,intensity_max,workload,is_customized
                FROM dbo.horse_program_phase WHERE horse_training_program_id=? ORDER BY phase_order
                """, (rs, n) -> new ProgramPhase(rs.getLong("id"), (Long) rs.getObject("template_version_phase_id"),
                        rs.getInt("phase_order"), rs.getString("name"), rs.getString("objective"),
                        rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(),
                        rs.getString("intensity_min"), rs.getString("intensity_max"), rs.getString("workload"),
                        rs.getBoolean("is_customized")), id);
        UserRef createdBy = userRef((Long) row.get("created_by_user_id"));
        return new ProgramDetail(summary.id(), summary.horse(), summary.name(), summary.status(), summary.periodStart(),
                summary.periodEnd(), summary.sourceTemplateId(), summary.sourceTemplateVersion(), summary.currentTrainer(),
                summary.currentPhaseName(), summary.trainingAvailability(), (String) row.get("template_code"),
                (String) row.get("template_name"), (String) row.get("goals"), (String) row.get("target_metrics"),
                (String) row.get("notes"), (Instant) row.get("activated_at"), createdBy, phases,
                (Integer) row.get("pending_count"));
    }

    private ProgramSummary programSummary(long id) {
        Map<String, Object> p = jdbc.query("""
                SELECT p.id,p.horse_id,h.horse_code,h.name AS horse_name,p.name,p.status,p.period_start,p.period_end,
                       tv.training_program_template_id,tv.version_no,
                       (SELECT TOP 1 u.id FROM dbo.trainer_assignment ta JOIN dbo.app_user u ON u.id=ta.trainer_user_id WHERE ta.horse_training_program_id=p.id AND ta.effective_to IS NULL) AS trainer_id,
                       (SELECT TOP 1 u.user_code FROM dbo.trainer_assignment ta JOIN dbo.app_user u ON u.id=ta.trainer_user_id WHERE ta.horse_training_program_id=p.id AND ta.effective_to IS NULL) AS trainer_code,
                       (SELECT TOP 1 u.full_name FROM dbo.trainer_assignment ta JOIN dbo.app_user u ON u.id=ta.trainer_user_id WHERE ta.horse_training_program_id=p.id AND ta.effective_to IS NULL) AS trainer_name,
                       (SELECT TOP 1 ph.name FROM dbo.horse_program_phase ph WHERE ph.horse_training_program_id=p.id AND CAST(CURRENT_TIMESTAMP AS DATE) BETWEEN ph.start_date AND ph.end_date ORDER BY ph.phase_order) AS current_phase,
                       COALESCE((SELECT TOP 1 mr.restriction_level FROM dbo.medical_restriction mr WHERE mr.horse_id=p.horse_id AND mr.lifted_at IS NULL),'AVAILABLE') AS availability
                FROM dbo.horse_training_program p JOIN dbo.horse h ON h.id=p.horse_id
                JOIN dbo.template_version tv ON tv.id=p.template_version_id
                JOIN dbo.training_program_template v ON v.id=tv.training_program_template_id WHERE p.id=?
                """, rs -> rs.next() ? row("id",rs.getLong("id"),"horse_id",rs.getLong("horse_id"),"horse_code",rs.getString("horse_code"),"horse_name",rs.getString("horse_name"),
                        "name",rs.getString("name"),"status",rs.getString("status"),"period_start",rs.getDate("period_start").toLocalDate(),"period_end",rs.getDate("period_end").toLocalDate(),
                        "training_program_template_id",rs.getLong("training_program_template_id"),"version_no",rs.getInt("version_no"),"trainer_id",(Long)rs.getObject("trainer_id"),
                        "trainer_code",rs.getString("trainer_code"),"trainer_name",rs.getString("trainer_name"),"current_phase",rs.getString("current_phase"),"availability",rs.getString("availability")) : null, id);
        if (p == null) throw notFound("Training program was not found.");
        UserRef trainer = p.get("trainer_id") == null ? null : new UserRef((Long) p.get("trainer_id"), (String) p.get("trainer_code"), (String) p.get("trainer_name"));
        return new ProgramSummary((Long) p.get("id"), new HorseRef((Long) p.get("horse_id"), (String) p.get("horse_code"), (String) p.get("horse_name")),
                (String) p.get("name"), (String) p.get("status"), (LocalDate) p.get("period_start"), (LocalDate) p.get("period_end"),
                (Long) p.get("training_program_template_id"), (Integer) p.get("version_no"), trainer, (String) p.get("current_phase"), (String) p.get("availability"));
    }

    private long insertVersion(long templateId, int versionNo, long actorId, TemplateVersionRequest request) {
        long versionId = insertId("""
                INSERT INTO dbo.template_version (training_program_template_id,created_by_user_id,version_no,name,goals,suitable_horse_profile,recommended_distance_use_case,recommended_duration,workload_intensity_guidance,preferred_surfaces,recovery_guidance)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, templateId, actorId, versionNo, request.name(), request.goals(), request.suitableHorseProfile(),
                request.recommendedDistanceUseCase(), request.recommendedDuration(), request.workloadIntensityGuidance(),
                request.preferredSurfaces(), request.recoveryGuidance());
        for (TemplatePhaseInput phase : request.phases()) {
            jdbc.update("INSERT INTO dbo.template_version_phase (template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (?,?,?,?,?,?)",
                    versionId, phase.phaseOrder(), phase.name(), phase.objective(), phase.recommendedDuration(), phase.intensityGuidance());
        }
        if (request.requiredSkillIds() != null) {
            for (Long skillId : request.requiredSkillIds()) {
                Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.skill WHERE id=? AND kind='TRAINER_SPECIALIZATION'", Integer.class, skillId);
                if (valid == null || valid != 1) throw notFound("A required Trainer specialization skill was not found.");
                jdbc.update("INSERT INTO dbo.template_required_skill (template_version_id,skill_id) VALUES (?,?)", versionId, skillId);
            }
        }
        return versionId;
    }

    private TemplateVersion templateVersion(long versionId) {
        Map<String, Object> v = jdbc.query("SELECT * FROM dbo.template_version WHERE id=?", rs -> rs.next()
                ? row("id",rs.getLong("id"),"version_no",rs.getInt("version_no"),"name",rs.getString("name"),"goals",rs.getString("goals"),
                "suitable_horse_profile",rs.getString("suitable_horse_profile"),"recommended_distance_use_case",rs.getString("recommended_distance_use_case"),"recommended_duration",rs.getString("recommended_duration"),
                "workload_intensity_guidance",rs.getString("workload_intensity_guidance"),"preferred_surfaces",rs.getString("preferred_surfaces"),"recovery_guidance",rs.getString("recovery_guidance"),
                "created_by_user_id",rs.getLong("created_by_user_id"),"created_at",rs.getTimestamp("created_at").toInstant()) : null, versionId);
        List<TemplatePhase> phases = jdbc.query("SELECT id,phase_order,name,objective,recommended_duration,intensity_guidance FROM dbo.template_version_phase WHERE template_version_id=? ORDER BY phase_order",
                (rs,n) -> new TemplatePhase(rs.getLong("id"),rs.getInt("phase_order"),rs.getString("name"),rs.getString("objective"),rs.getString("recommended_duration"),rs.getString("intensity_guidance")), versionId);
        List<ProgramApi.SkillRef> skills = jdbc.query("SELECT s.id,s.name FROM dbo.template_required_skill trs JOIN dbo.skill s ON s.id=trs.skill_id WHERE trs.template_version_id=? ORDER BY s.name",
                (rs,n) -> new ProgramApi.SkillRef(rs.getLong("id"),rs.getString("name")), versionId);
        Long createdById = (Long) v.get("created_by_user_id");
        return new TemplateVersion((Long)v.get("id"),(Integer)v.get("version_no"),(String)v.get("name"),(String)v.get("goals"),
                (String)v.get("suitable_horse_profile"),(String)v.get("recommended_distance_use_case"),(String)v.get("recommended_duration"),
                (String)v.get("workload_intensity_guidance"),(String)v.get("preferred_surfaces"),(String)v.get("recovery_guidance"),
                skills,phases,createdById == null ? null : userRef(createdById),(Instant)v.get("created_at"));
    }

    private List<ProgramApi.VersionRef> versionRefs(long templateId) {
        return jdbc.query("SELECT id,version_no,created_at FROM dbo.template_version WHERE training_program_template_id=? ORDER BY version_no DESC",
                (rs,n) -> new ProgramApi.VersionRef(rs.getLong("id"),rs.getInt("version_no"),rs.getTimestamp("created_at").toInstant()), templateId);
    }

    private TemplateSummary templateSummary(Map<String,Object> row) {
        return new TemplateSummary((Long)row.get("id"),(String)row.get("template_code"),(String)row.get("status"),
                (Integer)row.get("latest_version_no"),(String)row.get("name"),(String)row.get("goals"),
                (Long)row.get("duplicated_from_template_id"),(Long)row.get("used_count"));
    }

    private Map<String,Object> templateRow(long id) {
        Map<String,Object> row = jdbc.query("""
                SELECT t.id,t.template_code,t.status,t.duplicated_from_template_id,v.id AS latest_version_id,v.version_no AS latest_version_no,v.name,v.goals,
                       (SELECT COUNT(*) FROM dbo.horse_training_program hp JOIN dbo.template_version vx ON vx.id=hp.template_version_id WHERE vx.training_program_template_id=t.id) AS used_count
                FROM dbo.training_program_template t JOIN dbo.template_version v ON v.training_program_template_id=t.id
                  AND v.version_no=(SELECT MAX(v2.version_no) FROM dbo.template_version v2 WHERE v2.training_program_template_id=t.id)
                WHERE t.id=?
                """, rs -> rs.next() ? row("id",rs.getLong("id"),"template_code",rs.getString("template_code"),"status",rs.getString("status"),
                "duplicated_from_template_id",(Long)rs.getObject("duplicated_from_template_id"),"latest_version_id",rs.getLong("latest_version_id"),"latest_version_no",rs.getInt("latest_version_no"),
                "name",rs.getString("name"),"goals",rs.getString("goals"),"used_count",rs.getLong("used_count")) : null,id);
        if (row == null) throw notFound("Program template was not found.");
        return row;
    }

    private void validateTemplate(TemplateVersionRequest request) {
        if (request == null || request.name() == null || request.name().isBlank() || request.phases() == null || request.phases().isEmpty()) {
            throw validation("Template name and at least one phase are required.");
        }
        Set<Integer> orders = new HashSet<>();
        for (TemplatePhaseInput phase : request.phases()) {
            if (phase.phaseOrder() <= 0 || phase.name() == null || phase.name().isBlank() || !orders.add(phase.phaseOrder())) {
                throw validation("Template phases require unique positive orders and names.");
            }
        }
        if (request.requiredSkillIds() != null && new HashSet<>(request.requiredSkillIds()).size() != request.requiredSkillIds().size()) {
            throw validation("requiredSkillIds must be unique.");
        }
    }

    private void validateProgramRequest(CreateProgramRequest r) {
        if (r == null || r.horseId() <= 0 || r.templateVersionId() <= 0 || r.name() == null || r.name().isBlank()
                || r.periodStart() == null || r.periodEnd() == null || r.periodEnd().isBefore(r.periodStart())
                || r.trainer() == null || r.phases() == null || r.phases().isEmpty()) throw validation("Required program fields are missing or invalid.");
    }

    private void validatePhases(List<ProgramPhaseInput> phases, LocalDate start, LocalDate end) {
        List<ProgramPhaseInput> sorted = phases.stream().sorted(Comparator.comparingInt(ProgramPhaseInput::phaseOrder)).toList();
        Set<Integer> orders = new HashSet<>();
        LocalDate previousEnd = null;
        for (ProgramPhaseInput p : sorted) {
            if (p.phaseOrder() <= 0 || !orders.add(p.phaseOrder()) || p.name() == null || p.name().isBlank()
                    || p.startDate() == null || p.endDate() == null || p.endDate().isBefore(p.startDate())
                    || p.startDate().isBefore(start) || p.endDate().isAfter(end)
                    || previousEnd != null && !p.startDate().isAfter(previousEnd)
                    || p.intensityMin() != null && !INTENSITIES.contains(p.intensityMin())
                    || p.intensityMax() != null && !INTENSITIES.contains(p.intensityMax())) {
                throw validation("Program phases must be ordered, non-overlapping, inside the program period, and use approved intensities.");
            }
            previousEnd = p.endDate();
        }
    }

    private void validateTrainer(long trainerId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.app_user u JOIN dbo.user_role ur ON ur.user_id=u.id JOIN dbo.role r ON r.id=ur.role_id WHERE u.id=? AND u.account_status='ACTIVE' AND r.role_code='TRAINER'", Integer.class, trainerId);
        if (count == null || count != 1) throw notFound("An active Trainer account was not found.");
    }

    private void requireNotLocked(long horseId) {
        List<Map<String,Object>> rows = jdbc.query("SELECT TOP 1 restriction_level,reason FROM dbo.medical_restriction WHERE horse_id=? AND lifted_at IS NULL", (rs,n)->row("restriction_level",rs.getString("restriction_level"),"reason",rs.getString("reason")),horseId);
        if (!rows.isEmpty() && "LOCKED".equals(rows.getFirst().get("restriction_level"))) {
            throw new BusinessException(ErrorCode.CONFLICT, "Training program is blocked by a LOCKED medical restriction: " + rows.getFirst().get("reason"));
        }
    }

    private void notifyTrainer(long trainerId, long programId, String programName) {
        jdbc.update("INSERT INTO dbo.notification (recipient_user_id,category,notification_type,title,message,target_type,target_id) VALUES (?,'SCHEDULE','PROGRAM_ASSIGNED',?,?,?,?)",
                trainerId, "Training program assigned", "You have been assigned to " + programName + ".", "TRAINING_PROGRAM", programId);
    }

    private void audit(ActorContext actor, String action, String type, long id, String summary) {
        jdbc.update("INSERT INTO dbo.audit_log (actor_user_id,actor_role,action,target_type,target_id,safe_summary,source) VALUES (?,?,?,?,?,?,?)",
                actor.userId(), actor.roles().stream().findFirst().map(Enum::name).orElse(null), action, type, id, summary, "API");
    }

    private long insertId(String sql, Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i=0;i<args.length;i++) ps.setObject(i+1,args[i]);
            return ps;
        }, holder);
        Object value = holder.getKeyList().stream().flatMap(m -> m.entrySet().stream())
                .filter(e -> e.getKey().equalsIgnoreCase("id")).map(Map.Entry::getValue).findFirst().orElse(null);
        if (!(value instanceof Number n)) throw new IllegalStateException("The database did not return the generated id.");
        return n.longValue();
    }

    private UserRef userRef(long id) {
        return jdbc.query("SELECT id,user_code,full_name FROM dbo.app_user WHERE id=?", rs -> rs.next()
                ? new UserRef(rs.getLong("id"),rs.getString("user_code"),rs.getString("full_name")) : null,id);
    }

    private boolean programExists(long id) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.horse_training_program WHERE id=?",Integer.class,id);
        return n != null && n == 1;
    }

    private boolean trainerAssigned(long programId,long trainerId) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.trainer_assignment WHERE horse_training_program_id=? AND trainer_user_id=? AND effective_to IS NULL",Integer.class,programId,trainerId);
        return n != null && n > 0;
    }

    private String historyType(String action) {
        return switch(action) {
            case "PROGRAM_CREATED" -> "CREATED";
            case "TRAINER_ASSIGNED" -> "TRAINER_ASSIGNED";
            case "PROGRAM_STATUS_CHANGED" -> "STATUS_CHANGED";
            case "PROGRAM_PHASES_ADJUSTED" -> "PHASES_ADJUSTED";
            default -> action;
        };
    }

    private ActorContext actor() {
        return actorProvider.currentActor().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,"A valid authenticated actor is required."));
    }

    private ActorContext requireRole(RoleCode role) {
        ActorContext a=actor();
        if (!a.roles().contains(role)) throw forbidden("The authenticated role cannot perform this operation.");
        return a;
    }

    private void validatePage(int page,int size) {
        if (page<0 || size<1 || size>100) throw validation("page must be non-negative and size must be between 1 and 100.");
    }

    private <T> ProgramApi.Page<T> page(List<T> rows,int page,int size,long total) {
        return new ProgramApi.Page<>(rows,page,size,total,(int)Math.ceil(total/(double)size));
    }

    private void requirePositiveId(long id,String field) { if(id<=0) throw validation(field+" must be positive."); }

    private Map<String,Object> row(Object... pairs) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]);
        return result;
    }

    private BusinessException validation(String m){return new BusinessException(ErrorCode.VALIDATION_ERROR,m);}
    private BusinessException notFound(String m){return new BusinessException(ErrorCode.NOT_FOUND,m);}
    private BusinessException forbidden(String m){return new BusinessException(ErrorCode.FORBIDDEN,m);}
    private BusinessException conflict(String m){return new BusinessException(ErrorCode.CONFLICT,m);}
}
