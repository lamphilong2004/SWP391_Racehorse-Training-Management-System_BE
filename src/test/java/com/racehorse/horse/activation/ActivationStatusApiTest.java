package com.racehorse.horse.activation;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivationStatusApiTest extends HorseActivationTestSupport {

    @Test
    void swaggerExposesActivationStatusOperations() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/horses/pending-activation'].get.operationId")
                        .value("listPendingActivation"))
                .andExpect(jsonPath("$.paths['/api/horses/{id}/activation-status'].get.operationId")
                        .value("getActivationStatus"))
                .andExpect(jsonPath("$.paths['/api/horses/{id}/readiness-review'].post.operationId")
                        .value("acknowledgeReadinessReview"));
    }

    @Test
    void statusAndPendingActivationExposeOnlyTheApprovedChecklist() throws Exception {
        long horseId = createHorse("Status horse");
        long managerId = createAccount(RoleCode.CLUB_MANAGER);

        mvc.perform(get("/api/horses/{id}/activation-status", horseId)
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horseId").value(horseId))
                .andExpect(jsonPath("$.data.lifecycle").value("DRAFT"))
                .andExpect(jsonPath("$.data.activatedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.checklist.length()").value(6))
                .andExpect(jsonPath("$.data.checklist[0].id").value("IDENTITY"))
                .andExpect(jsonPath("$.data.checklist[0].state").value("SATISFIED"))
                .andExpect(jsonPath("$.data.checklist[1].id").value("PEDIGREE"))
                .andExpect(jsonPath("$.data.checklist[1].state").value("SATISFIED"))
                .andExpect(jsonPath("$.data.checklist[2].state").value("PENDING"))
                .andExpect(jsonPath("$.data.checklist[5].requirement").value("RECOMMENDED"))
                .andExpect(jsonPath("$.data.checklist[5].state").value("NOT_REVIEWED"));

        mvc.perform(get("/api/horses/pending-activation")
                        .param("page", "0")
                        .param("size", "100")
                        .with(authenticatedAs(RoleCode.VETERINARIAN, createAccount(RoleCode.VETERINARIAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].horseId", hasItem((int) horseId)));

        mvc.perform(get("/api/horses/{id}/activation-status", horseId)
                        .with(authenticatedAs(RoleCode.HORSE_OWNER, createAccount(RoleCode.HORSE_OWNER))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/horses/{id}/activation-status", horseId))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/horses/{id}/activation-status", Long.MAX_VALUE)
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
