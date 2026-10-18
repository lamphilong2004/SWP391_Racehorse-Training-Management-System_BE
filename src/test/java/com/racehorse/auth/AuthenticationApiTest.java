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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

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

    @MockitoSpyBean
    private AuthRepository authRepository;

    @MockitoBean
    private PendingRegistrationRepository pendingRegistrationRepository;

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
        when(pendingRegistrationRepository.findPendingByNormalizedEmail(anyString()))
                .thenReturn(Optional.empty());
        jdbcTemplate.update("DELETE FROM dbo.audit_log");
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
    void deactivatedAccountCannotUsePreviouslyIssuedToken() throws Exception {
        long userId = createUser(AccountStatus.ACTIVE, RoleCode.TRAINER, passwordEncoder.encode(PASSWORD));
        JsonNode login = objectMapper.readTree(login(emailFor(userId), PASSWORD, "192.0.2.31")
                .andReturn().getResponse().getContentAsString());
        String token = login.path("data").path("accessToken").stringValue();
        jdbcTemplate.update("UPDATE dbo.app_user SET account_status = 'INACTIVE' WHERE id = ?", userId);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Your account is deactivated."));
        mockMvc.perform(get("/api/test-probe/actor").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Your account is deactivated."));
    }

    @Test
    void unknownEmailAndWrongPasswordHaveIdenticalUnauthorizedResponses() throws Exception {
        long userId = createUser(AccountStatus.ACTIVE, RoleCode.HEAD_TRAINER, passwordEncoder.encode(PASSWORD));
        String wrongPasswordBody = login(emailFor(userId), "wrong-password", "192.0.2.2")
                .andReturn().getResponse().getContentAsString();
        verify(pendingRegistrationRepository, never()).findPendingByNormalizedEmail(anyString());

        String unknownEmail = "missing+" + UUID.randomUUID() + "@example.test";
        String unknownEmailBody = login(unknownEmail, PASSWORD, "192.0.2.2")
                .andReturn().getResponse().getContentAsString();

        assertEquals(wrongPasswordBody, unknownEmailBody);
        org.junit.jupiter.api.Assertions.assertTrue(wrongPasswordBody.contains("Invalid email or password."));
        assertFalse(objectMapper.readTree(wrongPasswordBody).path("data").has("accessToken"));
        assertFalse(objectMapper.readTree(unknownEmailBody).path("data").has("accessToken"));
        verify(pendingRegistrationRepository).findPendingByNormalizedEmail(unknownEmail);
    }

    @Test
    void pendingRegistrationWithMatchingPasswordGetsForbiddenWithoutJwt() throws Exception {
        String email = "Pending+" + UUID.randomUUID() + "@EXAMPLE.TEST";
        String normalizedEmail = EmailAddressNormalizer.normalize(email);
        when(pendingRegistrationRepository.findPendingByNormalizedEmail(normalizedEmail))
                .thenReturn(Optional.of(new PendingRegistrationRepository.PendingCredentials(
                        passwordEncoder.encode(PASSWORD))));

        login(email, PASSWORD, "192.0.2.40")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message")
                        .value("Your account is waiting for Club Manager approval."))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());

        var lookupOrder = inOrder(authRepository, pendingRegistrationRepository);
        lookupOrder.verify(authRepository).findByEmail(normalizedEmail);
        lookupOrder.verify(pendingRegistrationRepository).findPendingByNormalizedEmail(normalizedEmail);
    }

    @Test
    void pendingRegistrationWithWrongPasswordGetsNeutralUnauthorizedWithoutJwt() throws Exception {
        String email = "pending+" + UUID.randomUUID() + "@example.test";
        when(pendingRegistrationRepository.findPendingByNormalizedEmail(email))
                .thenReturn(Optional.of(new PendingRegistrationRepository.PendingCredentials(
                        passwordEncoder.encode("Different-Password-456"))));

        login(email, PASSWORD, "192.0.2.41")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Invalid email or password."))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void rejectedRegistrationIsIndistinguishableFromUnknownEmailAndGetsNoJwt() throws Exception {
        String email = "rejected+" + UUID.randomUUID() + "@example.test";
        when(pendingRegistrationRepository.findPendingByNormalizedEmail(email))
                .thenReturn(Optional.empty());

        String rejectedResponse = login(email, PASSWORD, "192.0.2.42")
                .andReturn().getResponse().getContentAsString();
        String unknownResponse = login(
                "unknown+" + UUID.randomUUID() + "@example.test", PASSWORD, "192.0.2.42")
                .andReturn().getResponse().getContentAsString();

        assertEquals(rejectedResponse, unknownResponse);
        assertEquals("UNAUTHORIZED", objectMapper.readTree(rejectedResponse).path("error").path("code").stringValue());
        assertEquals("Invalid email or password.",
                objectMapper.readTree(rejectedResponse).path("error").path("message").stringValue());
        assertFalse(objectMapper.readTree(rejectedResponse).path("data").has("accessToken"));
        assertFalse(objectMapper.readTree(unknownResponse).path("data").has("accessToken"));
    }

    @Test
    void existingAppUserTakesPrecedenceOverPendingRegistrationWithSameEmail() throws Exception {
        long userId = createUser(AccountStatus.ACTIVE, RoleCode.TRAINER, passwordEncoder.encode(PASSWORD));
        String email = emailFor(userId);
        when(pendingRegistrationRepository.findPendingByNormalizedEmail(email))
                .thenReturn(Optional.of(new PendingRegistrationRepository.PendingCredentials(
                        passwordEncoder.encode(PASSWORD))));

        login(email.toUpperCase(java.util.Locale.ROOT), PASSWORD, "192.0.2.43")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(userId))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        verify(authRepository).findByEmail(email);
        verify(pendingRegistrationRepository, never()).findPendingByNormalizedEmail(anyString());
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
        when(pendingRegistrationRepository.findPendingByNormalizedEmail(emailFor(userId)))
                .thenReturn(Optional.of(new PendingRegistrationRepository.PendingCredentials(
                        passwordEncoder.encode(PASSWORD))));

        login(emailFor(userId), PASSWORD, "192.0.2.4")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Your account is deactivated."));
        verify(pendingRegistrationRepository, never()).findPendingByNormalizedEmail(anyString());
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
    void revokingRoleDeniesSameValidJwtAndCurrentRolesDriveMeAndActor() throws Exception {
        long userId = createUser(
                AccountStatus.ACTIVE,
                passwordEncoder.encode(PASSWORD),
                RoleCode.CLUB_MANAGER,
                RoleCode.TRAINER);
        String token = accessTokenFor(userId);
        jdbcTemplate.update("""
                DELETE FROM dbo.user_role
                WHERE user_id = ? AND role_id = ?
                """, userId, roleId(RoleCode.CLUB_MANAGER));

        mockMvc.perform(get("/api/admin/test-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(1))
                .andExpect(jsonPath("$.data.roles[0]").value("TRAINER"));
        mockMvc.perform(get("/api/test-probe/actor").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.roles.length()").value(1))
                .andExpect(jsonPath("$.data.roles[0]").value("TRAINER"));
    }

    @Test
    void revokingOneRoleKeepsAnotherAssignedRoleEffective() throws Exception {
        long userId = createUser(
                AccountStatus.ACTIVE,
                passwordEncoder.encode(PASSWORD),
                RoleCode.CLUB_MANAGER,
                RoleCode.TRAINER);
        String token = accessTokenFor(userId);
        jdbcTemplate.update("""
                DELETE FROM dbo.user_role
                WHERE user_id = ? AND role_id = ?
                """, userId, roleId(RoleCode.TRAINER));

        mockMvc.perform(get("/api/admin/test-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(1))
                .andExpect(jsonPath("$.data.roles[0]").value("CLUB_MANAGER"));
        mockMvc.perform(get("/api/test-probe/actor").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("CLUB_MANAGER"));
    }

    @Test
    void logoutPersistsApprovedActionAndCurrentActorWithoutSecrets() throws Exception {
        long userId = createUser(
                AccountStatus.ACTIVE,
                RoleCode.VETERINARIAN,
                passwordEncoder.encode(PASSWORD));
        String token = accessTokenFor(userId);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                SELECT actor_user_id, actor_role, action, target_type, target_id, safe_summary, source
                FROM dbo.audit_log
                """);
        assertEquals(userId, ((Number) audit.get("ACTOR_USER_ID")).longValue());
        assertEquals("VETERINARIAN", audit.get("ACTOR_ROLE"));
        assertEquals("USER_LOGGED_OUT", audit.get("ACTION"));
        assertNull(audit.get("TARGET_TYPE"));
        assertNull(audit.get("TARGET_ID"));
        assertEquals("User logged out.", audit.get("SAFE_SUMMARY"));
        assertEquals("WEB", audit.get("SOURCE"));
        for (Object value : audit.values()) {
            if (value != null) {
                assertFalse(value.toString().contains(token));
                assertFalse(value.toString().contains(PASSWORD));
            }
        }
    }

    @Test
    void logoutReturnsInternalErrorWhenAuditPersistenceFails() throws Exception {
        long userId = createUser(
                AccountStatus.ACTIVE,
                RoleCode.TRAINER,
                passwordEncoder.encode(PASSWORD));
        String token = accessTokenFor(userId);
        jdbcTemplate.execute("ALTER TABLE dbo.audit_log RENAME TO audit_log_unavailable");

        try {
            mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.success").value(false));
        } finally {
            jdbcTemplate.execute("ALTER TABLE dbo.audit_log_unavailable RENAME TO audit_log");
        }
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

    @Test
    void emailNormalizationUsesTrimNfcAndLocaleIndependentLowercase() {
        assertEquals("\u00e9xample@example.test",
                EmailAddressNormalizer.normalize("  E\u0301xample@EXAMPLE.TEST  "));
        assertEquals("i@example.test", EmailAddressNormalizer.normalize("I@EXAMPLE.TEST"));
    }

    private ResultActions login(String email, String password, String remoteAddress) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddress(remoteAddress))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String accessTokenFor(long userId) throws Exception {
        JsonNode login = objectMapper.readTree(login(emailFor(userId), PASSWORD, "192.0.2.32")
                .andReturn().getResponse().getContentAsString());
        return login.path("data").path("accessToken").stringValue();
    }

    private long createUser(AccountStatus status, RoleCode role, String passwordHash) {
        return createUser(status, passwordHash, role);
    }

    private long createUser(AccountStatus status, String passwordHash, RoleCode... roles) {
        long id = nextId.getAndIncrement();
        jdbcTemplate.update("""
                INSERT INTO dbo.app_user (id, user_code, full_name, email, password_hash, account_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, "U-" + id, "Test User " + id, emailFor(id), passwordHash, status.name());
        for (int i = 0; i < roles.length; i++) {
            int roleId = roleId(roles[i]);
            Integer existingRole = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM dbo.role WHERE id = ?", Integer.class, roleId);
            if (existingRole == null || existingRole == 0) {
                jdbcTemplate.update("INSERT INTO dbo.role (id, role_code) VALUES (?, ?)", roleId, roles[i].name());
            }
            jdbcTemplate.update("INSERT INTO dbo.user_role (id, user_id, role_id) VALUES (?, ?, ?)",
                    id + 10_000 + i, id, roleId);
        }
        return id;
    }

    private int roleId(RoleCode role) {
        return role.ordinal() + 1;
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
