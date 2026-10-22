package com.racehorse.management;

import com.racehorse.TestJwtProperties;
import com.racehorse.auth.JwtTokenService;
import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:management_dashboard;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class ManagementSummaryApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    MockMvc mvc;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwtProperties.register(registry);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.registration_request (id BIGINT PRIMARY KEY, status VARCHAR(20) NOT NULL)");
    }

    @Test
    void reportsPersistedCoreCountsAndOnlySafeNewestAuditFields() throws Exception {
        String token = tokenFor(RoleCode.CLUB_MANAGER);
        jdbc.update("INSERT INTO dbo.app_user(id,user_code,full_name,email,password_hash,account_status) VALUES(90,'INACTIVE','Inactive','private@example.test','private-hash','INACTIVE')");
        for (int id = 1; id <= 5; id++) {
            jdbc.update("INSERT INTO dbo.horse(id,horse_code,name,lifecycle) VALUES(?,?,?,?)",
                    id, "H-" + id, "Horse " + id, id <= 3 ? "ACTIVE" : id == 4 ? "DRAFT" : "INACTIVE");
        }
        jdbc.update("INSERT INTO dbo.medical_restriction(horse_id,set_by_user_id,restriction_level,reason,effective_from) VALUES(1,?,'LOCKED','private clinical reason',CURRENT_TIMESTAMP)", actorId(RoleCode.CLUB_MANAGER));
        jdbc.update("INSERT INTO dbo.medical_restriction(horse_id,set_by_user_id,restriction_level,reason,effective_from) VALUES(2,?,'LIMITED','private clinical reason',CURRENT_TIMESTAMP)", actorId(RoleCode.CLUB_MANAGER));
        jdbc.update("INSERT INTO dbo.medical_restriction(horse_id,set_by_user_id,restriction_level,reason,effective_from,lifted_at) VALUES(3,?,'LOCKED','lifted',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", actorId(RoleCode.CLUB_MANAGER));
        jdbc.update("INSERT INTO dbo.stable_box(id,box_code) VALUES(1,'A-01'),(2,'A-02')");
        jdbc.update("INSERT INTO dbo.horse_stable_assignment(horse_id,stable_box_id,assigned_by_user_id,effective_from) VALUES(1,1,?,CURRENT_DATE)", actorId(RoleCode.CLUB_MANAGER));
        jdbc.update("INSERT INTO dbo.horse_stable_assignment(horse_id,stable_box_id,assigned_by_user_id,effective_from,effective_to) VALUES(2,2,?,CURRENT_DATE,CURRENT_DATE)", actorId(RoleCode.CLUB_MANAGER));
        jdbc.update("INSERT INTO dbo.registration_request(id,status) VALUES(1,'PENDING_APPROVAL'),(2,'APPROVED')");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime clubMidnightUtc = LocalDateTime.ofInstant(today.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneOffset.UTC);
        for (int id = 1; id <= 4; id++) {
            jdbc.update("INSERT INTO dbo.training_session(id,session_code,horse_program_phase_id,created_by_user_id,work_rider_user_id,scheduled_start,status) VALUES(?,?,1,?,?,?,?)",
                    id, "SESSION-" + id, actorId(RoleCode.CLUB_MANAGER), actorId(RoleCode.CLUB_MANAGER),
                    id == 1 ? clubMidnightUtc : id == 2 ? clubMidnightUtc.plusHours(12) : id == 3 ? clubMidnightUtc.plusDays(1) : clubMidnightUtc.minusNanos(1_000_000),
                    id == 2 ? "COMPLETED" : "SCHEDULED");
        }
        for (int id = 1; id <= 3; id++) {
            jdbc.update("INSERT INTO dbo.incident(incident_code,horse_id,reported_by_user_id,observed_at,reported_at,incident_type,observed_severity,status) VALUES(?,1,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'OTHER','LOW',?)",
                    "INC-" + id, actorId(RoleCode.CLUB_MANAGER), id == 1 ? "REPORTED" : id == 2 ? "UNDER_REVIEW" : "RESOLVED");
        }
        for (int id = 1; id <= 12; id++) {
            jdbc.update("INSERT INTO dbo.audit_log(id,actor_user_id,occurred_at,actor_role,action,safe_summary,source) VALUES(?,?,?,'CLUB_MANAGER','HORSE_REGISTERED','Horse registered.','WEB')",
                    id, actorId(RoleCode.CLUB_MANAGER), LocalDate.now(ZoneOffset.UTC).atTime(0, id));
        }

        mvc.perform(get("/api/dashboard/club-manager").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horsesByLifecycle[0].key").value("ACTIVE"))
                .andExpect(jsonPath("$.data.horsesByLifecycle[0].count").value(3))
                .andExpect(jsonPath("$.data.horsesByAvailability.length()").value(3))
                .andExpect(jsonPath("$.data.horsesByAvailability[0].key").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.horsesByAvailability[0].count").value(1))
                .andExpect(jsonPath("$.data.boxOccupancy.total").value(2))
                .andExpect(jsonPath("$.data.boxOccupancy.occupied").value(1))
                .andExpect(jsonPath("$.data.boxOccupancy.free").value(1))
                .andExpect(jsonPath("$.data.accountsByStatus[1].key").value("INACTIVE"))
                .andExpect(jsonPath("$.data.accountsByStatus[1].count").value(1))
                .andExpect(jsonPath("$.data.needsAttention.pendingActivations").value(1))
                .andExpect(jsonPath("$.data.needsAttention.pendingRegistrations").value(1))
                .andExpect(jsonPath("$.data.trainingToday.byStatus.length()").value(2))
                .andExpect(jsonPath("$.data.trainingToday.byStatus[1].key").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.trainingToday.byStatus[1].count").value(1))
                .andExpect(jsonPath("$.data.openIncidents").value(2))
                .andExpect(jsonPath("$.data.recentAudit.length()").value(10))
                .andExpect(jsonPath("$.data.recentAudit[0].id").value(12))
                .andExpect(jsonPath("$.data.recentAudit[0].actor.fullName").value("Manager Test"))
                .andExpect(content().string(not(containsString("private-hash"))))
                .andExpect(content().string(not(containsString("private@example.test"))))
                .andExpect(content().string(not(containsString("private clinical reason"))));
    }

    @Test
    void rejectsAnonymousAndAllOtherRoles() throws Exception {
        mvc.perform(get("/api/dashboard/club-manager")).andExpect(status().isUnauthorized());
        for (RoleCode role : RoleCode.values()) {
            if (role != RoleCode.CLUB_MANAGER) {
                mvc.perform(get("/api/dashboard/club-manager")
                                .header("Authorization", "Bearer " + tokenFor(role)))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void emptyDatabaseReturnsActualZeroCountsAndNoAudit() throws Exception {
        mvc.perform(get("/api/dashboard/club-manager")
                        .header("Authorization", "Bearer " + tokenFor(RoleCode.CLUB_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horsesByLifecycle").isEmpty())
                .andExpect(jsonPath("$.data.horsesByAvailability").isEmpty())
                .andExpect(jsonPath("$.data.boxOccupancy.total").value(0))
                .andExpect(jsonPath("$.data.boxOccupancy.free").value(0))
                .andExpect(jsonPath("$.data.openIncidents").value(0))
                .andExpect(jsonPath("$.data.recentAudit").isEmpty());
    }

    private long actorId(RoleCode role) {
        return 100 + role.ordinal();
    }

    private String tokenFor(RoleCode role) {
        long userId = actorId(role);
        jdbc.update("INSERT INTO dbo.app_user(id,user_code,full_name,email,account_status) VALUES(?,?,?,?, 'ACTIVE')",
                userId, "USER-" + userId, "Manager Test", "user-" + userId + "@example.test");
        jdbc.update("INSERT INTO dbo.role(id,role_code) VALUES(?,?)", userId, role.name());
        jdbc.update("INSERT INTO dbo.user_role(id,user_id,role_id) VALUES(?,?,?)", userId, userId, userId);
        return tokens.issue(userId, List.of(role)).value();
    }
}
