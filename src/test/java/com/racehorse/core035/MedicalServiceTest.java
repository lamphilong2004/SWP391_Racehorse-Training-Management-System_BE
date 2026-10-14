package com.racehorse.core035;

import com.racehorse.common.error.*;
import com.racehorse.core035.MedicalDtos.*;
import com.racehorse.core035.MedicalPorts.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/core035-h2.sql")
class MedicalServiceTest {
    static final AtomicReference<Actor> CURRENT = new AtomicReference<>(new Actor(10, Set.of(Role.VETERINARIAN)));
    static final Set<Long> ASSIGNED = new HashSet<>();
    @TestConfiguration static class Adapters {
        @Bean ActorResolver actorResolver() { return CURRENT::get; }
        @Bean GroomTaskScope groomTaskScope() { return actor -> Set.copyOf(ASSIGNED); }
    }
    @Autowired MedicalService service;
    @Autowired JdbcTemplate sql;

    @BeforeEach void reset() { CURRENT.set(new Actor(10, Set.of(Role.VETERINARIAN))); ASSIGNED.clear(); }
    private CaseDetail opened() { return service.create(new CaseCreate(1L, null, "Left foreleg tenderness")); }
    private BusinessException fails(ErrorCode code, org.junit.jupiter.api.function.Executable call) {
        BusinessException e = assertThrows(BusinessException.class, call);
        assertEquals(code, e.code()); return e;
    }
    @Test void createsAndPersistsCaseWithServerActor() {
        CaseDetail c = opened();
        assertEquals(CaseStatus.OPEN, c.status());
        assertEquals(10L, sql.queryForObject("select opened_by_user_id from dbo.medical_case where id=?", Long.class, c.id()));
        assertEquals("Left foreleg tenderness", service.get(c.id()) instanceof CaseDetail d ? d.reason() : null);
    }
    @Test void missingHorseAndIncidentBoundary() {
        fails(ErrorCode.NOT_FOUND, () -> service.create(new CaseCreate(99L, null, "Concern")));
        fails(ErrorCode.CONFLICT, () -> service.create(new CaseCreate(1L, 5L, "Concern")));
        assertEquals(0, sql.queryForObject("select count(*) from dbo.medical_case", Integer.class));
    }
    @Test void closeIsOneWayAndPreventsClinicalEdits() {
        long id = opened().id();
        assertEquals(CaseStatus.CLOSED, service.close(id).status());
        fails(ErrorCode.CONFLICT, () -> service.close(id));
        fails(ErrorCode.CONFLICT, () -> service.examine(id, new ExamCreate("Sound", null, null, Instant.now(), null, null)));
    }
    @Test void summaryNeverExposesClinicalFieldsAndTrainerIsScoped() {
        long id = opened().id();
        CURRENT.set(new Actor(20, Set.of(Role.HEAD_TRAINER)));
        assertInstanceOf(CaseSummary.class, service.get(id));
        CURRENT.set(new Actor(21, Set.of(Role.TRAINER)));
        fails(ErrorCode.FORBIDDEN, () -> service.get(id));
        assertEquals(0, service.list(0, 20).totalElements());
        sql.update("insert into dbo.horse_training_program(id,horse_id) values (4,1)");
        sql.update("insert into dbo.trainer_assignment(id,horse_training_program_id,trainer_user_id,effective_from) values (5,4,21,?)", LocalDate.now(ZoneOffset.UTC));
        assertInstanceOf(CaseSummary.class, service.get(id));
        assertEquals(1, service.list(0, 20).totalElements());
    }
    @Test void examinationAndReexaminationPersistAndAreSeparateHistory() {
        long id = opened().id(); Instant stamp = Instant.parse("2026-10-13T08:11:00Z");
        ExamView e = service.examine(id, new ExamCreate("Swelling", "Strain", ClinicalSeverity.MILD, stamp, HealthStatus.INJURED, null));
        assertEquals("EXAMINATION", sql.queryForObject("select exam_type from dbo.veterinary_examination where id=?", String.class, e.id()));
        assertEquals("INJURED", sql.queryForObject("select health_status from dbo.horse where id=1", String.class));
        service.reexamine(id, new ReexamCreate(RecoveryStatus.IMPROVING, "Less swelling", stamp.plusSeconds(86400), null, null, null, null));
        CaseDetail detail = (CaseDetail) service.get(id);
        assertEquals(1, detail.examinations().size()); assertEquals(1, detail.reexaminations().size());
        assertEquals(RecoveryStatus.IMPROVING, detail.reexaminations().getFirst().recoveryStatus());
    }
    @Test void invalidExamAndRecoveryRequestsAreRejected() {
        long id = opened().id();
        fails(ErrorCode.VALIDATION_ERROR, () -> service.examine(id, new ExamCreate("", null, null, Instant.now(), null, null)));
        fails(ErrorCode.VALIDATION_ERROR, () -> service.reexamine(id, new ReexamCreate(null, "Finding", Instant.now(), null, null, null, null)));
    }
    @Test void recoveredOutcomeDoesNotChangeHorseAvailabilityOrRequireRestrictionTable() {
        long id = opened().id();
        service.reexamine(id, new ReexamCreate(RecoveryStatus.RECOVERED, "Sound on palpation",
                Instant.now(), null, null, null, null));
        assertEquals(RecoveryStatus.RECOVERED, ((CaseDetail) service.get(id)).reexaminations().getFirst().recoveryStatus());
        assertNull(sql.queryForObject("select health_status from dbo.horse where id=1", String.class));
    }
    @Test void injuryValidatesCaseAndExaminationRelationship() {
        long id = opened().id();
        InjuryView injury = service.injure(id, new InjuryCreate("Foreleg", ClinicalSeverity.MODERATE, RecoveryStatus.NOT_RECOVERED, null, "marker-1", "left"));
        assertEquals("Foreleg", sql.queryForObject("select body_region from dbo.injury where id=?", String.class, injury.id()));
        fails(ErrorCode.NOT_FOUND, () -> service.injure(999, new InjuryCreate("Leg", ClinicalSeverity.MILD, RecoveryStatus.IMPROVING, null, null, null)));
        fails(ErrorCode.NOT_FOUND, () -> service.injure(id, new InjuryCreate("Leg", ClinicalSeverity.MILD, RecoveryStatus.IMPROVING, 999L, null, null)));
        fails(ErrorCode.VALIDATION_ERROR, () -> service.injure(id, new InjuryCreate("Leg", ClinicalSeverity.MILD, RecoveryStatus.IMPROVING, null, "é", null)));
    }
    @Test void planUpsertPersistsAndRejectsUnsupportedTaskCreation() {
        long id = opened().id();
        PlanView first = service.putPlan(id, new PlanPut("Vet medication guidance", "2 days", "Apply dressing", null, LocalDate.now()));
        PlanView second = service.putPlan(id, new PlanPut("Revised guidance", null, "Check bandage", null, null));
        assertEquals(first.id(), second.id());
        assertEquals("Revised guidance", service.plan(id).treatmentMedicationInstructions());
        fails(ErrorCode.CONFLICT, () -> service.putPlan(id, new PlanPut("x", null, null, List.of(new GroomInstruction("Wash", LocalDate.now(), null)), null)));
        assertEquals(1, sql.queryForObject("select count(*) from dbo.treatment_plan", Integer.class));
    }
    @Test void groomScopeAndCompletionAreIsolated() {
        long id = opened().id();
        long plan = service.putPlan(id, new PlanPut("Vet only", null, "Safe care", null, null)).id();
        sql.update("insert into dbo.care_task(horse_id,treatment_plan_id,task_date,instruction_snapshot,created_at) values (1,?,?,?,?)", plan, LocalDate.now(), "Change dressing", LocalDateTime.now());
        long taskId = sql.queryForObject("select id from dbo.care_task", Long.class);
        CURRENT.set(new Actor(30, Set.of(Role.GROOM)));
        assertEquals(0, service.groomTasks(0, 20).totalElements());
        fails(ErrorCode.FORBIDDEN, () -> service.complete(taskId));
        ASSIGNED.add(taskId);
        TaskView safe = service.groomTasks(0, 20).items().getFirst();
        assertEquals("Change dressing", safe.instruction());
        assertFalse(safe.toString().contains("Vet only"));
        assertEquals("COMPLETED", service.complete(taskId).status());
        assertEquals(30L, sql.queryForObject("select completed_by_user_id from dbo.care_task where id=?", Long.class, taskId));
        fails(ErrorCode.CONFLICT, () -> service.complete(taskId));
    }
    @Test void unauthorizedRolesCannotWriteOrReadClinicalData() {
        long id = opened().id(); CURRENT.set(new Actor(20, Set.of(Role.GROOM)));
        fails(ErrorCode.FORBIDDEN, () -> service.create(new CaseCreate(1L, null, "x")));
        fails(ErrorCode.FORBIDDEN, () -> service.examine(id, new ExamCreate("Findings", null, null, Instant.now(), null, null)));
        fails(ErrorCode.FORBIDDEN, () -> service.get(id));
    }
}
