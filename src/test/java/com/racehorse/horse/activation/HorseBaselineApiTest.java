package com.racehorse.horse.activation;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HorseBaselineApiTest extends HorseActivationTestSupport {

    @MockitoSpyBean
    private HorseActivationService activationServiceSpy;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    void baselineWritePersistsClinicalBaselineAndAutomaticallyActivatesReadyHorse() throws Exception {
        long horseId = readyHorse();
        long veterinarianId = createAccount(RoleCode.VETERINARIAN);

        MvcResult result = mvc.perform(post("/api/horses/{horseId}/baseline-examinations", horseId)
                        .with(authenticatedAs(RoleCode.VETERINARIAN, veterinarianId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "weightKg", new BigDecimal("501.25"),
                                "healthStatus", "FIT",
                                "clinicalNotes", "Baseline examination notes"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.horseId").value(horseId))
                .andExpect(jsonPath("$.data.weightKg").value(501.25))
                .andExpect(jsonPath("$.data.healthStatus").value("FIT"))
                .andReturn();

        Long activated = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.horse WHERE id = ? AND lifecycle = 'ACTIVE' AND activated_at IS NOT NULL",
                Long.class, horseId);
        assertEquals(1L, activated);
        assertEquals(1L, count("SELECT COUNT(*) FROM dbo.veterinary_examination WHERE horse_id = ?", horseId));
        assertEquals(1L, count("SELECT COUNT(*) FROM dbo.weight_record WHERE horse_id = ? AND context = 'BASELINE'",
                horseId));
        assertEquals("FIT", jdbc.queryForObject(
                "SELECT health_status FROM dbo.horse WHERE id = ?", String.class, horseId));
        assertNotNull(result.getResponse().getContentAsString());

        mvc.perform(get("/api/horses/{id}", horseId)
                        .with(authenticatedAs(RoleCode.VETERINARIAN, veterinarianId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestWeight.weightKg").value(501.25))
                .andExpect(jsonPath("$.data.latestWeight.recordedAt").isNotEmpty());

        mvc.perform(post("/api/horses/{horseId}/baseline-examinations", horseId)
                        .with(authenticatedAs(RoleCode.VETERINARIAN, veterinarianId))
                        .contentType("application/json")
                        .content("""
                                {"weightKg": 501.25, "healthStatus": "FIT"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void activationFailureRollsBackTheBaselineExaminationWeightAndHealthStatus() throws Exception {
        long horseId = readyHorse();
        long veterinarianId = createAccount(RoleCode.VETERINARIAN);
        doThrow(new IllegalStateException("activation reevaluation failed"))
                .when(activationServiceSpy).reevaluate(horseId);

        mvc.perform(post("/api/horses/{horseId}/baseline-examinations", horseId)
                        .with(authenticatedAs(RoleCode.VETERINARIAN, veterinarianId))
                        .contentType("application/json")
                        .content("""
                                {"weightKg": 501.25, "healthStatus": "FIT"}
                                """))
                .andExpect(status().isInternalServerError());

        assertEquals(0L, count("SELECT COUNT(*) FROM dbo.veterinary_examination WHERE horse_id = ?", horseId));
        assertEquals(0L, count("SELECT COUNT(*) FROM dbo.weight_record WHERE horse_id = ?", horseId));
        assertNull(jdbc.queryForObject(
                "SELECT health_status FROM dbo.horse WHERE id = ?", String.class, horseId));
    }

    @Test
    void veterinarianCanRecordAndReadWeightsButCannotUseFutureMeasurementTime() throws Exception {
        long horseId = createHorse("Weight Test Horse");
        long veterinarianId = createAccount(RoleCode.VETERINARIAN);
        Instant recordedAt = Instant.parse("2026-10-01T09:30:00Z");

        mvc.perform(get("/api/horses/{id}", horseId)
                        .with(authenticatedAs(RoleCode.VETERINARIAN, veterinarianId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestWeight").isEmpty());

        mvc.perform(post("/api/horses/{horseId}/weights", horseId)
                        .with(authenticatedAs(RoleCode.VETERINARIAN, veterinarianId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "weightKg", new BigDecimal("499.50"),
                                "recordedAt", recordedAt.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.weightKg").value(499.5))
                .andExpect(jsonPath("$.data.recordedAt").value(recordedAt.toString()));

        mvc.perform(get("/api/horses/{horseId}/weights", horseId)
                        .with(authenticatedAs(RoleCode.VETERINARIAN, veterinarianId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].weightKg").value(499.5));

        mvc.perform(post("/api/horses/{horseId}/weights", horseId)
                        .with(authenticatedAs(RoleCode.VETERINARIAN, veterinarianId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "weightKg", new BigDecimal("500.00"),
                                "recordedAt", Instant.now().plusSeconds(3600).toString()))))
                .andExpect(status().isBadRequest());

        assertEquals(1L, count("SELECT COUNT(*) FROM dbo.weight_record WHERE horse_id = ?", horseId));
        assertNull(jdbc.queryForObject(
                "SELECT health_status FROM dbo.horse WHERE id = ?", String.class, horseId));
    }

    @Test
    void pedigreeWriteTriggersActivationWhenItCompletesTheLastMandatoryCondition() throws Exception {
        long horseId = readyHorseWithoutPedigree();
        long managerId = createAccount(RoleCode.CLUB_MANAGER);

        mvc.perform(put("/api/horses/{id}/pedigree", horseId)
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId))
                        .contentType("application/json")
                        .content("""
                                {"sire":{"name":"Test sire"},"dam":{"name":"Test dam"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycle").value("ACTIVE"));

        assertEquals(1L, activatedHorseCount(horseId));
    }

    @Test
    void ownershipWriteTriggersActivationWhenItCompletesTheLastMandatoryCondition() throws Exception {
        long horseId = readyHorseWithoutOwnership();
        long ownerId = createAccount(RoleCode.HORSE_OWNER);
        long managerId = createAccount(RoleCode.CLUB_MANAGER);

        mvc.perform(post("/api/horses/{id}/ownerships", horseId)
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ownerUserId", ownerId,
                                "sharePercentage", new BigDecimal("100.00"),
                                "effectiveFrom", "2026-10-01",
                                "isManagingOwner", true))))
                .andExpect(status().isCreated());

        assertEquals(1L, activatedHorseCount(horseId));
    }

    @Test
    void stableAssignmentWriteTriggersActivationWhenItCompletesTheLastMandatoryCondition() throws Exception {
        long horseId = readyHorseWithoutStableAssignment();
        long managerId = createAccount(RoleCode.CLUB_MANAGER);
        long stableBoxId = createStableBox();

        mvc.perform(post("/api/horses/{id}/stable-assignments", horseId)
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "stableBoxId", stableBoxId,
                                "effectiveFrom", "2026-10-01"))))
                .andExpect(status().isCreated());

        assertEquals(1L, activatedHorseCount(horseId));
    }

    @Test
    void pedigreeWriteRollsBackWhenActivationReevaluationFails() throws Exception {
        long horseId = readyHorseWithoutPedigree();
        long managerId = createAccount(RoleCode.CLUB_MANAGER);
        doThrow(new IllegalStateException("activation reevaluation failed"))
                .when(activationServiceSpy).reevaluate(horseId);

        mvc.perform(put("/api/horses/{id}/pedigree", horseId)
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId))
                        .contentType("application/json")
                        .content("""
                                {"sire":{"name":"Test sire"},"dam":{"name":"Test dam"}}
                                """))
                .andExpect(status().isInternalServerError());

        assertNull(jdbc.queryForObject("SELECT sire_name FROM dbo.horse WHERE id = ?", String.class, horseId));
        assertNull(jdbc.queryForObject("SELECT dam_name FROM dbo.horse WHERE id = ?", String.class, horseId));
        assertEquals(0L, activatedHorseCount(horseId));
    }

    private long readyHorse() {
        long horseId = createHorse("Ready Baseline Horse");
        long ownerId = createAccount(RoleCode.HORSE_OWNER);
        long managerId = createAccount(RoleCode.CLUB_MANAGER);
        setValidNamedPedigree(horseId);
        addOwnership(horseId, ownerId);
        addStableAssignment(horseId, managerId);
        return horseId;
    }

    private long readyHorseWithoutPedigree() {
        long horseId = createHorse("Ready Without Pedigree");
        long ownerId = createAccount(RoleCode.HORSE_OWNER);
        long managerId = createAccount(RoleCode.CLUB_MANAGER);
        long veterinarianId = createAccount(RoleCode.VETERINARIAN);
        addOwnership(horseId, ownerId);
        addStableAssignment(horseId, managerId);
        addBaseline(horseId, veterinarianId);
        return horseId;
    }

    private long readyHorseWithoutOwnership() {
        long horseId = createHorse("Ready Without Owner");
        long managerId = createAccount(RoleCode.CLUB_MANAGER);
        long veterinarianId = createAccount(RoleCode.VETERINARIAN);
        setValidNamedPedigree(horseId);
        addStableAssignment(horseId, managerId);
        addBaseline(horseId, veterinarianId);
        return horseId;
    }

    private long readyHorseWithoutStableAssignment() {
        long horseId = createHorse("Ready Without Stable");
        long ownerId = createAccount(RoleCode.HORSE_OWNER);
        long veterinarianId = createAccount(RoleCode.VETERINARIAN);
        setValidNamedPedigree(horseId);
        addOwnership(horseId, ownerId);
        addBaseline(horseId, veterinarianId);
        return horseId;
    }

    private long activatedHorseCount(long horseId) {
        return count("SELECT COUNT(*) FROM dbo.horse WHERE id = ? AND lifecycle = 'ACTIVE' AND activated_at IS NOT NULL",
                horseId);
    }
}
