package com.racehorse.core035;

import com.racehorse.common.error.*;
import com.racehorse.core035.MedicalDtos.*;
import com.racehorse.core035.MedicalPorts.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service("medicalCaseService") // DEMO INTEGRATION: bean name clashed with com.racehorse.medical.MedicalService
public class MedicalService {
    private final MedicalCases cases;
    private final ExaminationStore exams;
    private final Injuries injuries;
    private final TreatmentPlans plans;
    private final TreatmentTasks tasks;
    private final JdbcTemplate sql;
    private final ObjectProvider<ActorResolver> actors;
    private final ObjectProvider<IncidentLink> incidents;
    private final ObjectProvider<GroomTaskScope> groomScope;

    MedicalService(MedicalCases cases, ExaminationStore exams, Injuries injuries, TreatmentPlans plans,
                   TreatmentTasks tasks, JdbcTemplate sql, ObjectProvider<ActorResolver> actors,
                   ObjectProvider<IncidentLink> incidents, ObjectProvider<GroomTaskScope> groomScope) {
        this.cases = cases; this.exams = exams; this.injuries = injuries; this.plans = plans;
        this.tasks = tasks; this.sql = sql; this.actors = actors; this.incidents = incidents;
        this.groomScope = groomScope;
    }

    static BusinessException error(ErrorCode code, String message) { return new BusinessException(code, message); }
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
    private Actor groom() {
        Actor actor = actor();
        if (!actor.has(Role.GROOM)) throw error(ErrorCode.FORBIDDEN, "Groom role required.");
        return actor;
    }
    private Set<Long> assigned(Actor actor) {
        GroomTaskScope scope = groomScope.getIfAvailable();
        if (scope == null) throw error(ErrorCode.FORBIDDEN, "Groom assignment source is unavailable.");
        Set<Long> ids = scope.assignedTaskIds(actor);
        if (ids == null) throw error(ErrorCode.FORBIDDEN, "Groom assignment source is unavailable.");
        return ids;
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
        Integer count = sql.queryForObject("select count(*) from dbo.trainer_assignment a join dbo.horse_training_program p on p.id=a.horse_training_program_id where p.horse_id=? and a.trainer_user_id=? and a.effective_from<=? and a.effective_to is null", Integer.class, horseId, actor.id(), LocalDate.now(ZoneOffset.UTC));
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
        if (request.incidentId() != null && request.incidentId() < 1)
            throw error(ErrorCode.VALIDATION_ERROR, "Positive incident ID required.");
        if (request.incidentId() != null && incidents.getIfAvailable() == null)
            throw error(ErrorCode.CONFLICT, "Incident link integration is unavailable.");
        MedicalCaseEntity c = cases.saveAndFlush(new MedicalCaseEntity(request.horseId(), actor.id(), request.reason(), now()));
        if (request.incidentId() != null) incidents.getObject().link(request.incidentId(), c.horseId, c.id, actor);
        return detail(c);
    }

    @Transactional(readOnly = true)
    public Page<Object> list(int page, int size) {
        Actor actor = actor(); readRole(actor);
        if (page < 0 || size < 1 || size > 100) throw error(ErrorCode.VALIDATION_ERROR, "Invalid page or size.");
        boolean unrestricted = actor.has(Role.VETERINARIAN) || actor.has(Role.CLUB_MANAGER) || actor.has(Role.HEAD_TRAINER);
        String where = unrestricted ? "" : " where exists (select 1 from dbo.horse_training_program p join dbo.trainer_assignment a on a.horse_training_program_id=p.id where p.horse_id=c.horse_id and a.trainer_user_id=? and a.effective_from<=? and a.effective_to is null)";
        Object[] scope = unrestricted ? new Object[]{} : new Object[]{actor.id(), LocalDate.now(ZoneOffset.UTC)};
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
        c.status = CaseStatus.CLOSED;
        LocalDateTime stamp = now();
        c.closedAt = stamp.isBefore(c.openedAt) ? c.openedAt : stamp;
        c.updatedAt = c.closedAt;
        cases.flush();
        return detail(c);
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
        TreatmentPlanEntity p = plans.findByMedicalCaseId(c.id).orElseGet(() -> {
            TreatmentPlanEntity value = new TreatmentPlanEntity();
            value.medicalCaseId = c.id; value.createdByUserId = actor.id(); value.createdAt = now();
            return value;
        });
        p.treatmentMedicationInstructions = request.treatmentMedicationInstructions();
        p.restPeriod = request.restInstructions(); p.careInstructions = request.careInstructions();
        p.reExaminationDate = request.reexaminationDate(); p.updatedAt = now();
        TreatmentPlanEntity saved;
        try { saved = plans.saveAndFlush(p); }
        catch (DataIntegrityViolationException ex) { throw error(ErrorCode.CONFLICT, "Treatment plan changed concurrently."); }
        // DEMO INTEGRATION (DEC-009 §2): treatment-linked care tasks for the Groom pool (care_task.treatment_plan_id set).
        if (request.groomTasks() != null) for (GroomInstruction g : request.groomTasks())
            sql.update("insert into dbo.care_task(horse_id,treatment_plan_id,task_date,scheduled_slot,instruction_snapshot,created_at) values (?,?,?,?,?,?)",
                    c.horseId, saved.id, g.taskDate(), g.scheduledSlot(), g.instruction(), now());
        return planView(saved);
    }

