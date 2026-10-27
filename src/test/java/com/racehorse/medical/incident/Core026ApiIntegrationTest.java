package com.racehorse.medical.incident;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class Core026ApiIntegrationTest {
    private static final AtomicLong IDS=new AtomicLong(260_000);
    private static final String PASSWORD="Core026-Test-123";
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired JsonMapper json;
    @MockitoBean PendingRegistrationRepository pendingRegistrationRepository;
    private MockMvc mvc;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        TestJwtProperties.register(r);
        r.add("spring.datasource.url",()->"jdbc:h2:mem:racehorse_core026_api;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach void setUp(){
        IDS.set(260_000);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        createTables();
        jdbc.update("DELETE FROM dbo.incident");jdbc.update("DELETE FROM dbo.pre_session_check");jdbc.update("DELETE FROM dbo.notification");
        jdbc.update("DELETE FROM dbo.training_session_groom_assignment");
        jdbc.update("DELETE FROM dbo.training_session");jdbc.update("DELETE FROM dbo.horse_program_phase");jdbc.update("DELETE FROM dbo.trainer_assignment");
        jdbc.update("DELETE FROM dbo.horse_training_program");jdbc.update("DELETE FROM dbo.horse");jdbc.update("DELETE FROM dbo.medical_case");
        jdbc.update("DELETE FROM dbo.user_role");jdbc.update("DELETE FROM dbo.app_user");jdbc.update("DELETE FROM dbo.role");
        for(RoleCode role:RoleCode.values())jdbc.update("INSERT INTO dbo.role(id,role_code) VALUES (?,?)",role.ordinal()+1,role.name());
        jdbc.update("INSERT INTO dbo.horse(id,horse_code,name,lifecycle) VALUES (1,'H-1','Horse One','ACTIVE'),(2,'H-2','Horse Two','ACTIVE')");
        jdbc.update("""
                INSERT INTO dbo.horse_training_program
                    (id,horse_id,template_version_id,created_by_user_id,name,status,period_start,period_end,activated_at)
                VALUES (1,1,1,260001,'Incident test program one','ACTIVE',CURRENT_DATE,CURRENT_DATE,CURRENT_TIMESTAMP),
                       (2,2,1,260001,'Incident test program two','ACTIVE',CURRENT_DATE,CURRENT_DATE,CURRENT_TIMESTAMP)
                """);
        LocalDate today=LocalDate.now();
        jdbc.update("""
                INSERT INTO dbo.horse_program_phase
                    (id,horse_training_program_id,name,phase_order,start_date,end_date)
                VALUES (1,1,'Incident test phase one',1,?,?),(2,2,'Incident test phase two',1,?,?)
                """,java.sql.Date.valueOf(today),java.sql.Date.valueOf(today),
                java.sql.Date.valueOf(today),java.sql.Date.valueOf(today));
        jdbc.update("INSERT INTO dbo.trainer_assignment(id,horse_training_program_id,trainer_user_id,assigned_by_user_id,effective_from) VALUES (1,1,260001,260001,?)",java.sql.Date.valueOf(today.minusDays(1)));
        jdbc.update("INSERT INTO dbo.training_session(id,session_code,horse_program_phase_id,created_by_user_id,work_rider_user_id,scheduled_start,status) VALUES (1,'S-1',1,260001,260002,?,'SCHEDULED'),(2,'S-2',1,260001,260002,?,'SCHEDULED'),(3,'S-3',1,260001,260002,?,'SCHEDULED')",java.sql.Timestamp.from(Instant.now().plusSeconds(600)),java.sql.Timestamp.from(Instant.now().plusSeconds(3600)),java.sql.Timestamp.from(Instant.now().plusSeconds(7200)));
    }

    @Test void groomMustBeNotifiedAndChecklistResultIsDerivedWithoutAutoIncident() throws Exception {
        String groom=login(260003,RoleCode.GROOM);
        String normal=checklist("NORMAL");
        mvc.perform(post("/api/training-sessions/1/pre-session-check").header("Authorization","Bearer "+groom).contentType("application/json").content(normal))
                .andExpect(status().isForbidden());
        notice(260003,1);
        String result=mvc.perform(post("/api/training-sessions/1/pre-session-check").header("Authorization","Bearer "+groom).contentType("application/json").content(normal))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.overallResult").value("ALL_NORMAL"))
                .andExpect(jsonPath("$.data.performedBy.id").value(260003)).andReturn().getResponse().getContentAsString();
        long checkId=json.readTree(result).path("data").path("id").longValue();
        org.junit.jupiter.api.Assertions.assertEquals("SCHEDULED",jdbc.queryForObject("SELECT status FROM dbo.training_session WHERE id=1",String.class));
        org.junit.jupiter.api.Assertions.assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.incident",Integer.class));
        mvc.perform(post("/api/training-sessions/1/pre-session-check").header("Authorization","Bearer "+groom).contentType("application/json").content(normal))
                .andExpect(status().isConflict());

        notice(260003,2);
        mvc.perform(post("/api/training-sessions/2/pre-session-check").header("Authorization","Bearer "+groom).contentType("application/json").content(checklist("ABNORMAL")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.overallResult").value("ABNORMAL"));
        org.junit.jupiter.api.Assertions.assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.incident",Integer.class));
        // BR-23: the abnormal check hands over to Flow 3 by notifying the assigned Trainer (and any Veterinarian).
        org.junit.jupiter.api.Assertions.assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE notification_type='PRECHECK_ABNORMAL' AND recipient_user_id=260001 AND target_id=2",Integer.class));
        // A check more than 12 hours before the scheduled start is refused.
        jdbc.update("INSERT INTO dbo.training_session(id,session_code,horse_program_phase_id,created_by_user_id,work_rider_user_id,scheduled_start,status) VALUES (4,'S-4',1,260001,260002,?,'SCHEDULED')",java.sql.Timestamp.from(Instant.now().plusSeconds(2*86400)));
        notice(260003,4);
        mvc.perform(post("/api/training-sessions/4/pre-session-check").header("Authorization","Bearer "+groom).contentType("application/json").content(normal))
                .andExpect(status().isConflict());

        String incident=String.format("{\"horseId\":1,\"preSessionCheckId\":%d,\"incidentType\":\"FEVER\",\"observedSeverity\":\"MEDIUM\",\"observedAt\":\"%s\"}",checkId,Instant.now());
        mvc.perform(post("/api/incidents").header("Authorization","Bearer "+groom).contentType("application/json").content(incident))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("REPORTED"));
    }

    @Test void incidentsEnforceReporterScopeAndVetReviewTransitionsAtomically() throws Exception {
        long trainerId=addUser(RoleCode.TRAINER);String trainer=login(trainerId,RoleCode.TRAINER);
        long riderId=addUser(RoleCode.WORK_RIDER);String rider=login(riderId,RoleCode.WORK_RIDER);
        long vetId=addUser(RoleCode.VETERINARIAN);String vet=login(vetId,RoleCode.VETERINARIAN);
        long ownerId=addUser(RoleCode.HORSE_OWNER);String owner=login(ownerId,RoleCode.HORSE_OWNER);
        jdbc.update("INSERT INTO dbo.trainer_assignment(id,horse_training_program_id,trainer_user_id,assigned_by_user_id,effective_from) VALUES (2,1,?,?,?)",trainerId,trainerId,java.sql.Date.valueOf(LocalDate.now().minusDays(1)));

        String trainerReport=String.format("{\"horseId\":1,\"incidentType\":\"COLIC_SIGNS\",\"observedSeverity\":\"HIGH\",\"description\":\"Observed unwell\",\"observedAt\":\"%s\"}",Instant.now());
        String response=mvc.perform(post("/api/incidents").header("Authorization","Bearer "+trainer).contentType("application/json").content(trainerReport))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("REPORTED"))
                .andReturn().getResponse().getContentAsString();long incidentId=json.readTree(response).path("data").path("id").longValue();
        // BR-27: every reported incident notifies the Veterinarian.
        org.junit.jupiter.api.Assertions.assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE notification_type='INCIDENT_REPORTED' AND recipient_user_id=? AND target_id=?",Integer.class,vetId,incidentId));
        mvc.perform(get("/api/incidents").header("Authorization","Bearer "+trainer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/incidents/{id}",incidentId).header("Authorization","Bearer "+owner)).andExpect(status().isForbidden());
        String open="{\"action\":\"OPEN_CASE\"}";
        mvc.perform(post("/api/incidents/{id}/review",incidentId).header("Authorization","Bearer "+vet).contentType("application/json").content(open))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.data.medicalCaseId").exists());
        mvc.perform(post("/api/incidents/{id}/review",incidentId).header("Authorization","Bearer "+vet).contentType("application/json").content(open))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/incidents/{id}/review",incidentId).header("Authorization","Bearer "+vet).contentType("application/json").content("{\"action\":\"RESOLVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RESOLVED"));

        mvc.perform(get("/api/incidents").header("Authorization","Bearer "+vet)).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/incidents").header("Authorization","Bearer "+rider)).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/incidents").header("Authorization","Bearer "+owner)).andExpect(status().isForbidden());
    }

    private void notice(long groom,long session){
        jdbc.update("INSERT INTO dbo.training_session_groom_assignment(training_session_id,groom_user_id,assigned_by_user_id) VALUES (?,?,260001)",session,groom);
        jdbc.update("INSERT INTO dbo.notification(recipient_user_id,category,notification_type,title,message,target_type,target_id) VALUES (?,'SCHEDULE','GROOM_PRECHECK_ASSIGNED','Precheck','Session precheck','TRAINING_SESSION',?)",groom,session);
    }
    private String checklist(String result){return "{\"checks\":{\"GENERAL_CONDITION\":{\"result\":\""+result+"\"},\"APPETITE_BEHAVIOUR\":{\"result\":\"NORMAL\"},\"LEGS_HOOVES\":{\"result\":\"NORMAL\"},\"MOVEMENT\":{\"result\":\"NORMAL\"},\"TEMPERATURE\":{\"result\":\"NORMAL\"}}}";}
    private long addUser(RoleCode role){long id=IDS.incrementAndGet();String email="core026-"+id+"@test.invalid";jdbc.update("INSERT INTO dbo.app_user(id,user_code,full_name,email,password_hash,account_status) VALUES (?,?,?, ?,?,'ACTIVE')",id,"U"+id,"Core026 User",email,encoder.encode(PASSWORD));jdbc.update("INSERT INTO dbo.user_role(id,user_id,role_id) VALUES (?,?,?)",id,id,role.ordinal()+1);return id;}
    private String login(long id,RoleCode role)throws Exception{Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.app_user WHERE id=?",Integer.class,id);if(count==null||count==0){String email="core026-"+id+"@test.invalid";jdbc.update("INSERT INTO dbo.app_user(id,user_code,full_name,email,password_hash,account_status) VALUES (?,?,?, ?,?,'ACTIVE')",id,"U"+id,"Core026 User",email,encoder.encode(PASSWORD));jdbc.update("INSERT INTO dbo.user_role(id,user_id,role_id) VALUES (?,?,?)",id,id,role.ordinal()+1);}String email="core026-"+id+"@test.invalid";String body=json.writeValueAsString(Map.of("email",email,"password",PASSWORD));String raw=mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();JsonNode node=json.readTree(raw);return node.path("data").path("accessToken").stringValue();}

    private void createTables(){
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.role(id BIGINT PRIMARY KEY,role_code VARCHAR(30) UNIQUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.app_user(id BIGINT PRIMARY KEY,user_code VARCHAR(20),full_name NVARCHAR(150),email NVARCHAR(254),password_hash VARCHAR(255),account_status VARCHAR(20),last_login_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.user_role(id BIGINT PRIMARY KEY,user_id BIGINT,role_id BIGINT,assigned_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse(id BIGINT PRIMARY KEY,horse_code VARCHAR(20),name NVARCHAR(150),lifecycle VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse_training_program(id BIGINT PRIMARY KEY,horse_id BIGINT,status VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse_program_phase(id BIGINT PRIMARY KEY,horse_training_program_id BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.trainer_assignment(id BIGINT PRIMARY KEY,horse_training_program_id BIGINT,trainer_user_id BIGINT,effective_from DATE,effective_to DATE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.training_session(id BIGINT PRIMARY KEY,session_code VARCHAR(20),horse_program_phase_id BIGINT,created_by_user_id BIGINT,work_rider_user_id BIGINT,scheduled_start TIMESTAMP,status VARCHAR(20),actual_start TIMESTAMP,actual_end TIMESTAMP,interruption_reason NVARCHAR(1000))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.notification(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,recipient_user_id BIGINT,category VARCHAR(20),notification_type VARCHAR(50),title NVARCHAR(200),message NVARCHAR(1000),target_type VARCHAR(50),target_id BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.pre_session_check(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,training_session_id BIGINT UNIQUE,performed_by_user_id BIGINT,performed_at TIMESTAMP,observations NVARCHAR(4000),overall_result VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.incident(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,incident_code VARCHAR(20) UNIQUE,horse_id BIGINT,reported_by_user_id BIGINT,reviewed_by_user_id BIGINT,medical_case_id BIGINT,pre_session_check_id BIGINT UNIQUE,training_session_id BIGINT,care_task_id BIGINT,observed_at TIMESTAMP,reported_at TIMESTAMP,incident_type VARCHAR(30),observed_severity VARCHAR(20),description NVARCHAR(2000),status VARCHAR(20),updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.medical_case(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,horse_id BIGINT,opened_by_user_id BIGINT,status VARCHAR(20),opened_at TIMESTAMP,closed_at TIMESTAMP,condition_summary NVARCHAR(2000))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.care_task(id BIGINT PRIMARY KEY,horse_id BIGINT)");
    }
}
