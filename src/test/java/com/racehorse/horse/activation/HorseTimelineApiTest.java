package com.racehorse.horse.activation;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HorseTimelineApiTest extends HorseActivationTestSupport {

    private final List<Long> timelineHorseIds = new ArrayList<>();

    @AfterEach
    void cleanTimelineData() {
        for (long horseId : timelineHorseIds) {
            jdbc.update("DELETE FROM dbo.incident WHERE horse_id = ?", horseId);
            jdbc.update("""
                    DELETE FROM dbo.training_session
                    WHERE horse_program_phase_id IN (
                        SELECT ph.id FROM dbo.horse_program_phase ph
                        JOIN dbo.horse_training_program p ON p.id = ph.horse_training_program_id
                        WHERE p.horse_id = ?
                    )
                    """, horseId);
            jdbc.update("""
                    DELETE FROM dbo.trainer_assignment
                    WHERE horse_training_program_id IN (
                        SELECT id FROM dbo.horse_training_program WHERE horse_id = ?
                    )
                    """, horseId);
            jdbc.update("""
                    DELETE FROM dbo.horse_program_phase
                    WHERE horse_training_program_id IN (
                        SELECT id FROM dbo.horse_training_program WHERE horse_id = ?
                    )
                    """, horseId);
            jdbc.update("DELETE FROM dbo.horse_training_program WHERE horse_id = ?", horseId);
            jdbc.update("DELETE FROM dbo.medical_restriction WHERE horse_id = ?", horseId);
        }
        timelineHorseIds.clear();
    }

    @Test
    void assemblesApprovedSourcesAfterActivationWithStableDateAndTieOrdering() throws Exception {
        long horseId = createActivatedHorse();
        long managerId = createAccount(RoleCode.CLUB_MANAGER);
        long ownerId = createAccount(RoleCode.HORSE_OWNER);
        long veterinarianId = createAccount(RoleCode.VETERINARIAN);
        long stableBoxId = createStableBox();

        jdbc.update("""
                INSERT INTO dbo.horse_ownership
                    (horse_id, owner_user_id, share_percentage, effective_from)
                VALUES (?, ?, 100.00, ?)
                """, horseId, ownerId, LocalDate.of(2026, 2, 3));
        jdbc.update("""
                INSERT INTO dbo.horse_stable_assignment
                    (horse_id, stable_box_id, assigned_by_user_id, effective_from)
                VALUES (?, ?, ?, ?)
                """, horseId, stableBoxId, managerId, LocalDate.of(2026, 2, 3));
        jdbc.update("""
                INSERT INTO dbo.horse_ownership
                    (horse_id, owner_user_id, share_percentage, effective_from)
                VALUES (?, ?, 100.00, ?)
                """, horseId, ownerId, LocalDate.of(2026, 2, 1));

        long examinationId = insertExamination(horseId, veterinarianId, at(5, 9));
        long restrictionId = insertRestriction(horseId, veterinarianId, at(6, 11), at(7, 12));
        long programId = insertProgram(horseId, at(8, 8));
        long phaseId = insertPhase(programId);
        insertSession(phaseId, "TIMELINE-SCHEDULED", at(9, 8), null);
        insertSession(phaseId, "TIMELINE-STARTED", at(9, 8), at(9, 9));
        insertIncident(horseId, at(11, 10), at(10, 10));

        MvcResult result = mvc.perform(get("/api/horses/{id}/timeline", horseId)
                        .param("page", "0")
                        .param("size", "20")
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(9))
                .andExpect(jsonPath("$.data.items.length()").value(9))
                .andExpect(jsonPath("$.data.items[0].eventType").value("INCIDENT_REPORTED"))
                .andExpect(jsonPath("$.data.items[0].occurredAt").value("2026-02-10T10:00:00Z"))
                .andExpect(jsonPath("$.data.items[1].eventType").value("TRAINING_SESSION"))
                .andExpect(jsonPath("$.data.items[2].eventType").value("PROGRAM_ACTIVATED"))
                .andExpect(jsonPath("$.data.items[3].eventType").value("RESTRICTION_CHANGED"))
                .andExpect(jsonPath("$.data.items[3].title").value("Medical restriction lifted"))
                .andExpect(jsonPath("$.data.items[4].title").value("Medical restriction applied"))
                .andExpect(jsonPath("$.data.items[5].eventType").value("VETERINARY_EXAMINATION"))
                .andExpect(jsonPath("$.data.items[6].eventType").value("OWNERSHIP_CHANGED"))
                .andExpect(jsonPath("$.data.items[6].occurredAt").value("2026-02-03T00:00:00Z"))
                .andExpect(jsonPath("$.data.items[7].eventType").value("STABLE_ASSIGNED"))
                .andExpect(jsonPath("$.data.items[7].occurredAt").value("2026-02-03T00:00:00Z"))
                .andExpect(jsonPath("$.data.items[8].eventType").value("HORSE_ACTIVATED"))
                .andExpect(jsonPath("$.data.items[8].occurredAt").value("2026-02-01T10:00:00Z"))
                .andExpect(jsonPath("$.data.items[6].targetType").value("OWNERSHIP"))
                .andExpect(jsonPath("$.data.items[7].targetType").value("STABLE_ASSIGNMENT"))
                .andExpect(jsonPath("$.data.items[5].targetId").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].title").value("Incident reported"))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("clinical"));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("diagnosis"));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("restriction reason"));

        mvc.perform(get("/api/horses/{id}/timeline", horseId)
                        .param("page", "0")
                        .param("size", "2")
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(9))
                .andExpect(jsonPath("$.data.totalPages").value(5))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].eventType").value("INCIDENT_REPORTED"));

        mvc.perform(get("/api/horses/{id}/timeline", horseId)
                        .param("page", "1")
                        .param("size", "2")
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].eventType").value("PROGRAM_ACTIVATED"));

        mvc.perform(get("/api/horses/{id}/timeline", horseId)
                        .with(authenticatedAs(RoleCode.VETERINARIAN, veterinarianId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[5].targetType").value("VETERINARY_EXAMINATION"))
                .andExpect(jsonPath("$.data.items[5].targetId").value(examinationId))
                .andExpect(jsonPath("$.data.items[3].targetType").value("MEDICAL_RESTRICTION"))
                .andExpect(jsonPath("$.data.items[3].targetId").value(restrictionId));
    }

    @Test
    void returnsEmptyForDraftAndEnforcesAuthenticationRoleAndHorseScope() throws Exception {
        long draftHorseId = createHorse("Unactivated timeline horse");
        long managerId = createAccount(RoleCode.CLUB_MANAGER);
        long trainerId = createAccount(RoleCode.TRAINER);

        mvc.perform(get("/api/horses/{id}/timeline", draftHorseId)
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        mvc.perform(get("/api/horses/{id}/timeline", draftHorseId)
                        .param("size", "101")
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/horses/{id}/timeline", draftHorseId))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/horses/{id}/timeline", draftHorseId)
                        .with(authenticatedAs(RoleCode.HORSE_OWNER, createAccount(RoleCode.HORSE_OWNER))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/horses/{id}/timeline", Long.MAX_VALUE)
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        long activeHorseId = createActivatedHorse();
        mvc.perform(get("/api/horses/{id}/timeline", activeHorseId)
                        .with(authenticatedAs(RoleCode.TRAINER, trainerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        long ownerId = createAccount(RoleCode.HORSE_OWNER);
        long stableBoxId = createStableBox();
        jdbc.update("""
                INSERT INTO dbo.horse_ownership
                    (horse_id, owner_user_id, share_percentage, effective_from)
                VALUES (?, ?, 100.00, ?)
                """, activeHorseId, ownerId, LocalDate.of(2026, 2, 2));
        jdbc.update("""
                INSERT INTO dbo.horse_stable_assignment
                    (horse_id, stable_box_id, assigned_by_user_id, effective_from)
                VALUES (?, ?, ?, ?)
                """, activeHorseId, stableBoxId, managerId, LocalDate.of(2026, 2, 2));
        long programId = insertProgram(activeHorseId, at(1, 11));
        jdbc.update("""
                INSERT INTO dbo.trainer_assignment (horse_training_program_id, trainer_user_id)
                VALUES (?, ?)
                """, programId, trainerId);

        mvc.perform(get("/api/horses/{id}/timeline", activeHorseId)
                        .with(authenticatedAs(RoleCode.TRAINER, trainerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].eventType").value("OWNERSHIP_CHANGED"))
                .andExpect(jsonPath("$.data.items[0].targetType").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].targetId").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].eventType").value("STABLE_ASSIGNED"))
                .andExpect(jsonPath("$.data.items[1].targetType").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].targetId").value(nullValue()))
                .andExpect(jsonPath("$.data.items[3].eventType").value("HORSE_ACTIVATED"));
    }

    @Test
    void exposesTimelineOperationAndApprovedEventVocabularyInSwagger() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/horses/{id}/timeline'].get.operationId")
                        .value("getHorseTimeline"))
                .andExpect(jsonPath("$.components.schemas.TimelineEvent.properties.eventType.enum.length()")
                        .value(8));
    }

    private long createActivatedHorse() {
        long horseId = createHorse("Timeline horse");
        timelineHorseIds.add(horseId);
        jdbc.update("""
                UPDATE dbo.horse
                SET lifecycle = 'ACTIVE', activated_at = ?
                WHERE id = ?
                """, at(1, 10), horseId);
        return horseId;
    }

    private long insertExamination(long horseId, long veterinarianId, LocalDateTime examinedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.veterinary_examination
                        (horse_id, medical_case_id, performed_by_user_id, exam_type, examined_at)
                    VALUES (?, NULL, ?, 'BASELINE', ?)
                    """, new String[]{"id"});
            statement.setLong(1, horseId);
            statement.setLong(2, veterinarianId);
            statement.setObject(3, examinedAt);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private long insertRestriction(long horseId, long veterinarianId,
                                   LocalDateTime effectiveFrom, LocalDateTime liftedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.medical_restriction
                        (horse_id, set_by_user_id, restriction_level, reason, effective_from, lifted_at)
                    VALUES (?, ?, 'LIMITED', 'private restriction reason', ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, horseId);
            statement.setLong(2, veterinarianId);
            statement.setObject(3, effectiveFrom);
            statement.setObject(4, liftedAt);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private long insertProgram(long horseId, LocalDateTime activatedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.horse_training_program (horse_id, name, status, activated_at)
                    VALUES (?, 'Test program', 'ACTIVE', ?)
                    """, new String[]{"id"});
            statement.setLong(1, horseId);
            statement.setObject(2, activatedAt);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private long insertPhase(long programId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO dbo.horse_program_phase (horse_training_program_id) VALUES (?)",
                    new String[]{"id"});
            statement.setLong(1, programId);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private void insertSession(long phaseId, String code, LocalDateTime scheduledStart,
                               LocalDateTime actualStart) {
        jdbc.update("""
                INSERT INTO dbo.training_session
                    (session_code, horse_program_phase_id, scheduled_start, actual_start, status)
                VALUES (?, ?, ?, ?, ?)
                """, code, phaseId, scheduledStart, actualStart,
                actualStart == null ? "SCHEDULED" : "COMPLETED");
    }

    private void insertIncident(long horseId, LocalDateTime observedAt, LocalDateTime reportedAt) {
        jdbc.update("""
                INSERT INTO dbo.incident (horse_id, observed_at, reported_at)
                VALUES (?, ?, ?)
                """, horseId, observedAt, reportedAt);
    }

    private static LocalDateTime at(int day, int hour) {
        return LocalDateTime.of(2026, 2, day, hour, 0);
    }

    private static long generatedId(KeyHolder keyHolder) {
        Number value = (Number) keyHolder.getKeys().entrySet().stream()
                .filter(entry -> "id".equalsIgnoreCase(entry.getKey()))
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
        return value.longValue();
    }
}
