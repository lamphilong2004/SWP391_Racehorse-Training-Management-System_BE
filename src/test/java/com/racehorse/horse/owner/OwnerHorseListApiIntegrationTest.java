package com.racehorse.horse.owner;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:racehorse_owner_api_test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
class OwnerHorseListApiIntegrationTest extends OwnerApiIntegrationTestSupport {

    @Test
    void returnsAnUnpaginatedArrayScopedToOpenOwnershipRows() throws Exception {
        long ownerId = createUser();
        long otherOwnerId = createUser();
        long ownedHorseId = createHorse("Silver Comet", "FIT", null, null, null, null);
        long otherHorseId = createHorse("Hidden Star", "MONITORING", null, null, null, null);
        long previouslyOwnedHorseId = createHorse("Former Horse", "FIT", null, null, null, null);
        addOwnership(ownedHorseId, ownerId, "60.00", true);
        addOwnership(otherHorseId, otherOwnerId, "100.00", true);
        addOwnership(previouslyOwnedHorseId, ownerId, "100.00", true);
        jdbcTemplate.update("UPDATE dbo.horse_ownership SET effective_to = DATE '2026-01-01' WHERE horse_id = ?",
                previouslyOwnedHorseId);
        jdbcTemplate.update("""
                INSERT INTO dbo.medical_restriction
                    (horse_id, set_by_user_id, restriction_level, reason, effective_from, review_date)
                VALUES (?, ?, 'LIMITED', 'private test reason', CURRENT_TIMESTAMP, DATE '2026-06-30')
                """, ownedHorseId, ownerId);

        String ownerToken = bearerToken(ownerId, RoleCode.HORSE_OWNER);
        mockMvc.perform(get("/api/owner/horses").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(ownedHorseId))
                .andExpect(jsonPath("$.data[0].name").value("Silver Comet"))
                .andExpect(jsonPath("$.data[0].trainingAvailability").value("LIMITED"))
                .andExpect(jsonPath("$.data.items").doesNotExist());

        long emptyOwnerId = createUser();
        mockMvc.perform(get("/api/owner/horses")
                        .header("Authorization",
                                "Bearer " + bearerToken(emptyOwnerId, RoleCode.HORSE_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        long inactiveOwnerId = createUser();
        String inactiveOwnerToken = bearerToken(inactiveOwnerId, RoleCode.HORSE_OWNER);
        jdbcTemplate.update("UPDATE dbo.app_user SET account_status = 'INACTIVE' WHERE id = ?", inactiveOwnerId);
        mockMvc.perform(get("/api/owner/horses")
                        .header("Authorization", "Bearer " + inactiveOwnerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthenticationAndOwnerRole() throws Exception {
        mockMvc.perform(get("/api/owner/horses"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/owner/horses")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        long managerId = createUser();
        mockMvc.perform(get("/api/owner/horses")
                        .header("Authorization", "Bearer " + bearerToken(managerId, RoleCode.CLUB_MANAGER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingInvalidAndNonOwnerJwtAcrossEveryImplementedEndpoint() throws Exception {
        long horseId = createHorse("Authorization Test Horse", "FIT", null, null, null, null);
        List<String> endpoints = List.of(
                "/api/owner/horses",
                "/api/owner/horses/{id}/profile",
                "/api/owner/horses/{id}/health-summary");

        for (String endpoint : endpoints) {
            mockMvc.perform(ownerRequest(endpoint, horseId))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(ownerRequest(endpoint, horseId)
                            .header("Authorization", "Bearer invalid.token.value"))
                    .andExpect(status().isUnauthorized());
        }

        for (RoleCode role : RoleCode.values()) {
            if (role == RoleCode.HORSE_OWNER) {
                continue;
            }
            String token = bearerToken(createUser(), role);
            for (String endpoint : endpoints) {
                mockMvc.perform(ownerRequest(endpoint, horseId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void generatedOpenApiDescribesTheImplementedOwnerListRoute() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/owner/horses'].get.operationId").value("listMyHorses"))
                .andExpect(jsonPath("$.paths['/api/owner/horses'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/owner/horses/{id}/profile'].get.operationId")
                        .value("getMyHorseProfile"))
                .andExpect(jsonPath("$.paths['/api/owner/horses/{id}/profile'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/owner/horses/{id}/profile'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/owner/horses/{id}/profile'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/owner/horses/{id}/health-summary'].get.operationId")
                        .value("getMyHorseHealthSummary"))
                .andExpect(jsonPath("$.paths['/api/owner/horses/{id}/health-summary'].get.security[0].bearerAuth")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.OwnerHorseCard.properties.trainingAvailability").exists())
                .andExpect(jsonPath("$.components.schemas.OwnerHorseProfile.properties.myOwnership").exists())
                .andExpect(jsonPath("$.components.schemas.OwnerHealthSummary.properties.weights").exists())
                .andExpect(jsonPath("$.paths['/api/owner/horses/{id}/training-summary']").doesNotExist());
    }

    private MockHttpServletRequestBuilder ownerRequest(String endpoint, long horseId) {
        return endpoint.contains("{id}") ? get(endpoint, horseId) : get(endpoint);
    }
}
