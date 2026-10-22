package com.racehorse.core035;

import com.racehorse.common.error.*;
import com.racehorse.TestJwtProperties;
import com.racehorse.core035.MedicalDtos.*;
import com.racehorse.core035.MedicalPorts.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
    @TestConfiguration static class Adapters {
        @Bean @Primary ActorResolver actorResolver() { return CURRENT::get; }
    }
    @Autowired MedicalService service;
    @Autowired JdbcTemplate sql;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwtProperties.register(registry);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:core035_medical_service;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach void reset() { CURRENT.set(new Actor(10, Set.of(Role.VETERINARIAN))); }
    private CaseDetail opened() { return service.create(new CaseCreate(1L, "Left foreleg tenderness")); }
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
    @Test void herdCountsKeepUnassessedSeparateAndRejectOtherRoles() {
        sql.update("INSERT INTO dbo.horse(id,health_status) VALUES (2,'FIT'),(3,'INJURED')");
        HerdHealthOverview overview=service.herdHealth();
        assertEquals(3,overview.totalHorses());assertEquals(1,overview.fit());assertEquals(1,overview.injured());assertEquals(1,overview.unassessed());
        CURRENT.set(new Actor(20,Set.of(Role.HEAD_TRAINER)));
        fails(ErrorCode.FORBIDDEN,()->service.herdHealth());
    }
    @Test void missingHorseAndIncidentBoundary() {
        fails(ErrorCode.NOT_FOUND, () -> service.create(new CaseCreate(99L, "Concern")));
        assertEquals(0, sql.queryForObject("select count(*) from dbo.medical_case", Integer.class));
    }
    @Test void closeIsOneWayAndPreventsClinicalEdits() {
        long id = opened().id();
        fails(ErrorCode.BUSINESS_RULE_VIOLATION, () -> service.close(id));
        service.examine(id, new ExamCreate("Initial exam", null, null, Instant.now(), null, null));
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
    @Test void planUpsertPersistsAndFailsClosedUntilAssignmentIntegrationExists() {
        long id = opened().id();
        PlanView first = service.putPlan(id, new PlanPut("Vet medication guidance", "2 days", "Apply dressing", null, LocalDate.now()));
        PlanView second = service.putPlan(id, new PlanPut("Revised guidance", null, "Check bandage", null, null));
        assertEquals(first.id(), second.id());
        assertEquals("Revised guidance", service.plan(id).treatmentMedicationInstructions());
        fails(ErrorCode.CONFLICT, () -> service.putPlan(id, new PlanPut("x", null, null,
                List.of(new GroomInstruction("Wash", LocalDate.now(), null, 30L)), null)));
        assertEquals(1, sql.queryForObject("select count(*) from dbo.treatment_plan", Integer.class));
    }
    @Test void closureRequiresRecoveryTasksRestrictionsAndIncidentsToBeResolved() {
        long id = opened().id();
        service.examine(id, new ExamCreate("Initial exam", null, null, Instant.now(), null, null));
        service.injure(id, new InjuryCreate("Foreleg", ClinicalSeverity.MODERATE,
                RecoveryStatus.NOT_RECOVERED, null, null, null));
        fails(ErrorCode.BUSINESS_RULE_VIOLATION, () -> service.close(id));
        service.reexamine(id, new ReexamCreate(RecoveryStatus.RECOVERED, "Recovered", Instant.now(), null, null, null, null));
        sql.update("insert into dbo.medical_restriction(id,medical_case_id,lifted_at) values (1,?,null)", id);
        fails(ErrorCode.BUSINESS_RULE_VIOLATION, () -> service.close(id));
        sql.update("update dbo.medical_restriction set lifted_at=? where id=1", LocalDateTime.now());
        sql.update("insert into dbo.incident(id,medical_case_id,status) values (1,?,'UNDER_REVIEW')", id);
        fails(ErrorCode.BUSINESS_RULE_VIOLATION, () -> service.close(id));
        sql.update("update dbo.incident set status='RESOLVED' where id=1");
        assertEquals(CaseStatus.CLOSED, service.close(id).status());
    }

    @Test void treatmentPlanClosureWaitsForEveryTaskCompletion() {
        long id = opened().id();
        service.examine(id, new ExamCreate("Initial exam", null, null, Instant.now(), null, null));
        long planId = service.putPlan(id, new PlanPut("Vet instructions", null, null, null, null)).id();
        service.reexamine(id, new ReexamCreate(RecoveryStatus.RECOVERED, "Recovered", Instant.now(), null, null, null, null));
        sql.update("insert into dbo.care_task(horse_id,treatment_plan_id,task_date,instruction_snapshot,created_at) values (1,?,?,?,?)", planId, LocalDate.now(), "Care", LocalDateTime.now());
        fails(ErrorCode.BUSINESS_RULE_VIOLATION, () -> service.close(id));
        sql.update("update dbo.care_task set completed_at=?,completed_by_user_id=10", LocalDateTime.now());
        assertEquals(CaseStatus.CLOSED, service.close(id).status());
    }
    @Test void unauthorizedRolesCannotWriteOrReadClinicalData() {
        long id = opened().id(); CURRENT.set(new Actor(20, Set.of(Role.GROOM)));
        fails(ErrorCode.FORBIDDEN, () -> service.create(new CaseCreate(1L, "x")));
        fails(ErrorCode.FORBIDDEN, () -> service.examine(id, new ExamCreate("Findings", null, null, Instant.now(), null, null)));
        fails(ErrorCode.FORBIDDEN, () -> service.get(id));
    }
}
