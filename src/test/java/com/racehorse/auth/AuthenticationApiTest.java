package com.racehorse.auth;

import com.racehorse.TestJwtProperties;
import com.racehorse.common.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(AuthenticationApiTest.ProbeConfig.class)
class AuthenticationApiTest {

    private static final String PASSWORD = "Test-Password-123";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JsonMapper objectMapper;

    private final AtomicLong nextId = new AtomicLong(100);

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwtProperties.register(registry);
    }

    @BeforeEach
    void clearDatabase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        jdbcTemplate.update("DELETE FROM dbo.user_role");
        jdbcTemplate.update("DELETE FROM dbo.app_user");
        jdbcTemplate.update("DELETE FROM dbo.role");
        nextId.set(100);
    }

    @Test
    void activeUserCanLogInAndReadCurrentUserWithoutSensitiveFields() throws Exception {
        long userId = createUser(AccountStatus.ACTIVE, RoleCode.TRAINER, passwordEncoder.encode(PASSWORD));

        JsonNode login = objectMapper.readTree(login(emailFor(userId), PASSWORD, "192.0.2.1")
                .andReturn().getResponse().getContentAsString());
        assertTrue(login.path("success").asBoolean());
        assertEquals("Bearer", login.path("data").path("tokenType").stringValue());
        assertEquals(3600, login.path("data").path("expiresIn").asLong());
        assertEquals(userId, login.path("data").path("user").path("id").asLong());
        assertEquals(RoleCode.TRAINER.name(), login.path("data").path("user").path("roles").get(0).stringValue());
        assertFalse(login.path("data").path("user").has("password"));
        assertFalse(login.path("data").path("user").has("passwordHash"));
        assertFalse(login.path("data").path("user").has("accessToken"));

        String token = login.path("data").path("accessToken").stringValue();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.email").value(emailFor(userId)))
                .andExpect(jsonPath("$.data.roles[0]").value("TRAINER"));

        Object lastLogin = jdbcTemplate.queryForObject(
                "SELECT last_login_at FROM dbo.app_user WHERE id = ?", Object.class, userId);
        org.junit.jupiter.api.Assertions.assertNotNull(lastLogin);
    }

    @Test
    void unknownEmailAndWrongPasswordHaveIdenticalUnauthorizedResponses() throws Exception {
        long userId = createUser(AccountStatus.ACTIVE, RoleCode.HEAD_TRAINER, passwordEncoder.encode(PASSWORD));
        String wrongPasswordBody = login(emailFor(userId), "wrong-password", "192.0.2.2")
                .andReturn().getResponse().getContentAsString();
        String unknownEmailBody = login("missing+" + UUID.randomUUID() + "@example.test", PASSWORD, "192.0.2.2")
                .andReturn().getResponse().getContentAsString();

        assertEquals(wrongPasswordBody, unknownEmailBody);
        org.junit.jupiter.api.Assertions.assertTrue(wrongPasswordBody.contains("Invalid email or password."));
    }

    @Test
    void nullPasswordHashIsHandledAsGenericInvalidCredentials() throws Exception {
        long userId = createUser(AccountStatus.ACTIVE, RoleCode.GROOM, null);

        login(emailFor(userId), PASSWORD, "192.0.2.3")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Invalid email or password."));
    }

    @Test
    void inactiveAccountWithCorrectPasswordGetsApprovedForbiddenMessage() throws Exception {
        long userId = createUser(AccountStatus.INACTIVE, RoleCode.VETERINARIAN, passwordEncoder.encode(PASSWORD));

        login(emailFor(userId), PASSWORD, "192.0.2.4")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Your account is deactivated."));
    }

    @Test
    void missingMalformedExpiredAndIncorrectlySignedTokensAreRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenWithoutExpiry()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + wrongSignatureToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allSevenRolesAreIssuedAndRecognizedAndOnlyClubManagerCanAccessAdmin() throws Exception {
        for (RoleCode role : RoleCode.values()) {
            long userId = createUser(AccountStatus.ACTIVE, role, passwordEncoder.encode(PASSWORD));
            JsonNode login = objectMapper.readTree(login(emailFor(userId), PASSWORD, "192.0.2.5")
                    .andReturn().getResponse().getContentAsString());
            String token = login.path("data").path("accessToken").stringValue();

            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.roles[0]").value(role.name()));

            var adminRequest = get("/api/admin/test-probe").header("Authorization", "Bearer " + token);
            if (role == RoleCode.CLUB_MANAGER) {
                mockMvc.perform(adminRequest).andExpect(status().isOk());
            } else {
                mockMvc.perform(adminRequest)
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
            }
        }
    }

    @Test
    void rateLimitIsTenFailedAttemptsPerIpAndEmailInFifteenMinutes() throws Exception {
        long userId = createUser(AccountStatus.ACTIVE, RoleCode.TRAINER, passwordEncoder.encode(PASSWORD));
        String email = emailFor(userId);
        for (int attempt = 0; attempt < 10; attempt++) {
            login(email, "wrong-password", "192.0.2.10").andExpect(status().isUnauthorized());
        }
        login(email, "wrong-password", "192.0.2.10")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
        login(email, "wrong-password", "192.0.2.11").andExpect(status().isUnauthorized());
        login("different+" + UUID.randomUUID() + "@example.test", PASSWORD, "192.0.2.10")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actorContextUsesVerifiedTokenAndDoesNotRemainAfterRequest() throws Exception {
        long userId = createUser(AccountStatus.ACTIVE, RoleCode.WORK_RIDER, passwordEncoder.encode(PASSWORD));
        JsonNode login = objectMapper.readTree(login(emailFor(userId), PASSWORD, "192.0.2.20")
                .andReturn().getResponse().getContentAsString());
        String token = login.path("data").path("accessToken").stringValue();

        mockMvc.perform(get("/api/test-probe/actor").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.roles[0]").value("WORK_RIDER"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        mockMvc.perform(get("/api/test-probe/actor"))
                .andExpect(status().isUnauthorized());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void loginValidatesContractEmailAndPasswordLengths() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddress("192.0.2.30"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private ResultActions login(String email, String password, String remoteAddress) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddress(remoteAddress))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private long createUser(AccountStatus status, RoleCode role, String passwordHash) {
        long id = nextId.getAndIncrement();
        int roleId = role.ordinal() + 1;
        Integer existingRole = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.role WHERE id = ?", Integer.class, roleId);
        if (existingRole == null || existingRole == 0) {
            jdbcTemplate.update("INSERT INTO dbo.role (id, role_code) VALUES (?, ?)", roleId, role.name());
        }
        jdbcTemplate.update("""
                INSERT INTO dbo.app_user (id, user_code, full_name, email, password_hash, account_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, "U-" + id, "Test User " + id, emailFor(id), passwordHash, status.name());
        jdbcTemplate.update("INSERT INTO dbo.user_role (id, user_id, role_id) VALUES (?, ?, ?)",
                id + 10_000, id, roleId);
        return id;
    }

    private String emailFor(long id) {
        return "user" + id + "@example.test";
    }

    private RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private String expiredToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("9001")
                .issuedAt(now.minusSeconds(7200))
                .expiresAt(now.minusSeconds(3600))
                .claim("roles", List.of(RoleCode.TRAINER.name()))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String tokenWithoutExpiry() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("9003")
                .issuedAt(Instant.now())
                .claim("roles", List.of(RoleCode.TRAINER.name()))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String wrongSignatureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        var otherEncoder = NimbusJwtEncoder.withSecretKey(
                        new SecretKeySpec(bytes, "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256)
                .build();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("9002")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("roles", List.of(RoleCode.TRAINER.name()))
                .build();
        return otherEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @TestConfiguration
    static class ProbeConfig {

        @RestController
        static class ProbeController {

            @GetMapping("/api/admin/test-probe")
            String admin() {
                return "allowed";
            }

            @GetMapping("/api/test-probe/actor")
            ApiResponse<ActorContext> actor(CurrentActorProvider currentActorProvider) {
                return ApiResponse.ok(currentActorProvider.currentActor().orElseThrow());
            }
        }
    }
}
