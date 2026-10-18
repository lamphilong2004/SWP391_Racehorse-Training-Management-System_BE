package com.racehorse.horse.stable;

import com.racehorse.TestJwtProperties;
import com.racehorse.auth.AccountStatus;
import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

abstract class StableBoxSqlServerTestSupport {

    private static final String[][] REQUIRED_COLUMNS = {
            {"role", "id"}, {"role", "role_code"}, {"role", "display_name"},
            {"app_user", "id"}, {"app_user", "user_code"}, {"app_user", "full_name"},
            {"app_user", "email"}, {"app_user", "password_hash"}, {"app_user", "account_status"},
            {"user_role", "user_id"}, {"user_role", "role_id"}, {"user_role", "assigned_at"},
            {"horse", "id"}, {"horse", "horse_code"}, {"horse", "name"}, {"horse", "lifecycle"},
            {"stable_box", "id"}, {"stable_box", "box_code"}, {"stable_box", "yard"},
            {"horse_stable_assignment", "id"}, {"horse_stable_assignment", "horse_id"},
            {"horse_stable_assignment", "stable_box_id"},
            {"horse_stable_assignment", "assigned_by_user_id"},
            {"horse_stable_assignment", "effective_from"},
            {"horse_stable_assignment", "effective_to"}
    };

