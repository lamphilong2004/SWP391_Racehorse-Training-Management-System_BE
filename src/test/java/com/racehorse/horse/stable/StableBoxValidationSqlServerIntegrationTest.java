package com.racehorse.horse.stable;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "core012.sqlserver.it", matches = "true")
class StableBoxValidationSqlServerIntegrationTest extends StableBoxSqlServerTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        sqlServerProperties(registry);
    }

    @Test
    void invalidStatusUsesValidationEnvelopeRatherThanGenericServerError() throws Exception {
        TestAccount manager = createAccount(RoleCode.CLUB_MANAGER);

        mockMvc.perform(get("/api/stable-boxes")
                        .header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/stable-boxes")
                        .param("status", "BUSY")
                        .header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
