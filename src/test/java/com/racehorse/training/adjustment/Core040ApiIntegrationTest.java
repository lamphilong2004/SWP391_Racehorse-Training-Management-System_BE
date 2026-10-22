package com.racehorse.training.adjustment;

import com.racehorse.TestJwtProperties;
import com.racehorse.auth.PendingRegistrationRepository;
import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class Core040ApiIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(400_000);
    private static final String PASSWORD = "Core040-Test-123";
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired JsonMapper json;
    @MockitoBean PendingRegistrationRepository pendingRegistrationRepository;
    MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestJwtProperties.register(registry);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:racehorse_core040_api;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        tables();
        for (String table : new String[]{"program_adjustment_request", "trainer_assignment", "horse_program_phase",
                "horse_training_program", "horse", "notification", "user_role", "app_user", "role"}) {
            jdbc.update("DELETE FROM dbo." + table);
        }
        for (RoleCode role : RoleCode.values()) jdbc.update("INSERT INTO dbo.role(id,role_code) VALUES (?,?)",
                role.ordinal() + 1, role.name());
        long manager = user(RoleCode.CLUB_MANAGER);
        jdbc.update("INSERT INTO dbo.horse(id,horse_code,name,lifecycle) VALUES (1,'H-1','Horse One','ACTIVE')");
        jdbc.update("INSERT INTO dbo.horse_training_program(id,horse_id,template_version_id,created_by_user_id,name,status,period_start,period_end,activated_at) " +
                "VALUES (1,1,1,?,'Program One','ACTIVE',?,?,CURRENT_TIMESTAMP)",
                manager, java.sql.Date.valueOf(LocalDate.now().minusDays(1)), java.sql.Date.valueOf(LocalDate.now().plusDays(30)));
        jdbc.update("INSERT INTO dbo.horse_program_phase(id,horse_training_program_id,name,phase_order,start_date,end_date) " +
                "VALUES (11,1,'Base',1,?,?)",
                java.sql.Date.valueOf(LocalDate.now().minusDays(1)), java.sql.Date.valueOf(LocalDate.now().plusDays(30)));
        long trainer = user(RoleCode.TRAINER);
        user(RoleCode.HEAD_TRAINER);
        jdbc.update("INSERT INTO dbo.trainer_assignment(id,horse_training_program_id,trainer_user_id,assigned_by_user_id,effective_from) VALUES (1,1,?,?,?)",
                trainer, manager, java.sql.Date.valueOf(LocalDate.now().minusDays(1)));
    }

    @Test void trainerCreatesAndSeesOwnRequestWhileHeadTrainerDecidesOnce() throws Exception {
        long trainer = userWithExistingEmail(RoleCode.TRAINER);
        String trainerToken = login(trainer);
        String body = "{\"horseProgramPhaseId\":11,\"requestType\":\"PHASE_OBJECTIVE\",\"currentValue\":\"Base work\",\"requestedValue\":\"Add hill work\",\"reason\":\"Horse improved\"}";
        String raw = mvc.perform(post("/api/training-programs/1/adjustment-requests").header("Authorization", "Bearer " + trainerToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.requestedBy.id").value(trainer)).andReturn().getResponse().getContentAsString();
        long requestId = json.readTree(raw).path("data").path("id").longValue();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE notification_type='ADJUSTMENT_REQUEST_SUBMITTED'", Integer.class));
        mvc.perform(get("/api/adjustment-requests").header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(1));

        long head = user(RoleCode.HEAD_TRAINER);
        String headToken = login(head);
        mvc.perform(post("/api/adjustment-requests/{id}/approve", requestId).header("Authorization", "Bearer " + headToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedBy.id").value(head));
        mvc.perform(post("/api/adjustment-requests/{id}/approve", requestId).header("Authorization", "Bearer " + headToken))
                .andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE notification_type='ADJUSTMENT_REQUEST_DECIDED'", Integer.class));
    }

    @Test void trainerCannotWriteUnassignedProgramOrSeeAnotherTrainersRequests() throws Exception {
        long trainer = userWithExistingEmail(RoleCode.TRAINER);
        long outsider = user(RoleCode.TRAINER);
        String token = login(trainer), outsiderToken = login(outsider);
        mvc.perform(post("/api/training-programs/999/adjustment-requests").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"requestType\":\"PRIMARY_GOAL\",\"requestedValue\":\"X\",\"reason\":\"Y\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/training-programs/1/adjustment-requests").header("Authorization", "Bearer " + outsiderToken)
                        .contentType("application/json").content("{\"requestType\":\"PRIMARY_GOAL\",\"requestedValue\":\"X\",\"reason\":\"Y\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/training-programs/1/adjustment-requests").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"requestType\":\"TARGET_RACE\",\"requestedValue\":\"Race X\",\"reason\":\"Y\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/adjustment-requests").header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @Test void nonTrainerCannotCreateAdjustmentRequest() throws Exception {
        String token = login(user(RoleCode.WORK_RIDER));
        mvc.perform(post("/api/training-programs/1/adjustment-requests").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"requestType\":\"PRIMARY_GOAL\",\"requestedValue\":\"X\",\"reason\":\"Y\"}"))
                .andExpect(status().isForbidden());
    }

    private long userWithExistingEmail(RoleCode role) {
        Long id = jdbc.queryForObject("SELECT trainer_user_id FROM dbo.trainer_assignment WHERE horse_training_program_id=1", Long.class);
        return id;
    }
    private long user(RoleCode role) {
        long id = IDS.incrementAndGet(); String email = "core040-" + id + "@test.invalid";
        jdbc.update("INSERT INTO dbo.app_user(id,user_code,full_name,email,password_hash,account_status) VALUES (?,?,?, ?,?,'ACTIVE')",
                id, "U" + id, "Core040 User", email, encoder.encode(PASSWORD));
        jdbc.update("INSERT INTO dbo.user_role(id,user_id,role_id) VALUES (?,?,?)", id, id, role.ordinal() + 1);
        return id;
    }
    private String login(long id) throws Exception {
        String email = jdbc.queryForObject("SELECT email FROM dbo.app_user WHERE id=?", String.class, id);
        String raw = mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(raw); return node.path("data").path("accessToken").stringValue();
    }
    private void tables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.role(id BIGINT PRIMARY KEY,role_code VARCHAR(30) UNIQUE,display_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.app_user(id BIGINT PRIMARY KEY,user_code VARCHAR(20),full_name NVARCHAR(150),email NVARCHAR(254),password_hash VARCHAR(255),account_status VARCHAR(20),last_login_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.user_role(id BIGINT PRIMARY KEY,user_id BIGINT,role_id BIGINT,assigned_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse(id BIGINT PRIMARY KEY,horse_code VARCHAR(20),name NVARCHAR(150),lifecycle VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse_training_program(id BIGINT PRIMARY KEY,horse_id BIGINT,name NVARCHAR(150),status VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse_program_phase(id BIGINT PRIMARY KEY,horse_training_program_id BIGINT,name NVARCHAR(150))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.trainer_assignment(id BIGINT PRIMARY KEY,horse_training_program_id BIGINT,trainer_user_id BIGINT,assigned_by_user_id BIGINT,effective_from DATE,effective_to DATE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.program_adjustment_request(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,horse_training_program_id BIGINT,horse_program_phase_id BIGINT,requested_by_user_id BIGINT,reviewed_by_user_id BIGINT,request_type VARCHAR(40),current_value NVARCHAR(1000),requested_value NVARCHAR(1000),reason NVARCHAR(2000),evidence_notes NVARCHAR(2000),status VARCHAR(20),requested_at TIMESTAMP,reviewed_at TIMESTAMP,decision_reason NVARCHAR(2000),updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.notification(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,recipient_user_id BIGINT,category VARCHAR(20),notification_type VARCHAR(50),title NVARCHAR(200),message NVARCHAR(1000),target_type VARCHAR(50),target_id BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
    }
}
