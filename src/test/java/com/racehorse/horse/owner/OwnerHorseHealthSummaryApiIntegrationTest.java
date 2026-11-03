package com.racehorse.horse.owner;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:racehorse_owner_api_test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
class OwnerHorseHealthSummaryApiIntegrationTest extends OwnerApiIntegrationTestSupport {

    @Test
    void returnsOnlyTheNonClinicalOwnerProjectionWithNewestTwelveWeights() throws Exception {
        long ownerId = createUser();
        long otherOwnerId = createUser();
        long horseId = createHorse("Silver Comet", "MONITORING", null, null, null, null);
        addOwnership(horseId, ownerId, "100.00", true);
        jdbcTemplate.update("""
                INSERT INTO dbo.medical_restriction
                    (horse_id, set_by_user_id, restriction_level, reason, effective_from, review_date)
                VALUES (?, ?, 'LOCKED', 'SECRET_RESTRICTION_REASON', ?, DATE '2026-08-12')
                """, horseId, ownerId, LocalDateTime.ofInstant(
                Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC));
        long caseId = insertOpenCase(horseId, ownerId);
        insertExamination(horseId, caseId, ownerId, "RE_EXAMINATION",
                Instant.parse("2026-06-01T10:15:30Z"), "IMPROVING",
                "SECRET_DIAGNOSIS", "SECRET_FINDINGS");
        insertExamination(horseId, caseId, ownerId, "EXAMINATION",
                Instant.parse("2026-06-05T10:15:30Z"), null,
                "SECRET_LATER_DIAGNOSIS", "SECRET_LATER_FINDINGS");
        for (int day = 0; day < 13; day++) {
            insertWeight(horseId, ownerId, new BigDecimal("450.25").add(BigDecimal.valueOf(day)),
                    Instant.parse("2026-01-01T00:00:00Z").plusSeconds(day * 86_400L));
        }

        String ownerToken = bearerToken(ownerId, RoleCode.HORSE_OWNER);
        MvcResult result = mockMvc.perform(get("/api/owner/horses/{id}/health-summary", horseId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.healthStatus").value("MONITORING"))
                .andExpect(jsonPath("$.data.trainingAvailability").value("LOCKED"))
                .andExpect(jsonPath("$.data.restrictionReviewDate").value("2026-08-12"))
                .andExpect(jsonPath("$.data.recoveryStatus").value("IMPROVING"))
                .andExpect(jsonPath("$.data.lastExaminedAt").value("2026-06-05T10:15:30Z"))
                .andExpect(jsonPath("$.data.weights.length()").value(12))
                .andExpect(jsonPath("$.data.weights[0].weightKg").value(462.25))
                .andExpect(jsonPath("$.data.weights[11].weightKg").value(451.25))
                .andExpect(jsonPath("$.data.weights[0].recordedAt").value("2026-01-13T00:00:00Z"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("SECRET_RESTRICTION_REASON"));
        assertFalse(body.contains("SECRET_DIAGNOSIS"));
        assertFalse(body.contains("SECRET_FINDINGS"));
        assertFalse(body.contains("SECRET_LATER_DIAGNOSIS"));
        assertFalse(body.contains("SECRET_LATER_FINDINGS"));
        assertFalse(body.contains("treatmentMedicationInstructions"));

        mockMvc.perform(get("/api/owner/horses/{id}/health-summary", horseId)
                        .header("Authorization",
                                "Bearer " + bearerToken(otherOwnerId, RoleCode.HORSE_OWNER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/owner/horses/{id}/health-summary", 922337203685477000L)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    private long insertOpenCase(long horseId, long openedByUserId) {
        org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.medical_case
                        (horse_id, opened_by_user_id, status, opened_at, condition_summary)
                    VALUES (?, ?, 'OPEN', ?, 'SECRET_CONDITION')
                    """, new String[] {"id"});
            statement.setLong(1, horseId);
            statement.setLong(2, openedByUserId);
            statement.setObject(3, LocalDateTime.ofInstant(
                    Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("H2 did not return the inserted medical case id.");
        }
        return key.longValue();
    }

    private void insertExamination(
            long horseId,
            long caseId,
            long performedByUserId,
            String examType,
            Instant examinedAt,
            String recoveryOutcome,
            String diagnosis,
            String findings) {
        jdbcTemplate.update("""
                INSERT INTO dbo.veterinary_examination
                    (horse_id, medical_case_id, performed_by_user_id, exam_type, examined_at,
                     recovery_outcome, diagnosis, findings)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, horseId, caseId, performedByUserId, examType,
                LocalDateTime.ofInstant(examinedAt, ZoneOffset.UTC), recoveryOutcome, diagnosis, findings);
    }

    private void insertWeight(long horseId, long recordedByUserId, BigDecimal weight, Instant measuredAt) {
        jdbcTemplate.update("""
                INSERT INTO dbo.weight_record (horse_id, recorded_by_user_id, weight, measured_at, context)
                VALUES (?, ?, ?, ?, 'test')
                """, horseId, recordedByUserId, weight,
                LocalDateTime.ofInstant(measuredAt, ZoneOffset.UTC));
    }
}
