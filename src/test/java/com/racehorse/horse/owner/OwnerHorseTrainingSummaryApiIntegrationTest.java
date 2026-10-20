package com.racehorse.horse.owner;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:racehorse_owner_api_test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
class OwnerHorseTrainingSummaryApiIntegrationTest extends OwnerApiIntegrationTestSupport {

    @Test
    void returnsSelectedCurrentProgramAndTenNewestSessionsAcrossAllPrograms() throws Exception {
        long ownerId = createUser();
        long otherOwnerId = createUser();
        long programCreatorId = createUser();
        long sessionCreatorId = createUser();
        long workRiderId = createUser();
        long horseId = createHorse("Training Summary Horse", "FIT", null, null, null, null);
        long otherHorseId = createHorse("Other Owner Horse", "FIT", null, null, null, null);
        addOwnership(horseId, ownerId, "100.00", true);
        addOwnership(otherHorseId, otherOwnerId, "100.00", true);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDateTime olderActivation = today.atTime(8, 0);
        LocalDateTime latestActivation = today.atTime(9, 0);
        long olderProgramId = insertProgram(horseId, programCreatorId, "Older current program", "ACTIVE",
                today.minusDays(10), today.plusDays(10), olderActivation);
        insertProgram(horseId, programCreatorId, "Earlier tie program", "ACTIVE",
                today.minusDays(5), today.plusDays(5), latestActivation);
        long chosenProgramId = insertProgram(horseId, programCreatorId, "Selected current program", "ACTIVE",
                today.minusDays(5), today.plusDays(5), latestActivation);
        insertProgram(horseId, programCreatorId, "Higher ID but older activation", "ACTIVE",
                today.minusDays(5), today.plusDays(5), today.atTime(7, 0));
        insertProgram(horseId, programCreatorId, "Expired program", "ACTIVE",
                today.minusDays(20), today.minusDays(1), today.atTime(11, 0));
        insertProgram(horseId, programCreatorId, "Paused program", "PAUSED",
                today.minusDays(5), today.plusDays(5), today.atTime(12, 0));
        long otherProgramId = insertProgram(otherHorseId, programCreatorId, "Private other program", "ACTIVE",
                today.minusDays(5), today.plusDays(5), today.atTime(13, 0));

        long chosenPhaseId = insertPhase(chosenProgramId, "Current phase", 1,
                today.minusDays(1), today.plusDays(1));
        long otherPhaseId = insertPhase(olderProgramId, "Older phase", 1,
                today.minusDays(2), today.plusDays(2));
        long otherOwnerPhaseId = insertPhase(otherProgramId, "Private phase", 1,
                today.minusDays(1), today.plusDays(1));

        for (int index = 0; index < 12; index++) {
            long phaseId = index < 3 ? chosenPhaseId : otherPhaseId;
            String status = switch (index % 5) {
                case 0 -> "SCHEDULED";
                case 1 -> "COMPLETED";
                case 2 -> "PAUSED";
                case 3 -> "INTERRUPTED";
                default -> "IN_PROGRESS";
            };
            LocalDateTime scheduledStart = index <= 1
                    ? today.atTime(12, 0)
                    : today.atTime(12, 0).minusHours(index - 1);
            long sessionId = insertSession(phaseId, "TS-OWNER-" + index, sessionCreatorId,
                    workRiderId, scheduledStart, status);
            if (index == 1) {
                insertResult(sessionId, sessionCreatorId, "SATISFACTORY", "SECRET_EVALUATION_NOTES");
            }
        }
        long privateSessionId = insertSession(otherOwnerPhaseId, "TS-PRIVATE",
                sessionCreatorId, workRiderId, today.atTime(23, 0), "SCHEDULED");
        insertResult(privateSessionId, sessionCreatorId, "PRIVATE_LABEL", "PRIVATE_NOTES");

        String ownerToken = bearerToken(ownerId, RoleCode.HORSE_OWNER);
        MvcResult result = mockMvc.perform(get("/api/owner/horses/{id}/training-summary", horseId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentProgram.name").value("Selected current program"))
                .andExpect(jsonPath("$.data.currentProgram.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.currentProgram.periodStart").value(today.minusDays(5).toString()))
                .andExpect(jsonPath("$.data.currentProgram.periodEnd").value(today.plusDays(5).toString()))
                .andExpect(jsonPath("$.data.currentProgram.currentPhaseName").value("Current phase"))
                .andExpect(jsonPath("$.data.currentProgram.sessionsCompleted").value(1))
                .andExpect(jsonPath("$.data.currentProgram.sessionsPlanned").value(3))
                .andExpect(jsonPath("$.data.recentSessions.length()").value(10))
                .andExpect(jsonPath("$.data.recentSessions[0].sessionCode").value("TS-OWNER-1"))
                .andExpect(jsonPath("$.data.recentSessions[0].evaluationLabel").value("SATISFACTORY"))
                .andExpect(jsonPath("$.data.recentSessions[1].sessionCode").value("TS-OWNER-0"))
                .andExpect(jsonPath("$.data.recentSessions[9].sessionCode").value("TS-OWNER-9"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("SECRET_EVALUATION_NOTES"));
        assertFalse(body.contains("PRIVATE_NOTES"));
        assertFalse(body.contains("PRIVATE_LABEL"));
        assertFalse(body.contains("TS-OWNER-10"));
        assertFalse(body.contains("targetRace"));

        long noProgramHorseId = createHorse("No Program Horse", "FIT", null, null, null, null);
        addOwnership(noProgramHorseId, ownerId, "100.00", true);
        mockMvc.perform(get("/api/owner/horses/{id}/training-summary", noProgramHorseId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentProgram").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.recentSessions.length()").value(0));
    }

    @Test
    void enforcesOwnerAuthenticationAndRowScopedHorseAccess() throws Exception {
        long ownerId = createUser();
        long otherOwnerId = createUser();
        long horseId = createHorse("Training Summary Scope Horse", "FIT", null, null, null, null);
        addOwnership(horseId, otherOwnerId, "100.00", true);
        String ownerToken = bearerToken(ownerId, RoleCode.HORSE_OWNER);

        mockMvc.perform(get("/api/owner/horses/{id}/training-summary", horseId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/owner/horses/{id}/training-summary", horseId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/owner/horses/{id}/training-summary", 922337203685477000L)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        long managerId = createUser();
        mockMvc.perform(get("/api/owner/horses/{id}/training-summary", horseId)
                        .header("Authorization", "Bearer " + bearerToken(managerId, RoleCode.CLUB_MANAGER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/owner/horses/{id}/training-summary", 0)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void omitsCurrentPhaseWhenMultiplePhasesCoverToday() throws Exception {
        long ownerId = createUser();
        long programCreatorId = createUser();
        long horseId = createHorse("Overlapping Phases Horse", "FIT", null, null, null, null);
        addOwnership(horseId, ownerId, "100.00", true);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        long programId = insertProgram(horseId, programCreatorId, "Current program", "ACTIVE",
                today.minusDays(5), today.plusDays(5), today.atTime(9, 0));
        insertPhase(programId, "First matching phase", 1, today.minusDays(1), today.plusDays(1));
        insertPhase(programId, "Second matching phase", 2, today, today.plusDays(2));

        mockMvc.perform(get("/api/owner/horses/{id}/training-summary", horseId)
                        .header("Authorization", "Bearer " + bearerToken(ownerId, RoleCode.HORSE_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentProgram.currentPhaseName")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.currentProgram.sessionsCompleted").value(0))
                .andExpect(jsonPath("$.data.currentProgram.sessionsPlanned").value(0));
    }

    private long insertProgram(long horseId, long createdByUserId, String name, String status, LocalDate start, LocalDate end,
                               LocalDateTime activatedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.horse_training_program
                        (horse_id, template_version_id, created_by_user_id, name, status, period_start, period_end, activated_at)
                    VALUES (?, 1, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, horseId);
            statement.setLong(2, createdByUserId);
            statement.setString(3, name);
            statement.setString(4, status);
            statement.setObject(5, start);
            statement.setObject(6, end);
            statement.setObject(7, activatedAt);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private long insertPhase(long programId, String name, int order, LocalDate start, LocalDate end) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.horse_program_phase
                        (horse_training_program_id, name, phase_order, start_date, end_date)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, programId);
            statement.setString(2, name);
            statement.setInt(3, order);
            statement.setObject(4, start);
            statement.setObject(5, end);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private long insertSession(long phaseId, String code, long createdByUserId, long workRiderUserId,
                               LocalDateTime scheduledStart, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.training_session
                        (session_code, horse_program_phase_id, created_by_user_id, work_rider_user_id,
                         scheduled_start, status, interruption_reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, code);
            statement.setLong(2, phaseId);
            statement.setLong(3, createdByUserId);
            statement.setLong(4, workRiderUserId);
            statement.setObject(5, scheduledStart);
            statement.setString(6, status);
            statement.setString(7, "INTERRUPTED".equals(status) ? "Test interruption" : null);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private void insertResult(long sessionId, long evaluatedByUserId, String label, String notes) {
        jdbcTemplate.update("""
                INSERT INTO dbo.session_result
                    (training_session_id, evaluated_by_user_id, evaluation_label, evaluation_notes, evaluated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, sessionId, evaluatedByUserId, label, notes);
    }

    private static long generatedId(KeyHolder keyHolder) {
        Number value = keyHolder.getKey();
        if (value == null) {
            throw new IllegalStateException("The database did not return a generated identity.");
        }
        return value.longValue();
    }
}
