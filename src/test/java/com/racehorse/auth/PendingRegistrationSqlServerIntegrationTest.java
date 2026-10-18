package com.racehorse.auth;

import com.racehorse.TestJwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "core007.sqlserver.it", matches = "true")
class PendingRegistrationSqlServerIntegrationTest {

    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String INVALID_CREDENTIALS = "Invalid email or password.";
    private static final String PENDING_MESSAGE =
            "Your account is waiting for Club Manager approval.";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JsonMapper objectMapper;

    @DynamicPropertySource
    static void sqlServerProperties(DynamicPropertyRegistry registry) {
        // Reject a wrong database name or a TLS-bypassing URL before the Spring context opens any connection.
        Core007TestDatabaseGuard.checkConfiguration(
                requiredEnvironment("CORE007_SQLSERVER_DATABASE"), requiredEnvironment("CORE007_SQLSERVER_URL"));
        registry.add("spring.datasource.url", () -> requiredEnvironment("CORE007_SQLSERVER_URL"));
        registry.add("spring.datasource.username", () -> environmentOrEmpty("CORE007_SQLSERVER_USERNAME"));
        registry.add("spring.datasource.password", () -> environmentOrEmpty("CORE007_SQLSERVER_PASSWORD"));
        registry.add("spring.sql.init.mode", () -> "never");
        TestJwtProperties.register(registry);
    }

    @BeforeEach
    void verifyDedicatedLocalTestDatabase() {
        // Fail closed before any write: dedicated disposable CORE-007 database, local instance, strict TLS.
        String expectedDatabase = requiredEnvironment("CORE007_SQLSERVER_DATABASE");
        String localMachine = System.getenv("COMPUTERNAME");
        Core007TestDatabaseGuard.checkConfiguration(expectedDatabase, requiredEnvironment("CORE007_SQLSERVER_URL"));
        Core007TestDatabaseGuard.checkDisposableInstall(
                java.nio.file.Path.of(requiredEnvironment("LOCALAPPDATA")), expectedDatabase, localMachine);
        Core007TestDatabaseGuard.checkConnection(jdbcTemplate, expectedDatabase, localMachine);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void correctPendingPasswordReturnsContractForbiddenWithoutToken() throws Exception {
        String email = email();
        insertPending(email, passwordEncoder.encode(PASSWORD));

        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value(PENDING_MESSAGE))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void wrongPendingPasswordReturnsNeutralUnauthorizedWithoutToken() throws Exception {
        String email = email();
        insertPending(email, passwordEncoder.encode(UUID.randomUUID().toString()));

        login(email, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value(INVALID_CREDENTIALS))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void rejectedAndUnknownEmailsHaveIdenticalUnauthorizedResponsesAndNoToken() throws Exception {
        String rejectedEmail = email();
        ActiveAccount reviewer = createActiveUser(email(), PASSWORD);
        insertReviewedRegistration(
                rejectedEmail, "REJECTED", null, reviewer.userId(), Instant.now(), null, null);

        String rejectedBody = login(rejectedEmail, PASSWORD).andReturn()
                .getResponse().getContentAsString();
        String unknownBody = login(email(), PASSWORD).andReturn()
                .getResponse().getContentAsString();

        assertEquals(unknownBody, rejectedBody);
        assertEquals(INVALID_CREDENTIALS,
                objectMapper.readTree(rejectedBody).path("error").path("message").stringValue());
        assertFalse(objectMapper.readTree(rejectedBody).path("data").has("accessToken"));
        assertFalse(objectMapper.readTree(unknownBody).path("data").has("accessToken"));
    }

    @Test
    void existingUserWithWrongPasswordDoesNotFallThroughToMatchingPendingRequest() throws Exception {
        String email = email();
        createActiveUser(email, UUID.randomUUID().toString());
        insertPending(email, passwordEncoder.encode(PASSWORD));

        login(email, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value(INVALID_CREDENTIALS))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void inactiveUserDoesNotFallThroughToMatchingPendingRequest() throws Exception {
        String email = email();
        createInactiveUser(email, PASSWORD);
        insertPending(email, passwordEncoder.encode(PASSWORD));

        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("Your account is deactivated."))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void activeUserWinsOverMatchingPendingRequestAndReceivesCurrentAccountToken() throws Exception {
        String email = email();
        ActiveAccount user = createActiveUser(email, PASSWORD);
        insertPending(email, passwordEncoder.encode(PASSWORD));

        var response = login(email.toUpperCase(java.util.Locale.ROOT), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(user.userId()))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String token = objectMapper.readTree(response.getResponse().getContentAsString())
                .path("data").path("accessToken").stringValue();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EmailAddressNormalizer.normalize(email)));
    }

    @Test
    void caseAndUnicodeNfcEquivalentInputFindsPendingRequest() throws Exception {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String storedSpelling = "r\u00e9gistration" + uuid + "@EXAMPLE.TEST";
        String inputSpelling = "re\u0301gistration" + uuid + "@example.test";
        insertPending(storedSpelling, passwordEncoder.encode(PASSWORD));

        login(inputSpelling, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value(PENDING_MESSAGE))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void filteredUniqueIndexRejectsDuplicatePendingButAllowsDecidedHistory() {
        String email = email();
        insertPending(email, passwordEncoder.encode(PASSWORD));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertPending(email.toUpperCase(java.util.Locale.ROOT), passwordEncoder.encode(PASSWORD)));

        ActiveAccount reviewer = createActiveUser(email(), PASSWORD);
        insertReviewedRegistration(email, "REJECTED", null, reviewer.userId(), Instant.now(), null, null);
    }

    @Test
    void checkConstraintsRejectInvalidStatusAndInvalidCredentialOrDecisionCombinations() {
        String invalidStatus = email();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertRaw(invalidStatus, "NOT_A_STATUS", passwordEncoder.encode(PASSWORD),
                        null, null, null, null));

        String pendingWithoutHash = email();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertRaw(pendingWithoutHash, "PENDING_APPROVAL", null,
                        null, null, null, null));