    @Transactional(readOnly = true)
    public PlanView plan(long id) {
        vet(); MedicalCaseEntity c = lookup(id);
        return plans.findByMedicalCaseId(c.id).map(this::planView).orElseThrow(() -> error(ErrorCode.NOT_FOUND, "Treatment plan not found."));
    }

    @Transactional(readOnly = true)
    public Page<VetTaskView> caseTasks(long id, int page, int size) {
        vet(); MedicalCaseEntity c = lookup(id);
        List<VetTaskView> rows = plans.findByMedicalCaseId(c.id).map(p -> tasks.findByTreatmentPlanIdOrderByTaskDateAscIdAsc(p.id).stream().map(t -> vetTaskView(t, c.id)).toList()).orElse(List.of());
        return page(rows, page, size);
    }

    @Transactional(readOnly = true)
    public Page<TaskView> groomTasks(int page, int size) {
        Actor actor = groom(); Set<Long> ids = assigned(actor);
        return page(ids.isEmpty() ? List.of() : tasks.assigned(ids).stream().map(this::taskView).toList(), page, size);
    }

    @Transactional
    public TaskView complete(long id) {
        Actor actor = groom(); positive(id);
        if (!assigned(actor).contains(id)) throw error(ErrorCode.FORBIDDEN, "Task is not assigned to this Groom.");
        TreatmentTaskEntity task = tasks.locked(id).orElseThrow(() -> error(ErrorCode.NOT_FOUND, "Task not found."));
        if (task.treatmentPlanId == null) throw error(ErrorCode.FORBIDDEN, "Task is not a treatment task.");
        if (task.completedAt != null) throw error(ErrorCode.CONFLICT, "Task already completed.");
        task.completedByUserId = actor.id(); task.completedAt = now(); task.updatedAt = task.completedAt;
        tasks.flush(); return taskView(task);
    }

    private InjuryView injuryView(InjuryEntity i) {
        return new InjuryView(i.id, i.medicalCaseId, i.bodyRegion, i.severity, i.recoveryStatus,
                i.veterinaryExaminationId, i.position3d, i.side);
    }
    private PlanView planView(TreatmentPlanEntity p) {
        return new PlanView(p.id, p.medicalCaseId, p.treatmentMedicationInstructions,
                p.restPeriod, p.careInstructions, p.reExaminationDate, instant(p.createdAt));
    }
    private TaskView taskView(TreatmentTaskEntity t) {
        if (t.instructionSnapshot == null) throw error(ErrorCode.CONFLICT, "Safe task instruction is unavailable.");
        return new TaskView(t.id, t.horseId, t.instructionSnapshot, t.taskDate, t.scheduledSlot,
                t.completedAt == null ? "PENDING" : "COMPLETED", instant(t.completedAt));
    }
    private VetTaskView vetTaskView(TreatmentTaskEntity t, long caseId) {
        TaskView safe = taskView(t);
        return new VetTaskView(safe.id(), safe.horseId(), caseId, t.treatmentPlanId,
                safe.instruction(), safe.taskDate(), safe.scheduledSlot(), safe.status(),
                safe.completedAt(), t.completedByUserId);
    }
    private static <T> Page<T> page(List<T> items, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw error(ErrorCode.VALIDATION_ERROR, "Invalid page or size.");
        long from = (long) page * size;
        return new Page<>(from >= items.size() ? List.of() : items.subList((int) from, (int) Math.min(items.size(), from + size)), page, size, items.size());
    }
}
