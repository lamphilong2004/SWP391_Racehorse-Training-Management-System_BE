package com.racehorse.training.session;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class Core028ApiIntegrationTest {
    private static final AtomicLong IDS=new AtomicLong(280_000);
    private static final String PASSWORD="Core028-Test-123";
    @Autowired WebApplicationContext context;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;@Autowired JsonMapper json;
    @MockitoBean PendingRegistrationRepository pendingRegistrationRepository;MockMvc mvc;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){TestJwtProperties.register(r);r.add("spring.datasource.url",()->"jdbc:h2:mem:racehorse_core028_api;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");}

    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();tables();
        for(String table:new String[]{"incident","rider_feedback","notification","training_session","trainer_assignment","horse_program_phase","horse_training_program","horse","user_role","app_user","role"})jdbc.update("DELETE FROM dbo."+table);
        for(RoleCode role:RoleCode.values())jdbc.update("INSERT INTO dbo.role(id,role_code) VALUES (?,?)",role.ordinal()+1,role.name());
        jdbc.update("INSERT INTO dbo.horse(id,horse_code,name,lifecycle) VALUES (1,'H-1','Horse One','ACTIVE')");
        jdbc.update("""
                INSERT INTO dbo.horse_training_program
                    (id,horse_id,template_version_id,created_by_user_id,name,status,period_start,period_end,activated_at)
                VALUES (1,1,1,280001,'CORE-028 test program','ACTIVE',CURRENT_DATE,CURRENT_DATE,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO dbo.horse_program_phase
                    (id,horse_training_program_id,name,phase_order,start_date,end_date)
                VALUES (1,1,'Base',1,CURRENT_DATE,CURRENT_DATE)
                """);
    }

    @Test void riderCompletesReportsIssueAndSubmitsFeedbackWithinOwnScope() throws Exception {
        long trainer=addUser(RoleCode.TRAINER),rider=addUser(RoleCode.WORK_RIDER),other=addUser(RoleCode.WORK_RIDER),vet=addUser(RoleCode.VETERINARIAN);
        assign(trainer);long completeSession=session("IN_PROGRESS",rider,"S-COMP"),session=session("IN_PROGRESS",rider,"S-ISSUE"),otherSession=session("SCHEDULED",other,"S-2");
        String riderToken=login(rider),otherToken=login(other),trainerToken=login(trainer);
        mvc.perform(get("/api/work-rider/assignments").header("Authorization","Bearer "+riderToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(post("/api/training-sessions/{id}/complete",completeSession).header("Authorization","Bearer "+riderToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mvc.perform(post("/api/training-sessions/{id}/complete",completeSession).header("Authorization","Bearer "+riderToken)).andExpect(status().isConflict());
        jdbc.update("UPDATE dbo.training_session SET status='IN_PROGRESS',actual_start=CURRENT_TIMESTAMP WHERE id=?",otherSession);
        mvc.perform(post("/api/training-sessions/{id}/report-issue",otherSession).header("Authorization","Bearer "+riderToken).contentType("application/json")
                .content("{\"incidentType\":\"FEVER\",\"observedSeverity\":\"MEDIUM\",\"description\":\"Horse unwell\"}"))
                .andExpect(status().isForbidden());
        String result=mvc.perform(post("/api/training-sessions/{id}/report-issue",session).header("Authorization","Bearer "+riderToken).contentType("application/json")
                .content("{\"incidentType\":\"FEVER\",\"observedSeverity\":\"MEDIUM\",\"description\":\"Horse unwell\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.sessionStatus").value("INTERRUPTED"))
                .andReturn().getResponse().getContentAsString();long incident=json.readTree(result).path("data").path("incidentId").longValue();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.incident WHERE id=? AND status='REPORTED' AND training_session_id=?",Integer.class,incident,session));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE notification_type='INCIDENT_REPORTED' AND target_id=?",Integer.class,session));
        String feedback="{\"responses\":{\"horseResponse\":\"Calm\",\"notes\":\"Stopped early\"}}";
        mvc.perform(post("/api/training-sessions/{id}/rider-feedback",session).header("Authorization","Bearer "+riderToken).contentType("application/json").content(feedback))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.sessionId").value(session));
        mvc.perform(post("/api/training-sessions/{id}/rider-feedback",session).header("Authorization","Bearer "+riderToken).contentType("application/json").content(feedback))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/work-rider/history").header("Authorization","Bearer "+riderToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(2));
        mvc.perform(post("/api/training-sessions/{id}/stop",otherSession).header("Authorization","Bearer "+trainerToken).contentType("application/json").content("{\"reason\":\"Stopped for review\",\"medicallyRelevant\":true,\"incidentType\":\"OTHER\",\"observedSeverity\":\"LOW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("INTERRUPTED"));
        mvc.perform(get("/api/work-rider/assignments").header("Authorization","Bearer "+otherToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.incident WHERE reported_by_user_id=? AND training_session_id=?",Integer.class,trainer,otherSession));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.app_user WHERE id=?",Integer.class,vet));
    }

    private long session(String state,long rider,String code){jdbc.update("INSERT INTO dbo.training_session(session_code,horse_program_phase_id,created_by_user_id,work_rider_user_id,scheduled_start,status,actual_start) VALUES (?,1,?,?,?, ?,?)",code,280001,rider,java.sql.Timestamp.from(Instant.now().minusSeconds(60)),state,"IN_PROGRESS".equals(state)?java.sql.Timestamp.from(Instant.now().minusSeconds(30)):null);return jdbc.queryForObject("SELECT id FROM dbo.training_session WHERE session_code=?",Long.class,code);}
    private void assign(long trainer){jdbc.update("INSERT INTO dbo.trainer_assignment(id,horse_training_program_id,trainer_user_id,assigned_by_user_id,effective_from) VALUES (1,1,?,?,?)",trainer,trainer,java.sql.Date.valueOf(LocalDate.now().minusDays(1)));}
    private long addUser(RoleCode role){long id=IDS.incrementAndGet();String email="core028-"+id+"@test.invalid";jdbc.update("INSERT INTO dbo.app_user(id,user_code,full_name,email,password_hash,account_status) VALUES (?,?,?,?,?,'ACTIVE')",id,"U"+id,"Core028 User",email,encoder.encode(PASSWORD));jdbc.update("INSERT INTO dbo.user_role(id,user_id,role_id) VALUES (?,?,?)",id,id,role.ordinal()+1);return id;}
    private String login(long id)throws Exception{String raw=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email","core028-"+id+"@test.invalid","password",PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();JsonNode node=json.readTree(raw);return node.path("data").path("accessToken").stringValue();}
    private void tables(){
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.role(id BIGINT PRIMARY KEY,role_code VARCHAR(30) UNIQUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.app_user(id BIGINT PRIMARY KEY,user_code VARCHAR(20),full_name NVARCHAR(150),email NVARCHAR(254),password_hash VARCHAR(255),account_status VARCHAR(20),last_login_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.user_role(id BIGINT PRIMARY KEY,user_id BIGINT,role_id BIGINT,assigned_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse(id BIGINT PRIMARY KEY,horse_code VARCHAR(20),name NVARCHAR(150),lifecycle VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse_training_program(id BIGINT PRIMARY KEY,horse_id BIGINT,status VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse_program_phase(id BIGINT PRIMARY KEY,horse_training_program_id BIGINT,name NVARCHAR(150))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.trainer_assignment(id BIGINT PRIMARY KEY,horse_training_program_id BIGINT,trainer_user_id BIGINT,effective_from DATE,effective_to DATE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.training_session(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,session_code VARCHAR(20),horse_program_phase_id BIGINT,created_by_user_id BIGINT,work_rider_user_id BIGINT,scheduled_start TIMESTAMP,scheduled_end TIMESTAMP,status VARCHAR(20),actual_start TIMESTAMP,actual_end TIMESTAMP,interruption_reason NVARCHAR(1000),updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.medical_restriction(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,horse_id BIGINT,restriction_level VARCHAR(20),lifted_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.pre_session_check(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,training_session_id BIGINT UNIQUE,overall_result VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.rider_feedback(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,training_session_id BIGINT UNIQUE,submitted_by_user_id BIGINT,submitted_at TIMESTAMP,responses NVARCHAR(4000))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.incident(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,incident_code VARCHAR(20) UNIQUE,horse_id BIGINT,reported_by_user_id BIGINT,training_session_id BIGINT,observed_at TIMESTAMP,reported_at TIMESTAMP,incident_type VARCHAR(30),observed_severity VARCHAR(20),description NVARCHAR(2000),status VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.notification(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,recipient_user_id BIGINT,category VARCHAR(20),notification_type VARCHAR(50),title NVARCHAR(200),message NVARCHAR(1000),target_type VARCHAR(50),target_id BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
    }
}
