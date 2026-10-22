package com.racehorse.horse.stable;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "core012.sqlserver.it", matches = "true")
class StableBoxAuthorizationSqlServerIntegrationTest extends StableBoxSqlServerTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        sqlServerProperties(registry);
    }

    @Test
    void approvedRolesCanReadAndOtherRoleIsForbidden() throws Exception {
        TestAccount manager = createAccount(RoleCode.CLUB_MANAGER);
        TestAccount headTrainer = createAccount(RoleCode.HEAD_TRAINER);

        mockMvc.perform(get("/api/stable-boxes")
                        .header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/stable-boxes")
                        .header("Authorization", "Bearer " + headTrainer.token()))
                .andExpect(status().isOk());

        for (RoleCode role : List.of(
                RoleCode.TRAINER,
                RoleCode.WORK_RIDER,
                RoleCode.VETERINARIAN,
                RoleCode.GROOM,
                RoleCode.HORSE_OWNER)) {
            TestAccount account = createAccount(role);
            mockMvc.perform(get("/api/stable-boxes")
                            .header("Authorization", "Bearer " + account.token()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @Test
    void missingAndInvalidTokensRemainUnauthorized() throws Exception {
        mockMvc.perform(get("/api/stable-boxes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/stable-boxes")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
