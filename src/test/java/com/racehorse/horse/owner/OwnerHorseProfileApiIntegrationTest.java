package com.racehorse.horse.owner;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:racehorse_owner_api_test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
class OwnerHorseProfileApiIntegrationTest extends OwnerApiIntegrationTestSupport {

    @Test
    void returnsPedigreeAndOnlyTheCurrentOwnersOwnershipRow() throws Exception {
        long ownerId = createUser();
        long coOwnerId = createUser();
        long sireId = createHorse("North Wind", "FIT", null, null, null, null);
        long horseId = createHorse("Silver Comet", "FIT", sireId, null, null, "Quiet Dawn");
        addOwnership(horseId, ownerId, "40.00", false);
        addOwnership(horseId, coOwnerId, "60.00", true);
        String token = bearerToken(ownerId, RoleCode.HORSE_OWNER);

        mockMvc.perform(get("/api/owner/horses/{id}/profile", horseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horse.id").value(horseId))
                .andExpect(jsonPath("$.data.countryOfOrigin").value("Testland"))
                .andExpect(jsonPath("$.data.registrationNo").isNotEmpty())
                .andExpect(jsonPath("$.data.activatedAt").value("2025-01-02T03:04:05Z"))
                .andExpect(jsonPath("$.data.pedigree.sire.horseId").value(sireId))
                .andExpect(jsonPath("$.data.pedigree.sire.name").value(nullValue()))
                .andExpect(jsonPath("$.data.pedigree.sire.horseCode").isNotEmpty())
                .andExpect(jsonPath("$.data.pedigree.dam.horseId").doesNotExist())
                .andExpect(jsonPath("$.data.pedigree.dam.name").value("Quiet Dawn"))
                .andExpect(jsonPath("$.data.myOwnership.sharePercentage").value(40.0))
                .andExpect(jsonPath("$.data.myOwnership.isManagingOwner").value(false))
                .andExpect(jsonPath("$.data.currentOwnerships").doesNotExist())
                .andExpect(jsonPath("$.data.myOwnership.ownerUserId").doesNotExist());
    }

    @Test
    void distinguishesNonOwnedFromMissingHorses() throws Exception {
        long ownerId = createUser();
        long otherOwnerId = createUser();
        long horseId = createHorse("Private Horse", "FIT", null, null, null, null);
        addOwnership(horseId, otherOwnerId, "100.00", true);
        String token = bearerToken(ownerId, RoleCode.HORSE_OWNER);

        mockMvc.perform(get("/api/owner/horses/{id}/profile", horseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/owner/horses/{id}/profile", 922337203685477000L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/owner/horses/{id}/profile", 0)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
