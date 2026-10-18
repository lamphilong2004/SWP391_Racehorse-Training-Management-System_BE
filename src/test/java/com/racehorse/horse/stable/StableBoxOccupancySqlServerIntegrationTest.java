package com.racehorse.horse.stable;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "core012.sqlserver.it", matches = "true")
class StableBoxOccupancySqlServerIntegrationTest extends StableBoxSqlServerTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        sqlServerProperties(registry);
    }

    @Test
    void emptyResultsOpenAssignmentsAndStatusFiltersPersistAcrossRequests() throws Exception {
        TestAccount manager = createAccount(RoleCode.CLUB_MANAGER);
        mockMvc.perform(get("/api/stable-boxes")
                        .header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long horseId = insertHorse(suffix);
        long freeBoxId = insertStableBox("F", suffix);
        long occupiedBoxId = insertStableBox("O", suffix);
        insertAssignment(horseId, freeBoxId, manager.userId(), false);
        insertAssignment(horseId, occupiedBoxId, manager.userId(), true);

        mockMvc.perform(get("/api/stable-boxes")
                        .header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("FREE"))
                .andExpect(jsonPath("$.data[0].currentHorse").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data[1].status").value("OCCUPIED"))
                .andExpect(jsonPath("$.data[1].currentHorse.horseCode").value("C12-H-" + suffix));

        mockMvc.perform(get("/api/stable-boxes")
                        .param("status", "FREE")
                        .header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("FREE"));

        mockMvc.perform(get("/api/stable-boxes")
                        .param("status", "OCCUPIED")
                        .header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("OCCUPIED"));
    }
}