        ActiveAccount reviewer = createActiveUser(email(), PASSWORD);
        String pendingWithReviewer = email();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertRaw(pendingWithReviewer, "PENDING_APPROVAL", passwordEncoder.encode(PASSWORD),
                        reviewer.userId(), Instant.now(), null, null));

        String pendingReviewerWithoutTime = email();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertRaw(pendingReviewerWithoutTime, "PENDING_APPROVAL",
                        passwordEncoder.encode(PASSWORD), reviewer.userId(), null, null, null));

        String pendingTimeWithoutReviewer = email();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertRaw(pendingTimeWithoutReviewer, "PENDING_APPROVAL",
                        passwordEncoder.encode(PASSWORD), null, Instant.now(), null, null));

        String approvedWithHash = email();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertRaw(approvedWithHash, "APPROVED", passwordEncoder.encode(PASSWORD),
                        reviewer.userId(), Instant.now(), reviewer.roleId(), reviewer.userId()));

        String approvedWithoutRequiredFields = email();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertRaw(approvedWithoutRequiredFields, "APPROVED", null,
                        null, null, null, null));

        String approvedWithoutRole = email();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertRaw(approvedWithoutRole, "APPROVED", null,
                        reviewer.userId(), Instant.now(), null, reviewer.userId()));

        String approvedWithoutCreatedUser = email();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertRaw(approvedWithoutCreatedUser, "APPROVED", null,
                        reviewer.userId(), Instant.now(), reviewer.roleId(), null));

        String rejectedWithRole = email();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertRaw(rejectedWithRole, "REJECTED", null,
                        reviewer.userId(), Instant.now(), reviewer.roleId(), null));

        String rejectedWithoutReviewer = email();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertRaw(rejectedWithoutReviewer, "REJECTED", null,
                        null, Instant.now(), null, null));
    }

    @Test
    void approvedAndRejectedRowsCanBeStoredWithTheApprovedStateShape() {
        ActiveAccount reviewer = createActiveUser(email(), PASSWORD);
        String approvedEmail = email();
        String rejectedEmail = email();
        insertReviewedRegistration(approvedEmail, "APPROVED", null, reviewer.userId(),
                Instant.now(), reviewer.roleId(), reviewer.userId());
        insertReviewedRegistration(rejectedEmail, "REJECTED", null, reviewer.userId(),
                Instant.now(), null, null);

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM dbo.registration_request
                WHERE email_normalized IN (?, ?)
                  AND status IN ('APPROVED', 'REJECTED')
                """,
                Integer.class,
                EmailAddressNormalizer.normalize(approvedEmail),
                EmailAddressNormalizer.normalize(rejectedEmail));
        assertEquals(2, count);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String insertPending(String email, String passwordHash) {
        String normalizedEmail = EmailAddressNormalizer.normalize(email);
        jdbcTemplate.update("""
                INSERT INTO dbo.registration_request
                    (full_name, email, email_normalized, password_hash, status, requested_at)
                VALUES (?, ?, ?, ?, 'PENDING_APPROVAL', ?)
                """,
                "Pending Test User", email, normalizedEmail, passwordHash, Timestamp.from(Instant.now()));
        return normalizedEmail;
    }

    private void insertReviewedRegistration(
            String email,
            String status,
            String passwordHash,
            long reviewerId,
            Instant reviewedAt,
            Long approvedRoleId,
            Long createdUserId) {
        insertRaw(email, status, passwordHash, reviewerId, reviewedAt, approvedRoleId, createdUserId);
    }

    private void insertRaw(
            String email,
            String status,
            String passwordHash,
            Long reviewerId,
            Instant reviewedAt,
            Long approvedRoleId,
            Long createdUserId) {
        jdbcTemplate.update("""
                INSERT INTO dbo.registration_request
                    (full_name, email, email_normalized, password_hash, status, requested_at,
                     reviewed_by_user_id, reviewed_at, approved_role_id, created_user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "Test User", email, EmailAddressNormalizer.normalize(email), passwordHash, status,
                Timestamp.from(Instant.now()), reviewerId,
                reviewedAt == null ? null : Timestamp.from(reviewedAt),
                approvedRoleId, createdUserId);
    }

    private ActiveAccount createActiveUser(String email, String password) {
        return createUser(email, password, "ACTIVE");
    }

    private ActiveAccount createInactiveUser(String email, String password) {
        return createUser(email, password, "INACTIVE");
    }

    private ActiveAccount createUser(String email, String password, String status) {
        String userCode = "IT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long userId = jdbcTemplate.queryForObject("""
                INSERT INTO dbo.app_user (user_code, full_name, email, password_hash, account_status)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?)
                """, Long.class, userCode, "CORE-007 SQL Test User",
                EmailAddressNormalizer.normalize(email), passwordEncoder.encode(password), status);
        Long existingRoleId = jdbcTemplate.query("""
                SELECT id FROM dbo.role WHERE role_code = 'TRAINER'
                """, resultSet -> resultSet.next() ? resultSet.getLong(1) : null);
        long roleId = existingRoleId == null
                ? jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.role (role_code, display_name)
                        OUTPUT INSERTED.id
                        VALUES ('TRAINER', N'Trainer')
                        """, Long.class)
                : existingRoleId;
        jdbcTemplate.update("""
                INSERT INTO dbo.user_role (user_id, role_id, assigned_at)
                VALUES (?, ?, ?)
                """, userId, roleId, Timestamp.from(Instant.now()));
        return new ActiveAccount(userId, roleId);
    }

    private String email() {
        return "core007-" + UUID.randomUUID().toString().replace("-", "") + "@example.test";
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set to the isolated SQL Server test database.");
        }
        return value;
    }

    private static String environmentOrEmpty(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private record ActiveAccount(long userId, long roleId) {
    }
}
