package com.racehorse.horse.activation;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadinessReviewApiTest extends HorseActivationTestSupport {

    @Test
    void readinessReviewIsAuditedRepeatedlyAndNeverGatesActivation() throws Exception {
        long horseId = createHorse("Readiness candidate");
        long headTrainerId = createAccount(RoleCode.HEAD_TRAINER);

        mvc.perform(post("/api/horses/{id}/readiness-review", horseId)
                        .with(authenticatedAs(RoleCode.HEAD_TRAINER, headTrainerId)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/horses/{id}/readiness-review", horseId)
                        .with(authenticatedAs(RoleCode.HEAD_TRAINER, headTrainerId)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/horses/{id}/activation-status", horseId)
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, createAccount(RoleCode.CLUB_MANAGER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycle").value("DRAFT"))
                .andExpect(jsonPath("$.data.activatedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.checklist[2].state").value("PENDING"))
                .andExpect(jsonPath("$.data.checklist[5].state").value("REVIEWED"));

        org.junit.jupiter.api.Assertions.assertEquals(2L,
                count("SELECT COUNT(*) FROM dbo.audit_log WHERE target_id = ? "
                        + "AND target_type = 'HORSE' "
                        + "AND action = 'HEAD_TRAINER_READINESS_REVIEWED'", horseId));

        mvc.perform(post("/api/horses/{id}/readiness-review", horseId)
                        .with(authenticatedAs(RoleCode.TRAINER, createAccount(RoleCode.TRAINER))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/horses/{id}/readiness-review", horseId))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/horses/{id}/readiness-review", Long.MAX_VALUE)
                        .with(authenticatedAs(RoleCode.HEAD_TRAINER, headTrainerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
