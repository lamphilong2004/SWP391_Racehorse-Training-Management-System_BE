package com.racehorse.medical.restriction;

import com.racehorse.TestJwtProperties;
import com.racehorse.auth.AccountStatus;
import com.racehorse.auth.PendingRegistrationRepository;
import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class MedicalRestrictionSecurityIntegrationTest {

    private static final String PASSWORD = "Test-Password-123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

        @Autowired
        private JwtEncoder jwtEncoder;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private PendingRegistrationRepository pendingRegistrationRepository;

    private final AtomicLong nextId = new AtomicLong(72_000);
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwtProperties.register(registry);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:racehorse_restriction_security;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        registry.add("app.medical.thresholds.heart-rate-critical", () -> "178.25");
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        createRestrictionTables();
        jdbcTemplate.update("DELETE FROM dbo.restriction_exercise_rule");
        jdbcTemplate.update("DELETE FROM dbo.medical_restriction");
        jdbcTemplate.update("DELETE FROM dbo.horse_threshold_override");
        jdbcTemplate.update("DELETE FROM dbo.medical_case");
        jdbcTemplate.update("DELETE FROM dbo.training_session");
        jdbcTemplate.update("DELETE FROM dbo.horse_program_phase");
        jdbcTemplate.update("DELETE FROM dbo.trainer_assignment");
        jdbcTemplate.update("DELETE FROM dbo.horse_training_program");
        jdbcTemplate.update("DELETE FROM dbo.exercise_definition");
        jdbcTemplate.update("DELETE FROM dbo.horse");
        jdbcTemplate.update("DELETE FROM dbo.audit_log");
        jdbcTemplate.update("DELETE FROM dbo.user_role");
        jdbcTemplate.update("DELETE FROM dbo.app_user");
        jdbcTemplate.update("DELETE FROM dbo.role");
        nextId.set(72_000);
        for (RoleCode role : RoleCode.values()) {
            jdbcTemplate.update("INSERT INTO dbo.role (id, role_code) VALUES (?, ?)", role.ordinal() + 1, role.name());
        }
        jdbcTemplate.update("INSERT INTO dbo.horse (id, horse_code, name, lifecycle) VALUES (1, 'R-TEST-1', 'Test Horse One', 'ACTIVE'), (2, 'R-TEST-2', 'Test Horse Two', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO dbo.medical_case (id, horse_id) VALUES (10, 1)");
        jdbcTemplate.update("INSERT INTO dbo.horse_training_program (id, horse_id) VALUES (1, 1)");
        jdbcTemplate.update("INSERT INTO dbo.horse_program_phase (id, horse_training_program_id) VALUES (1, 1)");
        jdbcTemplate.update("INSERT INTO dbo.exercise_definition (id, name, exercise_type) VALUES (5, 'Working trot', 'TROT')");
    }

    @Test
    void missingInvalidAndExpiredTokensAreRejectedByCanonicalSecurityChain() throws Exception {
        mockMvc.perform(get("/api/horses/1/training-availability"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/horses/1/training-availability").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/horses/1/training-availability").header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedVeterinarianCreatesRestrictionUsingJwtActorAndReadsItBack() throws Exception {
        long veterinarianId = createUser(RoleCode.VETERINARIAN);
        String token = login(veterinarianId);

        mockMvc.perform(post("/api/horses/1/medical-restrictions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"level":"LOCKED","reason":"Operational rest","effectiveAt":"2026-10-16T10:00:00Z","setByUserId":999999}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.level").value("LOCKED"))
                .andExpect(jsonPath("$.data.setByUserId").doesNotExist());

        assertEquals(veterinarianId, jdbcTemplate.queryForObject(
                "SELECT set_by_user_id FROM dbo.medical_restriction WHERE horse_id = 1", Long.class));
        mockMvc.perform(get("/api/horses/1/training-availability").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability").value("LOCKED"))
                .andExpect(jsonPath("$.data.reason").value("Operational rest"))
                .andExpect(jsonPath("$.data.medicalCaseId").doesNotExist());
    }

    @Test
    void wrongRoleCannotWriteAndTrainerReadsOnlyAssignedHorse() throws Exception {
        long trainerId = createUser(RoleCode.TRAINER);
        String trainerToken = login(trainerId);
        jdbcTemplate.update("""
                INSERT INTO dbo.trainer_assignment (id, horse_training_program_id, trainer_user_id, effective_from)
                VALUES (1, 1, ?, ?)
                """, trainerId, java.sql.Date.valueOf(LocalDate.now().minusDays(1)));

        mockMvc.perform(post("/api/horses/1/medical-restrictions")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType("application/json")
                        .content("""
                                {"level":"LOCKED","reason":"Operational rest","effectiveAt":"2026-10-16T10:00:00Z"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/horses/1/training-availability").header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/horses/2/training-availability").header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void workRiderUsesActionableSessionScopeAndHorseOwnerIsDenied() throws Exception {
        long workRiderId = createUser(RoleCode.WORK_RIDER);
        String workRiderToken = login(workRiderId);
        jdbcTemplate.update("""
                INSERT INTO dbo.training_session (id, horse_program_phase_id, work_rider_user_id, status)
                VALUES (1, 1, ?, 'SCHEDULED')
                """, workRiderId);

        mockMvc.perform(get("/api/horses/1/training-availability").header("Authorization", "Bearer " + workRiderToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/horses/2/training-availability").header("Authorization", "Bearer " + workRiderToken))
                .andExpect(status().isForbidden());

        long ownerId = createUser(RoleCode.HORSE_OWNER);
        mockMvc.perform(get("/api/horses/1/training-availability").header("Authorization", "Bearer " + login(ownerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void groomReceivesOperationalProjectionOnly() throws Exception {
        long veterinarianId = createUser(RoleCode.VETERINARIAN);
        String vetToken = login(veterinarianId);
        mockMvc.perform(post("/api/horses/1/medical-restrictions")
                        .header("Authorization", "Bearer " + vetToken)
                        .contentType("application/json")
                        .content("""
                                {"level":"LIMITED","reason":"Avoid fast work","effectiveAt":"2026-10-16T10:00:00Z","medicalCaseId":10}
                                """))
                .andExpect(status().isCreated());

        long groomId = createUser(RoleCode.GROOM);
        mockMvc.perform(get("/api/horses/1/training-availability").header("Authorization", "Bearer " + login(groomId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability").value("LIMITED"))
                .andExpect(jsonPath("$.data.reason").value("Avoid fast work"))
                .andExpect(jsonPath("$.data.medicalCaseId").doesNotExist())
                .andExpect(jsonPath("$.data.diagnosis").doesNotExist());
    }

    @Test
    void thresholdsAreRoleScopedAndEffectiveOverrideLifecycleUsesJwtActor() throws Exception {
        long veterinarianId = createUser(RoleCode.VETERINARIAN);
        String vetToken = login(veterinarianId);
        long trainerId = createUser(RoleCode.TRAINER);
        String trainerToken = login(trainerId);
        jdbcTemplate.update("UPDATE dbo.horse_training_program SET status='ACTIVE' WHERE id=1");
        jdbcTemplate.update("INSERT INTO dbo.trainer_assignment(id,horse_training_program_id,trainer_user_id,effective_from) VALUES (?,?,?,?)",
                41L, 1L, trainerId, java.sql.Date.valueOf(LocalDate.now().minusDays(1)));

        mockMvc.perform(get("/api/medical/thresholds").header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].metricType").value("HEART_RATE"))
                .andExpect(jsonPath("$.data[0].thresholdValue").value(178.25))
                .andExpect(jsonPath("$.data[0].scope").value("GLOBAL"));
        mockMvc.perform(get("/api/horses/1/medical-thresholds").header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].scope").value("GLOBAL"));
        mockMvc.perform(get("/api/horses/2/medical-thresholds").header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isForbidden());

        String override = String.format("{\"metricType\":\"HEART_RATE\",\"thresholdValue\":155.50,\"reason\":\"Clinical override\",\"effectiveFrom\":\"%s\"}",
                java.time.Instant.now().minusSeconds(60));
        mockMvc.perform(put("/api/horses/1/medical-thresholds").header("Authorization", "Bearer " + vetToken)
                        .contentType("application/json").content(override))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.scope").value("HORSE_OVERRIDE"))
                .andExpect(jsonPath("$.data.thresholdValue").value(155.50));
        assertEquals(veterinarianId, jdbcTemplate.queryForObject(
                "SELECT set_by_user_id FROM dbo.horse_threshold_override WHERE horse_id=1", Long.class));
        mockMvc.perform(get("/api/horses/1/medical-thresholds").header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].scope").value("HORSE_OVERRIDE"))
                .andExpect(jsonPath("$.data[0].thresholdValue").value(155.50));

        String replacement = String.format("{\"metricType\":\"HEART_RATE\",\"thresholdValue\":160.00,\"effectiveFrom\":\"%s\"}",
                java.time.Instant.now().plusSeconds(2));
        mockMvc.perform(put("/api/horses/1/medical-thresholds").header("Authorization", "Bearer " + vetToken)
                        .contentType("application/json").content(replacement))
                .andExpect(status().isOk());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.horse_threshold_override WHERE horse_id=1 AND effective_to IS NULL", Integer.class));
        mockMvc.perform(post("/api/horses/1/medical-thresholds/end").header("Authorization", "Bearer " + vetToken)
                        .contentType("application/json").content(String.format("{\"effectiveTo\":\"%s\"}", java.time.Instant.now().plusSeconds(20))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.effectiveTo").exists());
        mockMvc.perform(put("/api/horses/1/medical-thresholds").header("Authorization", "Bearer " + trainerToken)
                        .contentType("application/json").content(override))
                .andExpect(status().isForbidden());
    }

    @Test
    void thresholdsRejectInvalidMetricAndOverlappingFutureOverride() throws Exception {
        String vetToken = login(createUser(RoleCode.VETERINARIAN));
        String future = java.time.Instant.now().plusSeconds(120).toString();
        String first = String.format("{\"metricType\":\"HEART_RATE\",\"thresholdValue\":140,\"effectiveFrom\":\"%s\"}", future);
        mockMvc.perform(put("/api/horses/1/medical-thresholds").header("Authorization", "Bearer " + vetToken)
                        .contentType("application/json").content(first)).andExpect(status().isOk());
        String earlier = String.format("{\"metricType\":\"HEART_RATE\",\"thresholdValue\":150,\"effectiveFrom\":\"%s\"}",
                java.time.Instant.now());
        mockMvc.perform(put("/api/horses/1/medical-thresholds").header("Authorization", "Bearer " + vetToken)
                        .contentType("application/json").content(earlier)).andExpect(status().isConflict());
        String unsupported = String.format("{\"metricType\":\"SPEED\",\"thresholdValue\":150,\"effectiveFrom\":\"%s\"}",
                java.time.Instant.now().plusSeconds(300));
        mockMvc.perform(put("/api/horses/2/medical-thresholds").header("Authorization", "Bearer " + vetToken)
                        .contentType("application/json").content(unsupported)).andExpect(status().isBadRequest());
    }

    private void createRestrictionTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dbo.horse (id BIGINT PRIMARY KEY, horse_code VARCHAR(20) NOT NULL UNIQUE, name NVARCHAR(150) NOT NULL, lifecycle VARCHAR(20) NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dbo.horse_threshold_override (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, horse_id BIGINT NOT NULL, set_by_user_id BIGINT NOT NULL, metric VARCHAR(20) NOT NULL, critical_value DECIMAL(7,2) NOT NULL, reason NVARCHAR(1000), effective_from TIMESTAMP(3) NOT NULL, effective_to TIMESTAMP(3), created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP(3))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dbo.medical_case (id BIGINT PRIMARY KEY, horse_id BIGINT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dbo.exercise_definition (id BIGINT PRIMARY KEY, name NVARCHAR(150) NOT NULL, exercise_type NVARCHAR(50))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dbo.horse_training_program (id BIGINT PRIMARY KEY, horse_id BIGINT NOT NULL, status VARCHAR(20))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dbo.trainer_assignment (id BIGINT PRIMARY KEY, horse_training_program_id BIGINT NOT NULL, trainer_user_id BIGINT NOT NULL, effective_from DATE NOT NULL, effective_to DATE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dbo.horse_program_phase (id BIGINT PRIMARY KEY, horse_training_program_id BIGINT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dbo.training_session (id BIGINT PRIMARY KEY, horse_program_phase_id BIGINT NOT NULL, work_rider_user_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL)");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS dbo.medical_restriction (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    horse_id BIGINT NOT NULL,
                    medical_case_id BIGINT,
                    set_by_user_id BIGINT NOT NULL,
                    restriction_level VARCHAR(20) NOT NULL,
                    reason NVARCHAR(1000) NOT NULL,
                    effective_from TIMESTAMP(3) NOT NULL,
                    lifted_at TIMESTAMP(3),
                    review_date DATE,
                    created_at TIMESTAMP(3) NOT NULL,
                    updated_at TIMESTAMP(3),
                    active_horse_id BIGINT GENERATED ALWAYS AS
                        (CASE WHEN lifted_at IS NULL THEN horse_id ELSE NULL END),
                    CONSTRAINT uq_medical_restriction_active_horse UNIQUE (active_horse_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS dbo.restriction_exercise_rule (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    medical_restriction_id BIGINT NOT NULL,
                    exercise_definition_id BIGINT,
                    rule_type VARCHAR(20) NOT NULL,
                    exercise_type_note NVARCHAR(200),
                    created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP(3),
                    FOREIGN KEY (medical_restriction_id) REFERENCES dbo.medical_restriction(id),
                    FOREIGN KEY (exercise_definition_id) REFERENCES dbo.exercise_definition(id)
                )
                """);
    }

    private long createUser(RoleCode role) {
        long userId = nextId.getAndIncrement();
        String email = "restriction-" + userId + "@example.test";
        jdbcTemplate.update("""
                INSERT INTO dbo.app_user (id, user_code, full_name, email, password_hash, account_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, "R-" + userId, "Restriction User", email,
                passwordEncoder.encode(PASSWORD), AccountStatus.ACTIVE.name());
        jdbcTemplate.update("INSERT INTO dbo.user_role (id, user_id, role_id) VALUES (?, ?, ?)",
                userId, userId, role.ordinal() + 1);
        return userId;
    }

    private String login(long userId) throws Exception {
        String email = "restriction-" + userId + "@example.test";
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode login = objectMapper.readTree(response);
        return login.path("data").path("accessToken").stringValue();
    }

    private String expiredToken() throws Exception {
        java.time.Instant now = java.time.Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("9001")
                .issuedAt(now.minusSeconds(7200))
                .expiresAt(now.minusSeconds(3600))
                .claim("roles", List.of(RoleCode.TRAINER.name()))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
