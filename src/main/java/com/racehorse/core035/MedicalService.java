package com.racehorse.core035;

import com.racehorse.common.error.*;
import com.racehorse.core035.MedicalDtos.*;
import com.racehorse.core035.MedicalPorts.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class MedicalService {
    private static final ZoneId CLUB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final MedicalCases cases;
    private final ExaminationStore exams;
    private final Injuries injuries;
    private final TreatmentPlans plans;
    private final TreatmentTasks tasks;
    private final JdbcTemplate sql;
    private final ObjectProvider<ActorResolver> actors;
    private final ObjectProvider<TreatmentTaskMaterializer> taskMaterializers;

    MedicalService(MedicalCases cases, ExaminationStore exams, Injuries injuries, TreatmentPlans plans,
                   TreatmentTasks tasks, JdbcTemplate sql, ObjectProvider<ActorResolver> actors,
                   ObjectProvider<TreatmentTaskMaterializer> taskMaterializers) {
        this.cases = cases; this.exams = exams; this.injuries = injuries; this.plans = plans;
        this.tasks = tasks; this.sql = sql; this.actors = actors; this.taskMaterializers = taskMaterializers;
    }

    static BusinessException error(ErrorCode code, String message) { return new BusinessException(code, message); }
    @Transactional(readOnly=true)
    public HerdHealthOverview herdHealth() {
        vet();
        return sql.queryForObject("""
                SELECT COUNT(*) AS total,
                  COALESCE(SUM(CASE WHEN health_status='FIT' THEN 1 ELSE 0 END),0) AS fit,
                  COALESCE(SUM(CASE WHEN health_status='MONITORING' THEN 1 ELSE 0 END),0) AS monitoring,
                  COALESCE(SUM(CASE WHEN health_status='INJURED' THEN 1 ELSE 0 END),0) AS injured,
                  COALESCE(SUM(CASE WHEN health_status='QUARANTINED' THEN 1 ELSE 0 END),0) AS quarantined,
                  COALESCE(SUM(CASE WHEN health_status IS NULL THEN 1 ELSE 0 END),0) AS unassessed
                FROM dbo.horse
                """,(rs,n)->new HerdHealthOverview(rs.getLong("total"),rs.getLong("fit"),rs.getLong("monitoring"),rs.getLong("injured"),rs.getLong("quarantined"),rs.getLong("unassessed")));
    }
    private Actor actor() {
        ActorResolver resolver = actors.getIfAvailable();
        if (resolver == null) throw error(ErrorCode.UNAUTHORIZED, "Authentication integration is unavailable.");
        Actor actor = resolver.currentActor();
        if (actor == null) throw error(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        return actor;
    }
    private Actor vet() {
        Actor actor = actor();
        if (!actor.has(Role.VETERINARIAN)) throw error(ErrorCode.FORBIDDEN, "Veterinarian role required.");
        return actor;
    }
    private static LocalDateTime time(Instant value) {
        return LocalDateTime.ofInstant(value.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
    }
    private static Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
    private static LocalDateTime now() { return time(Instant.now()); }
    private static void positive(long id) {
        if (id < 1) throw error(ErrorCode.VALIDATION_ERROR, "Positive ID required.");
    }
    private MedicalCaseEntity lookup(long id) {
        positive(id);
        return cases.findById(id).orElseThrow(() -> error(ErrorCode.NOT_FOUND, "Medical case not found."));
    }
    private MedicalCaseEntity open(long id) {
        positive(id);
        MedicalCaseEntity value = cases.locked(id).orElseThrow(() -> error(ErrorCode.NOT_FOUND, "Medical case not found."));
        if (value.status != CaseStatus.OPEN) throw error(ErrorCode.CONFLICT, "Medical case is closed.");
        return value;
    }
    private boolean horseExists(long horseId) {
        return Boolean.TRUE.equals(sql.queryForObject("select case when exists(select 1 from dbo.horse where id=?) then 1 else 0 end", Integer.class, horseId) == 1);
    }
    private boolean trainerAssigned(Actor actor, long horseId) {
        LocalDate today = LocalDate.now(CLUB_ZONE);
        Integer count = sql.queryForObject("select count(*) from dbo.trainer_assignment a join dbo.horse_training_program p on p.id=a.horse_training_program_id where p.horse_id=? and a.trainer_user_id=? and a.effective_from<=? and (a.effective_to is null or a.effective_to>=?)", Integer.class, horseId, actor.id(), today, today);
        return count != null && count > 0;
    }
    private boolean clinical(Actor actor, MedicalCaseEntity c) {
        if (actor.has(Role.VETERINARIAN)) return true;
        if (actor.has(Role.CLUB_MANAGER) || actor.has(Role.HEAD_TRAINER)) return false;
        if (actor.has(Role.TRAINER) && trainerAssigned(actor, c.horseId)) return false;
        throw error(ErrorCode.FORBIDDEN, "Medical case access denied.");
    }
    private void readRole(Actor actor) {
        if (!(actor.has(Role.VETERINARIAN) || actor.has(Role.CLUB_MANAGER)
                || actor.has(Role.HEAD_TRAINER) || actor.has(Role.TRAINER)))
            throw error(ErrorCode.FORBIDDEN, "Medical case access denied.");
    }
    private static CaseSummary summary(MedicalCaseEntity c) {
        return new CaseSummary(c.id, c.horseId, c.status, instant(c.openedAt), instant(c.closedAt));
    }
    private CaseDetail detail(MedicalCaseEntity c) {
        List<ExaminationStore.HistoryItem> history = exams.history(c.id);
        List<Long> incidentIds = sql.queryForList("select id from dbo.incident where medical_case_id=? order by id", Long.class, c.id);
        return new CaseDetail(c.id, c.horseId, c.status, instant(c.openedAt), instant(c.closedAt),
                c.conditionSummary, incidentIds,
                history.stream().filter(e -> e.type() == ExamType.EXAMINATION).map(ExaminationStore.HistoryItem::view).toList(),
                injuries.findByMedicalCaseIdOrderByIdDesc(c.id).stream().map(this::injuryView).toList(),
                plans.findByMedicalCaseId(c.id).map(this::planView).orElse(null),
                history.stream().filter(e -> e.type() == ExamType.RE_EXAMINATION).map(ExaminationStore.HistoryItem::view).map(e -> {
                    if (e.recoveryStatus() == null) throw error(ErrorCode.CONFLICT, "Re-examination recovery outcome is missing.");
                    return new ReexamView(e.id(), e.caseId(), e.horseId(), e.recoveryStatus(), e.symptoms(), e.examinedAt(),
                            e.healthStatus(), e.diagnosis(), e.severity(), e.bodyCondition());
                }).toList());
    }

    @Transactional
    public CaseDetail create(CaseCreate request) {
        Actor actor = vet();
        if (request == null || request.horseId() == null || request.horseId() < 1 || request.reason() == null || request.reason().isBlank() || request.reason().length() > 2000)
            throw error(ErrorCode.VALIDATION_ERROR, "Invalid medical case request.");
        if (!horseExists(request.horseId())) throw error(ErrorCode.NOT_FOUND, "Horse not found.");
        MedicalCaseEntity c = cases.saveAndFlush(new MedicalCaseEntity(request.horseId(), actor.id(), request.reason(), now()));
        return detail(c);
    }

    @Transactional(readOnly = true)
    public Page<Object> list(int page, int size) {
        Actor actor = actor(); readRole(actor);
        if (page < 0 || size < 1 || size > 100) throw error(ErrorCode.VALIDATION_ERROR, "Invalid page or size.");
        boolean unrestricted = actor.has(Role.VETERINARIAN) || actor.has(Role.CLUB_MANAGER) || actor.has(Role.HEAD_TRAINER);
        String where = unrestricted ? "" : " where exists (select 1 from dbo.horse_training_program p join dbo.trainer_assignment a on a.horse_training_program_id=p.id where p.horse_id=c.horse_id and a.trainer_user_id=? and a.effective_from<=? and a.effective_to is null)";
        Object[] scope = unrestricted ? new Object[]{} : new Object[]{actor.id(), LocalDate.now(CLUB_ZONE)};
        long total = Objects.requireNonNull(sql.queryForObject("select count(*) from dbo.medical_case c" + where, Long.class, scope));
        Object[] args = Arrays.copyOf(scope, scope.length + 2); args[scope.length] = (long) page * size; args[scope.length + 1] = size;
        List<Long> ids = sql.queryForList("select c.id from dbo.medical_case c" + where + " order by c.opened_at desc,c.id desc offset ? rows fetch next ? rows only", Long.class, args);
        List<Object> items = ids.stream().map(id -> {
            MedicalCaseEntity c = lookup(id);
            return (Object) (actor.has(Role.VETERINARIAN) ? detail(c) : summary(c));
        }).toList();
        return new Page<>(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public Object get(long id) {
        Actor actor = actor(); readRole(actor);
        MedicalCaseEntity c = lookup(id);
        return clinical(actor, c) ? detail(c) : summary(c);
    }

    @Transactional
    public CaseDetail close(long id) {
        vet(); MedicalCaseEntity c = open(id);
        requireClosureConditions(c.id);
        c.status = CaseStatus.CLOSED;
        LocalDateTime stamp = now();
        c.closedAt = stamp.isBefore(c.openedAt) ? c.openedAt : stamp;
        c.updatedAt = c.closedAt;
        cases.flush();
        return detail(c);
    }

    private void requireClosureConditions(long caseId) {
        if (!exists("select case when exists(select 1 from dbo.veterinary_examination where medical_case_id=? and exam_type in ('EXAMINATION','RE_EXAMINATION')) then 1 else 0 end", caseId)) {
            throw error(ErrorCode.BUSINESS_RULE_VIOLATION, "A medical case requires at least one clinical examination before closure.");
        }

        boolean hasInjury = exists("select case when exists(select 1 from dbo.injury where medical_case_id=?) then 1 else 0 end", caseId);
        boolean hasPlan = exists("select case when exists(select 1 from dbo.treatment_plan where medical_case_id=?) then 1 else 0 end", caseId);
        if (hasInjury || hasPlan) {
            List<String> outcomes = sql.queryForList("select top (1) recovery_outcome from dbo.veterinary_examination where medical_case_id=? and exam_type='RE_EXAMINATION' order by examined_at desc,id desc", String.class, caseId);
            if (outcomes.isEmpty() || !RecoveryStatus.RECOVERED.name().equals(outcomes.getFirst())) {
                throw error(ErrorCode.BUSINESS_RULE_VIOLATION, "The latest re-examination must record RECOVERED before closure when an injury or treatment plan exists.");
            }
        }

        if (hasPlan && exists("select case when exists(select 1 from dbo.care_task ct join dbo.treatment_plan tp on tp.id=ct.treatment_plan_id where tp.medical_case_id=? and (ct.completed_at is null or ct.completed_by_user_id is null)) then 1 else 0 end", caseId)) {
            throw error(ErrorCode.BUSINESS_RULE_VIOLATION, "All treatment tasks must be completed before case closure.");
        }
        if (exists("select case when exists(select 1 from dbo.medical_restriction where medical_case_id=? and lifted_at is null) then 1 else 0 end", caseId)) {
            throw error(ErrorCode.BUSINESS_RULE_VIOLATION, "All restrictions linked to the case must be lifted before closure.");
        }
        if (exists("select case when exists(select 1 from dbo.incident where medical_case_id=? and status<>'RESOLVED') then 1 else 0 end", caseId)) {
            throw error(ErrorCode.BUSINESS_RULE_VIOLATION, "All incidents linked to the case must be resolved before closure.");
        }
    }

    private boolean exists(String query, long caseId) {
        Integer result = sql.queryForObject(query, Integer.class, caseId);
        return result != null && result == 1;
    }

    @Transactional
    public ExamView examine(long id, ExamCreate request) {
        Actor actor = vet(); MedicalCaseEntity c = open(id);
        if (request == null || request.examinedAt() == null || request.symptoms() == null || request.symptoms().isBlank())
            throw error(ErrorCode.VALIDATION_ERROR, "Invalid examination request.");
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        long examId = exams.insert(c.horseId, c.id, actor.id(), ExamType.EXAMINATION,
                request.examinedAt(), request.symptoms(), request.diagnosis(), request.severity(),
                null, request.healthStatus(), request.bodyCondition(), createdAt);
        if (request.healthStatus() != null) sql.update("update dbo.horse set health_status=?,updated_at=? where id=?",
                request.healthStatus().name(), time(createdAt), c.horseId);
        return new ExamView(examId, c.id, c.horseId, request.examinedAt().truncatedTo(ChronoUnit.MILLIS),
                request.symptoms(), request.diagnosis(), request.severity(), request.healthStatus(), request.bodyCondition(), null);
    }

    @Transactional
    public ReexamView reexamine(long id, ReexamCreate request) {
        Actor actor = vet(); MedicalCaseEntity c = open(id);
        if (request == null || request.examinedAt() == null || request.findings() == null || request.findings().isBlank() || request.recoveryStatus() == null)
            throw error(ErrorCode.VALIDATION_ERROR, "Invalid re-examination request.");
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        long examId = exams.insert(c.horseId, c.id, actor.id(), ExamType.RE_EXAMINATION,
                request.examinedAt(), request.findings(), request.diagnosis(), request.severity(),
                request.recoveryStatus(), request.healthStatus(), request.bodyCondition(), createdAt);
        if (request.healthStatus() != null) sql.update("update dbo.horse set health_status=?,updated_at=? where id=?",
                request.healthStatus().name(), time(createdAt), c.horseId);
        return new ReexamView(examId, c.id, c.horseId, request.recoveryStatus(), request.findings(),
                request.examinedAt().truncatedTo(ChronoUnit.MILLIS), request.healthStatus(),
                request.diagnosis(), request.severity(), request.bodyCondition());
    }

    @Transactional
    public InjuryView injure(long id, InjuryCreate request) {
        vet(); MedicalCaseEntity c = open(id);
        if (request == null || request.bodyRegion() == null || request.bodyRegion().isBlank() || request.severity() == null || request.recoveryStatus() == null)
            throw error(ErrorCode.VALIDATION_ERROR, "Invalid injury request.");
        if (request.position3d() != null && !request.position3d().chars().allMatch(ch -> ch < 128))
            throw error(ErrorCode.VALIDATION_ERROR, "position3d must fit the VARCHAR schema field.");
        if (request.veterinaryExaminationId() != null) {
            if (!exams.belongs(request.veterinaryExaminationId(), c.id, c.horseId))
                throw error(ErrorCode.NOT_FOUND, "Examination not found for this case and horse.");
        }
        InjuryEntity injury = new InjuryEntity();
        injury.medicalCaseId = c.id; injury.veterinaryExaminationId = request.veterinaryExaminationId();
        injury.bodyRegion = request.bodyRegion(); injury.position3d = request.position3d();
        injury.side = request.side(); injury.severity = request.severity();
        injury.recoveryStatus = request.recoveryStatus(); injury.createdAt = now();
        return injuryView(injuries.saveAndFlush(injury));
    }

    @Transactional
    public PlanView putPlan(long id, PlanPut request) {
        Actor actor = vet(); MedicalCaseEntity c = open(id);
        if (request == null || request.treatmentMedicationInstructions() == null || request.treatmentMedicationInstructions().isBlank())
            throw error(ErrorCode.VALIDATION_ERROR, "Treatment instructions required.");
        List<GroomInstruction> groomTasks = request.groomTasks();
        if (groomTasks != null) {
            Set<String> unique = new HashSet<>();
            for (GroomInstruction task : groomTasks) {
                if (task == null || task.instruction() == null || task.instruction().isBlank()
                        || task.taskDate() == null || task.assignedGroomUserId() == null || task.assignedGroomUserId() < 1
                        || task.instruction().length() > 2000
                        || task.scheduledSlot() != null && task.scheduledSlot().length() > 50) {
                    throw error(ErrorCode.VALIDATION_ERROR, "Treatment task fields are invalid.");
                }
                String key = task.assignedGroomUserId() + "\u0000" + task.taskDate() + "\u0000"
                        + Objects.toString(task.scheduledSlot(), "") + "\u0000" + task.instruction();
                if (!unique.add(key)) throw error(ErrorCode.VALIDATION_ERROR, "Duplicate treatment task entries are not allowed.");
            }
            if (!groomTasks.isEmpty() && taskMaterializers.getIfAvailable() == null) {
                throw error(ErrorCode.CONFLICT, "Assigned treatment task integration is unavailable until CORE-045 is integrated.");
            }
        }
        TreatmentPlanEntity p = plans.findByMedicalCaseId(c.id).orElseGet(() -> {
            TreatmentPlanEntity value = new TreatmentPlanEntity();
            value.medicalCaseId = c.id; value.createdByUserId = actor.id(); value.createdAt = now();
            return value;
        });
        p.treatmentMedicationInstructions = request.treatmentMedicationInstructions();
        p.restPeriod = request.restInstructions(); p.careInstructions = request.careInstructions();
        p.reExaminationDate = request.reexaminationDate(); p.updatedAt = now();
        try { p = plans.saveAndFlush(p); }
        catch (DataIntegrityViolationException ex) { throw error(ErrorCode.CONFLICT, "Treatment plan changed concurrently."); }
        if (groomTasks != null) {
            TreatmentTaskMaterializer materializer = taskMaterializers.getIfAvailable();
            if (materializer != null) {
                List<TreatmentTaskSpec> requested = groomTasks.stream().map(task -> new TreatmentTaskSpec(
                        task.instruction(), task.taskDate(), task.scheduledSlot(), task.assignedGroomUserId())).toList();
                materializer.reconcile(p.id, c.horseId, requested);
            }
        }
        return planView(p);
    }

    @Transactional(readOnly = true)
    public PlanView plan(long id) {
        vet(); MedicalCaseEntity c = lookup(id);
        return plans.findByMedicalCaseId(c.id).map(this::planView).orElseThrow(() -> error(ErrorCode.NOT_FOUND, "Treatment plan not found."));
    }

    @Transactional(readOnly = true)
    public Page<VetTaskView> caseTasks(long id, int page, int size) {
        vet(); MedicalCaseEntity c = lookup(id);
        if (page < 0 || size < 1 || size > 100) throw error(ErrorCode.VALIDATION_ERROR, "Invalid page or size.");
        Optional<TreatmentPlanEntity> plan = plans.findByMedicalCaseId(c.id);
        if (plan.isEmpty()) return new Page<>(List.of(), page, size, 0);
        org.springframework.data.domain.Page<TreatmentTaskEntity> result = tasks
                .findByTreatmentPlanIdOrderByTaskDateAscIdAsc(plan.get().id, PageRequest.of(page, size));
        return new Page<>(result.getContent().stream().map(t -> vetTaskView(t, c.id)).toList(),
                page, size, result.getTotalElements());
    }

    private InjuryView injuryView(InjuryEntity i) {
        return new InjuryView(i.id, i.medicalCaseId, i.bodyRegion, i.severity, i.recoveryStatus,
                i.veterinaryExaminationId, i.position3d, i.side);
    }
    private PlanView planView(TreatmentPlanEntity p) {
        return new PlanView(p.id, p.medicalCaseId, p.treatmentMedicationInstructions,
                p.restPeriod, p.careInstructions, p.reExaminationDate, instant(p.createdAt));
    }
    private VetTaskView vetTaskView(TreatmentTaskEntity t, long caseId) {
        return new VetTaskView(t.id, t.horseId, caseId, t.treatmentPlanId,
                t.instructionSnapshot, t.taskDate, t.scheduledSlot,
                t.completedAt == null ? "PENDING" : "COMPLETED",
                instant(t.completedAt), t.completedByUserId, t.assignedGroomUserId);
    }
    private static <T> Page<T> page(List<T> items, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw error(ErrorCode.VALIDATION_ERROR, "Invalid page or size.");
        long from = (long) page * size;
        return new Page<>(from >= items.size() ? List.of() : items.subList((int) from, (int) Math.min(items.size(), from + size)), page, size, items.size());
    }
}
