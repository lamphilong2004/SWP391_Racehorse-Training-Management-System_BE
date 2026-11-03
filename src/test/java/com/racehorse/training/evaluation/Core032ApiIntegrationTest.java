package com.racehorse.training.evaluation;

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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class Core032ApiIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(320_000);
    private static final String PASSWORD = "Core032-Test-123";
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired JsonMapper json;
    @MockitoBean PendingRegistrationRepository pendingRegistrationRepository;
    MockMvc mvc;
    long trainerId, riderId, outsiderTrainerId, sessionId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestJwtProperties.register(registry);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:racehorse_core032_api;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        tables();
        for (String table : new String[]{"session_result", "rider_feedback", "monitoring_alert", "training_metric_sample",
                "incident", "pre_session_check", "session_exercise", "exercise_definition", "training_session",
                "trainer_assignment", "horse_program_phase", "horse_training_program", "medical_restriction", "horse",
                "user_role", "app_user", "role"}) jdbc.update("DELETE FROM dbo." + table);
        for (RoleCode role : RoleCode.values()) jdbc.update("INSERT INTO dbo.role(id,role_code) VALUES (?,?)", role.ordinal()+1, role.name());
        long manager=user(RoleCode.CLUB_MANAGER); long head=user(RoleCode.HEAD_TRAINER);
        trainerId=user(RoleCode.TRAINER); outsiderTrainerId=user(RoleCode.TRAINER); riderId=user(RoleCode.WORK_RIDER); user(RoleCode.VETERINARIAN);
        jdbc.update("INSERT INTO dbo.horse(id,horse_code,name,lifecycle,health_status) VALUES (1,'H-1','Horse One','ACTIVE','FIT')");
        jdbc.update("INSERT INTO dbo.horse_training_program(id,horse_id,template_version_id,created_by_user_id,name,status,period_start,period_end,activated_at) " +
                "VALUES (1,1,1,?,'Program One','ACTIVE',?,?,CURRENT_TIMESTAMP)",
                head, java.sql.Date.valueOf(LocalDate.now().minusDays(1)), java.sql.Date.valueOf(LocalDate.now().plusDays(30)));
        jdbc.update("INSERT INTO dbo.horse_program_phase(id,horse_training_program_id,name,phase_order,start_date,end_date) " +
                "VALUES (1,1,'Base',1,?,?)",
                java.sql.Date.valueOf(LocalDate.now().minusDays(1)), java.sql.Date.valueOf(LocalDate.now().plusDays(30)));
        jdbc.update("INSERT INTO dbo.trainer_assignment(id,horse_training_program_id,trainer_user_id,assigned_by_user_id,effective_from) VALUES (1,1,?,?,?)",
                trainerId,head,java.sql.Date.valueOf(LocalDate.now().minusDays(1)));
        Instant now=Instant.now();
        jdbc.update("INSERT INTO dbo.training_session(id,session_code,horse_program_phase_id,created_by_user_id,work_rider_user_id,scheduled_start,scheduled_end,surface,focus,intensity,status,actual_start,actual_end) VALUES (1,'S-032',1,?,?,?,?,?,?,?,'COMPLETED',?,?)",
                head,riderId,java.sql.Timestamp.from(now.minusSeconds(7200)),java.sql.Timestamp.from(now.minusSeconds(3600)),"TRACK","Endurance","MEDIUM",java.sql.Timestamp.from(now.minusSeconds(7000)),java.sql.Timestamp.from(now.minusSeconds(1800)));
        sessionId=1;
        jdbc.update("INSERT INTO dbo.exercise_definition(id,name,exercise_type) VALUES (1,'Walk','Walk')");
        jdbc.update("INSERT INTO dbo.session_exercise(id,training_session_id,exercise_definition_id,exercise_order,distance,duration,intensity) VALUES (1,1,1,1,500,10,'LOW')");
        jdbc.update("INSERT INTO dbo.pre_session_check(id,training_session_id,performed_by_user_id,performed_at,observations,overall_result) VALUES (1,1,?,?,'{\"GENERAL_CONDITION\":{\"result\":\"NORMAL\"}}','ALL_NORMAL')",
                user(RoleCode.GROOM),java.sql.Timestamp.from(now.minusSeconds(7500)));
        jdbc.update("INSERT INTO dbo.training_metric_sample(id,training_session_id,recorded_at,heart_rate,speed,distance,source) VALUES (1,1,?,100,25,500,'MANUAL'),(2,1,?,120,30,700,'SIMULATOR')",
                java.sql.Timestamp.from(now.minusSeconds(4000)),java.sql.Timestamp.from(now.minusSeconds(3000)));
        jdbc.update("INSERT INTO dbo.monitoring_alert(id,training_session_id,metric,metric_value,threshold_applied,severity,raised_at) VALUES (1,1,'HEART_RATE',190,180,'CRITICAL',?)",java.sql.Timestamp.from(now.minusSeconds(3500)));
        jdbc.update("INSERT INTO dbo.rider_feedback(id,training_session_id,submitted_by_user_id,submitted_at,responses) VALUES (1,1,?,?, '{\"notes\":\"Good ride\"}')",riderId,java.sql.Timestamp.from(now.minusSeconds(2000)));
    }

    @Test void assignedTrainerEvaluatesOnceAndAuthorizedRolesReadResultAndPerformance() throws Exception {
        String trainerToken=login(trainerId), riderToken=login(riderId), outsiderToken=login(outsiderTrainerId), vetToken=login(userId(RoleCode.VETERINARIAN));
        mvc.perform(post("/api/training-sessions/{id}/evaluation",sessionId).header("Authorization","Bearer "+trainerToken)
                        .contentType("application/json").content("{\"result\":\"MET\",\"evaluatedSummary\":\"Target met\",\"recommendation\":\"Continue\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.result").value("MET"))
                .andExpect(jsonPath("$.data.evaluatedBy.id").value(trainerId));
        mvc.perform(post("/api/training-sessions/{id}/evaluation",sessionId).header("Authorization","Bearer "+trainerToken)
                        .contentType("application/json").content("{\"result\":\"NOT_MET\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/training-sessions/{id}/result",sessionId).header("Authorization","Bearer "+riderToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.session.sessionCode").value("S-032"))
                .andExpect(jsonPath("$.data.actual.sampleCount").value(2)).andExpect(jsonPath("$.data.actual.simulated").value(true))
                .andExpect(jsonPath("$.data.evaluation.result").value("MET")).andExpect(jsonPath("$.data.riderFeedback.responses.notes").value("Good ride"));
        mvc.perform(get("/api/training-sessions/{id}/result",sessionId).header("Authorization","Bearer "+outsiderToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/training-sessions/{id}/result",sessionId).header("Authorization","Bearer "+vetToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/horses/1/performance").header("Authorization","Bearer "+trainerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].sessionId").value(sessionId))
                .andExpect(jsonPath("$.data[0].result").value("MET"));
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM dbo.training_session WHERE id=1",String.class));
    }

    @Test void evaluationRequiresTerminalSessionAndRejectsInvalidRequest() throws Exception {
        String trainerToken=login(trainerId);
        jdbc.update("INSERT INTO dbo.training_session(id,session_code,horse_program_phase_id,created_by_user_id,work_rider_user_id,scheduled_start,status) VALUES (2,'S-032-2',1,?,?,CURRENT_TIMESTAMP,'SCHEDULED')",trainerId,riderId);
        mvc.perform(post("/api/training-sessions/2/evaluation").header("Authorization","Bearer "+trainerToken).contentType("application/json").content("{\"result\":\"MET\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/training-sessions/1/evaluation").header("Authorization","Bearer "+trainerToken).contentType("application/json").content("{\"result\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
    }

    private long userId(RoleCode role){return jdbc.queryForObject("SELECT u.id FROM dbo.app_user u JOIN dbo.user_role ur ON ur.user_id=u.id JOIN dbo.role r ON r.id=ur.role_id WHERE r.role_code=? ORDER BY u.id DESC",Long.class,role.name());}
    private long user(RoleCode role){long id=IDS.incrementAndGet();String email="core032-"+id+"@test.invalid";jdbc.update("INSERT INTO dbo.app_user(id,user_code,full_name,email,password_hash,account_status) VALUES (?,?,?, ?,?,'ACTIVE')",id,"U"+id,"Core032 User",email,encoder.encode(PASSWORD));jdbc.update("INSERT INTO dbo.user_role(id,user_id,role_id) VALUES (?,?,?)",id,id,role.ordinal()+1);return id;}
    private String login(long id)throws Exception{String email=jdbc.queryForObject("SELECT email FROM dbo.app_user WHERE id=?",String.class,id);String raw=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"password",PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();JsonNode n=json.readTree(raw);return n.path("data").path("accessToken").stringValue();}
    private void tables(){
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse(id BIGINT PRIMARY KEY,horse_code VARCHAR(20),name NVARCHAR(150),lifecycle VARCHAR(20),health_status VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse_training_program(id BIGINT PRIMARY KEY,horse_id BIGINT,name NVARCHAR(150),status VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse_program_phase(id BIGINT PRIMARY KEY,horse_training_program_id BIGINT,name NVARCHAR(150))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.trainer_assignment(id BIGINT PRIMARY KEY,horse_training_program_id BIGINT,trainer_user_id BIGINT,assigned_by_user_id BIGINT,effective_from DATE,effective_to DATE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.training_session(id BIGINT PRIMARY KEY,session_code VARCHAR(20),horse_program_phase_id BIGINT,created_by_user_id BIGINT,work_rider_user_id BIGINT,scheduled_start TIMESTAMP,scheduled_end TIMESTAMP,surface NVARCHAR(50),focus NVARCHAR(200),intensity VARCHAR(30),status VARCHAR(20),actual_start TIMESTAMP,actual_end TIMESTAMP,interruption_reason NVARCHAR(1000))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.exercise_definition(id BIGINT PRIMARY KEY,name NVARCHAR(150),exercise_type NVARCHAR(50))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.session_exercise(id BIGINT PRIMARY KEY,training_session_id BIGINT,exercise_definition_id BIGINT,exercise_order SMALLINT,distance DECIMAL(9,2),duration DECIMAL(7,2),repetitions SMALLINT,rest_interval DECIMAL(7,2),intensity NVARCHAR(30),surface NVARCHAR(50),suitability_category CHAR(1),acknowledgement_note NVARCHAR(1000))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.pre_session_check(id BIGINT PRIMARY KEY,training_session_id BIGINT,performed_by_user_id BIGINT,performed_at TIMESTAMP,observations NVARCHAR(4000),overall_result VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.incident(id BIGINT PRIMARY KEY,training_session_id BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.medical_restriction(id BIGINT PRIMARY KEY,horse_id BIGINT,restriction_level VARCHAR(20),reason NVARCHAR(1000),effective_from TIMESTAMP,lifted_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.training_metric_sample(id BIGINT PRIMARY KEY,training_session_id BIGINT,recorded_at TIMESTAMP,heart_rate SMALLINT,speed DECIMAL(6,2),distance DECIMAL(9,2),source VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.monitoring_alert(id BIGINT PRIMARY KEY,training_session_id BIGINT,acknowledged_by_user_id BIGINT,metric VARCHAR(20),metric_value DECIMAL(7,2),threshold_applied DECIMAL(7,2),severity VARCHAR(20),raised_at TIMESTAMP,acknowledged_at TIMESTAMP,action_taken VARCHAR(30),escalated_to_vet_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.rider_feedback(id BIGINT PRIMARY KEY,training_session_id BIGINT,submitted_by_user_id BIGINT,submitted_at TIMESTAMP,responses NVARCHAR(4000))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.session_result(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,training_session_id BIGINT UNIQUE,evaluated_by_user_id BIGINT,evaluated_summary NVARCHAR(2000),evaluation_label VARCHAR(50),evaluation_notes NVARCHAR(2000),recommendation NVARCHAR(2000),evaluated_at TIMESTAMP)");
    }
}
