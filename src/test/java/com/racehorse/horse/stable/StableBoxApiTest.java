package com.racehorse.horse.stable;

import com.racehorse.TestJwtProperties;
import com.racehorse.auth.AccountStatus;
import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class StableBoxApiTest {

    private static final String PASSWORD = "Test-Password-123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JsonMapper objectMapper;

    private final AtomicLong nextUserId = new AtomicLong(500);
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwtProperties.register(registry);
    }

    @BeforeEach
    void prepareDatabase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS dbo.horse (
                    id BIGINT PRIMARY KEY,
                    horse_code VARCHAR(20) NOT NULL UNIQUE,
                    name NVARCHAR(150) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS dbo.stable_box (
                    id BIGINT PRIMARY KEY,
                    box_code VARCHAR(20) NOT NULL UNIQUE,
                    yard NVARCHAR(100)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS dbo.horse_stable_assignment (
                    id BIGINT PRIMARY KEY,
                    horse_id BIGINT NOT NULL,
                    stable_box_id BIGINT NOT NULL,
                    assigned_by_user_id BIGINT NOT NULL,
                    effective_from DATE NOT NULL,
                    effective_to DATE,
                    reason NVARCHAR(500)
                )
                """);

        jdbcTemplate.update("DELETE FROM dbo.horse_stable_assignment");
        jdbcTemplate.update("DELETE FROM dbo.horse");
        jdbcTemplate.update("DELETE FROM dbo.stable_box");
        jdbcTemplate.update("DELETE FROM dbo.user_role");
        jdbcTemplate.update("DELETE FROM dbo.app_user");
        jdbcTemplate.update("DELETE FROM dbo.role");
        nextUserId.set(500);
    }

    @Test
    void headTrainerReadsOccupancyFromOpenAssignmentsAndCanFilter() throws Exception {
        seedStableBoxes();
        String token = tokenFor(RoleCode.HEAD_TRAINER);

        mockMvc.perform(get("/api/stable-boxes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].boxCode").value("A-01"))
                .andExpect(jsonPath("$.data[0].status").value("OCCUPIED"))
                .andExpect(jsonPath("$.data[0].currentHorse.id").value(101))
                .andExpect(jsonPath("$.data[0].currentHorse.horseCode").value("RH-101"))
                .andExpect(jsonPath("$.data[1].status").value("FREE"))
                .andExpect(jsonPath("$.data[1].currentHorse").value(nullValue()))
                .andExpect(jsonPath("$.data[2].status").value("FREE"));

        mockMvc.perform(get("/api/stable-boxes")
                        .param("status", "FREE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("FREE"))
                .andExpect(jsonPath("$.data[1].status").value("FREE"));

        mockMvc.perform(get("/api/stable-boxes")
                        .param("status", "OCCUPIED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].boxCode").value("A-01"));
    }

    @Test
    void otherAuthenticatedRoleIsForbiddenAndMissingTokenIsUnauthorized() throws Exception {
        for (RoleCode role : RoleCode.values()) {
            if (role == RoleCode.CLUB_MANAGER || role == RoleCode.HEAD_TRAINER) {
                continue;
            }
            String token = tokenFor(role);
            mockMvc.perform(get("/api/stable-boxes")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        mockMvc.perform(get("/api/stable-boxes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/stable-boxes")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidStatusUsesSharedValidationEnvelope() throws Exception {
        String token = tokenFor(RoleCode.CLUB_MANAGER);

        mockMvc.perform(get("/api/stable-boxes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/stable-boxes")
                        .param("status", "BUSY")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void runtimeOpenApiDescribesPathSecurityAndFilter() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/stable-boxes'].get.operationId").value("listStableBoxes"))
                .andExpect(jsonPath("$.paths['/api/stable-boxes'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/stable-boxes'].get.parameters[0].name").value("status"))
                .andExpect(jsonPath("$.paths['/api/stable-boxes'].get.description")
                        .value(containsString("CLUB_MANAGER and HEAD_TRAINER")))
                .andExpect(jsonPath("$.paths['/api/stable-boxes'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/stable-boxes'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/stable-boxes'].get.responses['403']").exists())
                .andExpect(jsonPath("$.components.schemas.StableBox.properties.boxCode").exists())
                .andExpect(jsonPath("$.components.schemas.StableBox.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.StableBox.properties.currentHorse").exists())
                .andExpect(jsonPath("$.paths['/api/stable-boxes'].get.parameters[0].schema.enum[0]").value("FREE"))
                .andExpect(jsonPath("$.paths['/api/stable-boxes'].get.parameters[0].schema.enum[1]").value("OCCUPIED"));
    }

    private void seedStableBoxes() {
        jdbcTemplate.update("INSERT INTO dbo.stable_box (id, box_code, yard) VALUES (?, ?, ?)",
                1, "A-01", "North");
        jdbcTemplate.update("INSERT INTO dbo.stable_box (id, box_code, yard) VALUES (?, ?, ?)",
                2, "A-02", "North");
        jdbcTemplate.update("INSERT INTO dbo.stable_box (id, box_code, yard) VALUES (?, ?, ?)",
                3, "B-01", "South");
        jdbcTemplate.update("INSERT INTO dbo.horse (id, horse_code, name, lifecycle) VALUES (?, ?, ?, ?)",
                101, "RH-101", "Silver Comet", "DRAFT");
        jdbcTemplate.update("""
                INSERT INTO dbo.app_user
                    (id, user_code, full_name, email, account_status)
                VALUES (?, ?, ?, ?, ?)
                """, 501, "SB-ACTOR", "Stable Box Actor", "stable-box-actor@example.test", "ACTIVE");
        jdbcTemplate.update("""
                INSERT INTO dbo.horse_stable_assignment
                    (id, horse_id, stable_box_id, assigned_by_user_id, effective_from, effective_to)
                VALUES (?, ?, ?, ?, ?, ?)
                """, 1, 101, 1, 501, LocalDate.parse("2026-10-01"), null);
        jdbcTemplate.update("""
                INSERT INTO dbo.horse_stable_assignment
                    (id, horse_id, stable_box_id, assigned_by_user_id, effective_from, effective_to)
                VALUES (?, ?, ?, ?, ?, ?)
                """, 2, 101, 2, 501, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"));
    }

    private String tokenFor(RoleCode role) throws Exception {
        long userId = nextUserId.getAndIncrement();
        int roleId = role.ordinal() + 1;
        String email = "stable-box-user-" + userId + "@example.test";
        jdbcTemplate.update("""
                INSERT INTO dbo.app_user (id, user_code, full_name, email, password_hash, account_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, "SB-" + userId, "Stable Box User", email,
                passwordEncoder.encode(PASSWORD), AccountStatus.ACTIVE.name());
        jdbcTemplate.update("INSERT INTO dbo.role (id, role_code) VALUES (?, ?)", roleId, role.name());
        jdbcTemplate.update("INSERT INTO dbo.user_role (id, user_id, role_id) VALUES (?, ?, ?)",
                userId + 10_000, userId, roleId);

        String loginBody = objectMapper.writeValueAsString(
                Map.of("email", email, "password", PASSWORD));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode login = objectMapper.readTree(response);
        return login.path("data").path("accessToken").stringValue();
    }
}
