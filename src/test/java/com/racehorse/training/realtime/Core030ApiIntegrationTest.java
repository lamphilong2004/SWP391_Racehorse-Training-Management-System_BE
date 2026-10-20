package com.racehorse.training.realtime;

import com.racehorse.TestJwtProperties;
import com.racehorse.auth.PendingRegistrationRepository;
import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
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
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class Core030ApiIntegrationTest {
    private static final AtomicLong IDS=new AtomicLong(300_000);private static final String PASSWORD="Core030-Test-123";
    @Autowired WebApplicationContext context;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;@Autowired JsonMapper json;
    @MockitoBean PendingRegistrationRepository pendingRegistrationRepository;MockMvc mvc;
    @Autowired Core030StompInterceptor stompInterceptor;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){TestJwtProperties.register(r);r.add("spring.datasource.url",()->"jdbc:h2:mem:racehorse_core030_api;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");r.add("app.medical.thresholds.heart-rate-critical",()->"180");r.add("app.medical.thresholds.heart-rate-warning",()->"150");}
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();tables();
        for(String t:new String[]{"monitoring_alert","training_metric_sample","horse_threshold_override","trainer_assignment","training_session","horse_program_phase","horse_training_program","horse","notification","user_role","app_user","role"})jdbc.update("DELETE FROM dbo."+t);
        for(RoleCode r:RoleCode.values())jdbc.update("INSERT INTO dbo.role(id,role_code) VALUES (?,?)",r.ordinal()+1,r.name());
        long programCreator=user(RoleCode.CLUB_MANAGER);
        LocalDate today=LocalDate.now();
        jdbc.update("INSERT INTO dbo.horse(id,horse_code,name,lifecycle) VALUES (1,'H-1','Horse One','ACTIVE')");
        jdbc.update("""
                INSERT INTO dbo.horse_training_program
                    (id, horse_id, template_version_id, created_by_user_id, name, status,
                     period_start, period_end, activated_at)
                VALUES (1, 1, 1, ?, 'Realtime test program', 'ACTIVE', ?, ?, ?)
                """, programCreator, java.sql.Date.valueOf(today), java.sql.Date.valueOf(today.plusDays(7)),
                java.sql.Timestamp.from(Instant.now()));
        jdbc.update("""
                INSERT INTO dbo.horse_program_phase
                    (id, horse_training_program_id, name, phase_order, start_date, end_date)
                VALUES (1, 1, 'Realtime test phase', 1, ?, ?)
                """, java.sql.Date.valueOf(today), java.sql.Date.valueOf(today.plusDays(7)));
    }

    @Test void metricCrossingsCreateSingleCriticalAlertsAndTrainerActionIsAtomic() throws Exception {
        long trainer=user(RoleCode.TRAINER),rider=user(RoleCode.WORK_RIDER),vet=user(RoleCode.VETERINARIAN),other=user(RoleCode.WORK_RIDER),manager=user(RoleCode.CLUB_MANAGER);jdbc.update("INSERT INTO dbo.trainer_assignment(id,horse_training_program_id,trainer_user_id,assigned_by_user_id,effective_from) VALUES (1,1,?,?,?)",trainer,manager,java.sql.Date.valueOf(LocalDate.now().minusDays(1)));
        long session=session(rider,"IN_PROGRESS","S-1"),foreign=session(other,"IN_PROGRESS","S-2");String token=login(rider),otherToken=login(other),trainerToken=login(trainer),vetToken=login(vet);
        metric(token,session,95,"NORMAL");metric(token,session,185,"CRITICAL");long first=latestAlert(session);metric(token,session,190,"CRITICAL");metric(token,session,140,"NORMAL");metric(token,session,186,"CRITICAL");long second=latestAlert(session);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.monitoring_alert WHERE training_session_id=?",Integer.class,session));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE notification_type='MONITORING_ALERT_CRITICAL' AND target_id=?",Integer.class,session));
        mvc.perform(post("/api/training-sessions/{id}/metrics",foreign).header("Authorization","Bearer "+token).contentType("application/json").content("{\"recordedAt\":\""+Instant.now()+"\",\"heartRate\":100}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/monitoring-alerts/{id}/acknowledge",first).header("Authorization","Bearer "+vetToken).contentType("application/json").content("{\"action\":\"PAUSE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/monitoring-alerts/{id}/acknowledge",first).header("Authorization","Bearer "+trainerToken).contentType("application/json").content("{\"action\":\"PAUSE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.actionTaken").value("PAUSE"));
        assertEquals("PAUSED",jdbc.queryForObject("SELECT status FROM dbo.training_session WHERE id=?",String.class,session));
        mvc.perform(post("/api/monitoring-alerts/{id}/acknowledge",first).header("Authorization","Bearer "+trainerToken).contentType("application/json").content("{\"action\":\"ACKNOWLEDGE\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/monitoring-alerts/{id}/acknowledge",second).header("Authorization","Bearer "+trainerToken).contentType("application/json").content("{\"action\":\"STOP\",\"stopReason\":\"Critical reading\",\"medicallyRelevant\":true,\"incidentType\":\"FEVER\",\"observedSeverity\":\"HIGH\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.actionTaken").value("STOP"));
        assertEquals("INTERRUPTED",jdbc.queryForObject("SELECT status FROM dbo.training_session WHERE id=?",String.class,session));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.incident WHERE training_session_id=? AND status='REPORTED'",Integer.class,session));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.user_role WHERE user_id=?",Integer.class,other));
    }

    @Test void stompConnectAndSubscriptionsEnforceJwtAndSessionScope() throws Exception {
        long rider=user(RoleCode.WORK_RIDER),other=user(RoleCode.WORK_RIDER);
        long ownSession=session(rider,"IN_PROGRESS","S-WS-OWN"),foreignSession=session(other,"IN_PROGRESS","S-WS-OTHER");
        String token=login(rider);MessageChannel channel=mock(MessageChannel.class);
        StompHeaderAccessor connect=StompHeaderAccessor.create(StompCommand.CONNECT);connect.addNativeHeader("Authorization","Bearer "+token);connect.setLeaveMutable(true);
        Message<byte[]> connectMessage=MessageBuilder.createMessage(new byte[0],connect.getMessageHeaders());
        stompInterceptor.preSend(connectMessage,channel);

        StompHeaderAccessor allowed=StompHeaderAccessor.create(StompCommand.SUBSCRIBE);allowed.setUser(connect.getUser());allowed.setDestination("/topic/sessions/"+ownSession+"/metrics");allowed.setLeaveMutable(true);
        stompInterceptor.preSend(MessageBuilder.createMessage(new byte[0],allowed.getMessageHeaders()),channel);

        StompHeaderAccessor forbiddenTopic=StompHeaderAccessor.create(StompCommand.SUBSCRIBE);forbiddenTopic.setUser(connect.getUser());forbiddenTopic.setDestination("/topic/sessions/"+ownSession+"/alerts");forbiddenTopic.setLeaveMutable(true);
        assertThrows(AccessDeniedException.class,()->stompInterceptor.preSend(MessageBuilder.createMessage(new byte[0],forbiddenTopic.getMessageHeaders()),channel));
        StompHeaderAccessor foreign=StompHeaderAccessor.create(StompCommand.SUBSCRIBE);foreign.setUser(connect.getUser());foreign.setDestination("/topic/sessions/"+foreignSession+"/metrics");foreign.setLeaveMutable(true);
        assertThrows(AccessDeniedException.class,()->stompInterceptor.preSend(MessageBuilder.createMessage(new byte[0],foreign.getMessageHeaders()),channel));
        StompHeaderAccessor send=StompHeaderAccessor.create(StompCommand.SEND);send.setUser(connect.getUser());send.setDestination("/topic/sessions/"+ownSession+"/metrics");send.setLeaveMutable(true);
        assertThrows(AccessDeniedException.class,()->stompInterceptor.preSend(MessageBuilder.createMessage(new byte[0],send.getMessageHeaders()),channel));
    }

    private long metric(String token,long session,int rate,String state)throws Exception{String body="{\"recordedAt\":\""+Instant.now()+"\",\"heartRate\":"+rate+"}";String raw=mvc.perform(post("/api/training-sessions/{id}/metrics",session).header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.realtimeState").value(state)).andReturn().getResponse().getContentAsString();return json.readTree(raw).path("data").path("sampleId").longValue();}
    private long latestAlert(long session){return jdbc.queryForObject("SELECT MAX(id) FROM dbo.monitoring_alert WHERE training_session_id=?",Long.class,session);}
    private long session(long rider,String status,String code){jdbc.update("INSERT INTO dbo.training_session(session_code,horse_program_phase_id,created_by_user_id,work_rider_user_id,scheduled_start,status,actual_start) VALUES (?,1,?,?,?, ?,CURRENT_TIMESTAMP)",code,rider,rider,java.sql.Timestamp.from(Instant.now().minusSeconds(120)),status);return jdbc.queryForObject("SELECT id FROM dbo.training_session WHERE session_code=?",Long.class,code);}
    private long user(RoleCode r){long id=IDS.incrementAndGet();String email="core030-"+id+"@test.invalid";jdbc.update("INSERT INTO dbo.app_user(id,user_code,full_name,email,password_hash,account_status) VALUES (?,?,?, ?,?,'ACTIVE')",id,"U"+id,"Core030 User",email,encoder.encode(PASSWORD));jdbc.update("INSERT INTO dbo.user_role(id,user_id,role_id) VALUES (?,?,?)",id,id,r.ordinal()+1);return id;}
    private String login(long id)throws Exception{String raw=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email","core030-"+id+"@test.invalid","password",PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();JsonNode node=json.readTree(raw);return node.path("data").path("accessToken").stringValue();}
    private void tables(){
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.role(id BIGINT PRIMARY KEY,role_code VARCHAR(30) UNIQUE)");jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.app_user(id BIGINT PRIMARY KEY,user_code VARCHAR(20),full_name NVARCHAR(150),email NVARCHAR(254),password_hash VARCHAR(255),account_status VARCHAR(20),last_login_at TIMESTAMP)");jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.user_role(id BIGINT PRIMARY KEY,user_id BIGINT,role_id BIGINT,assigned_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse(id BIGINT PRIMARY KEY,horse_code VARCHAR(20),name NVARCHAR(150),lifecycle VARCHAR(20))");jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse_training_program(id BIGINT PRIMARY KEY,horse_id BIGINT,template_version_id BIGINT,created_by_user_id BIGINT,name NVARCHAR(150),status VARCHAR(20),period_start DATE,period_end DATE,activated_at TIMESTAMP)");jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse_program_phase(id BIGINT PRIMARY KEY,horse_training_program_id BIGINT,name NVARCHAR(150),phase_order SMALLINT,start_date DATE,end_date DATE)");jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.trainer_assignment(id BIGINT PRIMARY KEY,horse_training_program_id BIGINT,trainer_user_id BIGINT,assigned_by_user_id BIGINT,effective_from DATE,effective_to DATE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.training_session(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,session_code VARCHAR(20),horse_program_phase_id BIGINT,created_by_user_id BIGINT,work_rider_user_id BIGINT,scheduled_start TIMESTAMP,status VARCHAR(20),actual_start TIMESTAMP,actual_end TIMESTAMP,interruption_reason NVARCHAR(1000),updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse_threshold_override(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,horse_id BIGINT,set_by_user_id BIGINT,metric VARCHAR(20),critical_value DECIMAL(7,2),effective_from TIMESTAMP,effective_to TIMESTAMP)");jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.training_metric_sample(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,training_session_id BIGINT,recorded_at TIMESTAMP,heart_rate SMALLINT,speed DECIMAL(6,2),distance DECIMAL(9,2),source VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.monitoring_alert(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,training_session_id BIGINT,acknowledged_by_user_id BIGINT,metric VARCHAR(20),metric_value DECIMAL(7,2),threshold_applied DECIMAL(7,2),severity VARCHAR(20),raised_at TIMESTAMP,acknowledged_at TIMESTAMP,action_taken VARCHAR(30),escalated_to_vet_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.notification(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,recipient_user_id BIGINT,category VARCHAR(20),notification_type VARCHAR(50),title NVARCHAR(200),message NVARCHAR(1000),target_type VARCHAR(50),target_id BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.incident(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,incident_code VARCHAR(20),horse_id BIGINT,reported_by_user_id BIGINT,training_session_id BIGINT,observed_at TIMESTAMP,reported_at TIMESTAMP,incident_type VARCHAR(30),observed_severity VARCHAR(20),description NVARCHAR(2000),status VARCHAR(20))");
    }
}