    private final List<Long> createdAssignments = new ArrayList<>();
    private final List<Long> createdHorses = new ArrayList<>();
    private final List<Long> createdBoxes = new ArrayList<>();
    private final List<Long> createdUsers = new ArrayList<>();
    private final List<Long> createdRoles = new ArrayList<>();
    private boolean targetVerified;

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JsonMapper objectMapper;

    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void sqlServerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironment("CORE012_SQLSERVER_URL"));
        registry.add("spring.datasource.username", () -> environmentOrEmpty("CORE012_SQLSERVER_USERNAME"));
        registry.add("spring.datasource.password", () -> environmentOrEmpty("CORE012_SQLSERVER_PASSWORD"));
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.liquibase.enabled", () -> "false");
        TestJwtProperties.register(registry);
    }

    @BeforeEach
    void verifyTargetAndCanonicalSchemaBeforeWrites() {
        createdAssignments.clear();
        createdHorses.clear();
        createdBoxes.clear();
        createdUsers.clear();
        createdRoles.clear();
        targetVerified = false;

        verifyServerAndDatabaseIdentity();
        verifyCanonicalSchema();
        long existingBoxes = jdbcTemplate.queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo.stable_box", Long.class);
        assertEquals(0L, existingBoxes,
                "The dedicated CORE-012 test database must start without stable-box data.");

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        targetVerified = true;
    }

    @AfterEach
    void removeOnlyThisTestRunData() {
        if (!targetVerified) {
            return;
        }

        verifyServerAndDatabaseIdentity();
        for (long id : createdAssignments) {
            jdbcTemplate.update("DELETE FROM dbo.horse_stable_assignment WHERE id = ?", id);
        }
        for (long id : createdHorses) {
            jdbcTemplate.update("DELETE FROM dbo.horse WHERE id = ?", id);
        }
        for (long id : createdBoxes) {
            jdbcTemplate.update("DELETE FROM dbo.stable_box WHERE id = ?", id);
        }
        for (long id : createdUsers) {
            jdbcTemplate.update("DELETE FROM dbo.user_role WHERE user_id = ?", id);
            jdbcTemplate.update("DELETE FROM dbo.app_user WHERE id = ?", id);
        }
        for (long id : createdRoles) {
            jdbcTemplate.update("""
                    DELETE FROM dbo.role
                    WHERE id = ?
                      AND NOT EXISTS (SELECT 1 FROM dbo.user_role WHERE role_id = ?)
                    """, id, id);
        }
    }

    protected TestAccount createAccount(RoleCode role) throws Exception {
        long roleId = findOrCreateRole(role);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "core012-" + suffix + "@example.test";
        String password = UUID.randomUUID().toString();
        String userCode = "C12-" + suffix.substring(0, 16);
        long userId = insertId("""
                INSERT INTO dbo.app_user
                    (user_code, full_name, email, password_hash, account_status)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?)
                """,
                userCode, "CORE-012 SQL Test", email, passwordEncoder.encode(password),
                AccountStatus.ACTIVE.name());
        createdUsers.add(userId);
        jdbcTemplate.update("""
                INSERT INTO dbo.user_role (user_id, role_id, assigned_at)
                VALUES (?, ?, SYSUTCDATETIME())
                """, userId, roleId);

        String loginRequest = objectMapper.writeValueAsString(
                Map.of("email", email, "password", password));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode login = objectMapper.readTree(response);
        assertTrue(login.path("success").booleanValue(), "Test account login should succeed.");
        String token = login.path("data").path("accessToken").stringValue();
        assertFalse(token.isBlank(), "Successful login should return an access token.");
        return new TestAccount(userId, token);
    }

    protected long insertHorse(String suffix) {
        long id = insertId("""
                INSERT INTO dbo.horse (horse_code, name, lifecycle)
                OUTPUT INSERTED.id
                VALUES (?, ?, 'DRAFT')
                """, "C12-H-" + suffix, "CORE-012 SQL Test Horse");
        createdHorses.add(id);
        return id;
    }

    protected long insertStableBox(String prefix, String suffix) {
        long id = insertId("""
                INSERT INTO dbo.stable_box (box_code, yard)
                OUTPUT INSERTED.id
                VALUES (?, ?)
                """, "C12-" + prefix + "-" + suffix, "CORE-012 Test Yard");
        createdBoxes.add(id);
        return id;
    }

    protected void insertAssignment(long horseId, long boxId, long assignedByUserId, boolean open) {
        long id;
        if (open) {
            id = insertId("""
                    INSERT INTO dbo.horse_stable_assignment
                        (horse_id, stable_box_id, assigned_by_user_id, effective_from, effective_to)
                    OUTPUT INSERTED.id
                    VALUES (?, ?, ?, CONVERT(date, SYSUTCDATETIME()), NULL)
                    """, horseId, boxId, assignedByUserId);
        } else {
            id = insertId("""
                    INSERT INTO dbo.horse_stable_assignment
                        (horse_id, stable_box_id, assigned_by_user_id, effective_from, effective_to)
                    OUTPUT INSERTED.id
                    VALUES (?, ?, ?, DATEADD(day, -2, CONVERT(date, SYSUTCDATETIME())),
                            DATEADD(day, -1, CONVERT(date, SYSUTCDATETIME())))
                    """, horseId, boxId, assignedByUserId);
        }
        createdAssignments.add(id);
    }

    private long findOrCreateRole(RoleCode role) {
        List<Long> existingIds = jdbcTemplate.query(
                "SELECT id FROM dbo.role WHERE role_code = ?",
                (resultSet, rowNumber) -> resultSet.getLong(1),
                role.name());
        if (!existingIds.isEmpty()) {
            return existingIds.get(0);
        }
        long id = insertId("""
                INSERT INTO dbo.role (role_code, display_name)
                OUTPUT INSERTED.id
                VALUES (?, ?)
                """, role.name(), role.name());
        createdRoles.add(id);
        return id;
    }

    private long insertId(String sql, Object... values) {
        Long id = jdbcTemplate.queryForObject(sql, Long.class, values);
        assertNotNull(id, "SQL Server insert should return its generated identifier.");
        return id;
    }

    private void verifyServerAndDatabaseIdentity() {
        String expectedServer = requiredEnvironment("CORE012_SQLSERVER_EXPECTED_SERVER");
        String expectedDatabase = requiredEnvironment("CORE012_SQLSERVER_DATABASE");
        String url = requiredEnvironment("CORE012_SQLSERVER_URL");
        if (!expectedDatabase.matches("racehorse_core012_test_[0-9]{8}_[0-9]{4}")
                || expectedDatabase.equalsIgnoreCase("racehorse_dev")
                || expectedDatabase.toLowerCase().startsWith("racehorse_core007_test_")) {
            throw new IllegalStateException(
                    "CORE012_SQLSERVER_DATABASE must identify a dedicated CORE-012 test database.");
        }
        if (!Pattern.compile("(?i)(?:^|;)databaseName=" + Pattern.quote(expectedDatabase) + "(?:;|$)")
                .matcher(url).find()
                || Pattern.compile("(?i)(?:^|;)(?:failoverPartner|user|username|password)\\s*=")
                        .matcher(url).find()) {
            throw new IllegalStateException(
                    "CORE012_SQLSERVER_URL must target the approved database without failover or embedded credentials.");
        }

        String actualServer = jdbcTemplate.queryForObject(
                "SELECT CONVERT(nvarchar(128), SERVERPROPERTY('ServerName'))", String.class);
        String actualDatabase = jdbcTemplate.queryForObject("SELECT DB_NAME()", String.class);
        if (!expectedServer.equalsIgnoreCase(actualServer)
                || !expectedDatabase.equalsIgnoreCase(actualDatabase)) {
            throw new IllegalStateException(
                    "Connected SQL Server identity does not match the approved CORE-012 test target.");
        }
    }

    private void verifyCanonicalSchema() {
        for (String[] column : REQUIRED_COLUMNS) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = ? AND COLUMN_NAME = ?
                    """, Integer.class, column[0], column[1]);
            assertEquals(1, count, "Missing canonical column dbo." + column[0] + "." + column[1]);
        }

        Integer openAssignmentIndexes = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys.indexes
                WHERE object_id = OBJECT_ID(N'dbo.horse_stable_assignment')
                  AND has_filter = 1
                  AND name IN (
                      N'ux_horse_stable_assignment__open_per_horse',
                      N'ux_horse_stable_assignment__open_per_box'
                  )
                """, Integer.class);
        assertEquals(2, openAssignmentIndexes, "Canonical open-assignment indexes are required.");

        Integer assignmentForeignKeys = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys.foreign_keys
                WHERE name IN (
                    N'fk_horse_stable_assignment__horse_id',
                    N'fk_horse_stable_assignment__stable_box_id',
                    N'fk_horse_stable_assignment__assigned_by_user_id'
                )
                """, Integer.class);
        assertEquals(3, assignmentForeignKeys, "Canonical assignment foreign keys are required.");
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for CORE-012 SQL Server tests.");
        }
        return value;
    }

    private static String environmentOrEmpty(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    protected record TestAccount(long userId, String token) {
    }
}
