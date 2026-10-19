package com.racehorse.horse.activation;

import com.racehorse.TestJwtProperties;
import com.racehorse.auth.RoleCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@ActiveProfiles("test")
@SpringBootTest
abstract class HorseActivationTestSupport {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected HorseActivationService activationService;

    @Autowired
    private WebApplicationContext context;

    protected MockMvc mvc;

    private final List<Long> horseIds = new ArrayList<>();
    private final List<Long> stableBoxIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> userRoleIds = new ArrayList<>();

    @DynamicPropertySource
    static void testJwtProperties(DynamicPropertyRegistry registry) {
        TestJwtProperties.register(registry);
    }

    @BeforeEach
    void initializeMockMvc() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @AfterEach
    void cleanActivationTestData() {
        for (long horseId : horseIds) {
            jdbc.update("DELETE FROM dbo.audit_log WHERE target_type = 'HORSE' AND target_id = ?", horseId);
            jdbc.update("DELETE FROM dbo.weight_record WHERE horse_id = ?", horseId);
            jdbc.update("DELETE FROM dbo.veterinary_examination WHERE horse_id = ?", horseId);
            jdbc.update("DELETE FROM dbo.horse_stable_assignment WHERE horse_id = ?", horseId);
            jdbc.update("DELETE FROM dbo.horse_ownership WHERE horse_id = ?", horseId);
            jdbc.update("DELETE FROM dbo.horse WHERE id = ?", horseId);
        }
        for (long stableBoxId : stableBoxIds) {
            jdbc.update("DELETE FROM dbo.stable_box WHERE id = ?", stableBoxId);
        }
        for (long userRoleId : userRoleIds) {
            jdbc.update("DELETE FROM dbo.user_role WHERE id = ?", userRoleId);
        }
        for (long userId : userIds) {
            jdbc.update("DELETE FROM dbo.audit_log WHERE actor_user_id = ?", userId);
            jdbc.update("DELETE FROM dbo.app_user WHERE id = ?", userId);
        }
        horseIds.clear();
        stableBoxIds.clear();
        userIds.clear();
        userRoleIds.clear();
    }

    protected long createHorse(String name) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO dbo.horse (horse_code, name, lifecycle) VALUES (?, ?, 'DRAFT')",
                    new String[]{"id"});
            statement.setString(1, "A16-" + suffix);
            statement.setString(2, name);
            return statement;
        }, keyHolder);
        long horseId = generatedId(keyHolder);
        horseIds.add(horseId);
        return horseId;
    }

    protected long createAccount(RoleCode role) {
        ensureRole(role);
        long userId = uniqueId("dbo.app_user");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO dbo.app_user
                    (id, user_code, full_name, email, password_hash, account_status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """, userId, "A16-" + suffix.substring(0, 12),
                "CORE-016 Test " + role.name(), "a16-" + suffix + "@example.test", "unused");
        userIds.add(userId);
        long userRoleId = uniqueId("dbo.user_role");
        jdbc.update("INSERT INTO dbo.user_role (id, user_id, role_id) VALUES (?, ?, ?)",
                userRoleId, userId, roleId(role));
        userRoleIds.add(userRoleId);
        return userId;
    }

    protected RequestPostProcessor authenticatedAs(RoleCode role, long userId) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.subject(Long.toString(userId))
                        .claim("roles", List.of(role.name())));
    }

    protected void setValidNamedPedigree(long horseId) {
        jdbc.update("""
                UPDATE dbo.horse
                SET sire_name = 'Test sire', dam_name = 'Test dam'
                WHERE id = ?
                """, horseId);
    }

    protected void addOwnership(long horseId, long ownerId) {
        jdbc.update("""
                INSERT INTO dbo.horse_ownership
                    (horse_id, owner_user_id, share_percentage, effective_from)
                VALUES (?, ?, 100.00, ?)
                """, horseId, ownerId, LocalDate.now());
    }

    protected void addStableAssignment(long horseId, long assignedByUserId) {
        long stableBoxId = createStableBox();
        jdbc.update("""
                INSERT INTO dbo.horse_stable_assignment
                    (horse_id, stable_box_id, assigned_by_user_id, effective_from)
                VALUES (?, ?, ?, ?)
                """, horseId, stableBoxId, assignedByUserId, LocalDate.now());
    }

    protected long createStableBox() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        KeyHolder boxKey = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO dbo.stable_box (box_code, yard) VALUES (?, ?)",
                    new String[]{"id"});
            statement.setString(1, "A16-" + suffix);
            statement.setString(2, "CORE-016 Test Yard");
            return statement;
        }, boxKey);
        long stableBoxId = generatedId(boxKey);
        stableBoxIds.add(stableBoxId);
        return stableBoxId;
    }

    protected void addBaseline(long horseId, long veterinarianId) {
        jdbc.update("UPDATE dbo.horse SET health_status = 'FIT' WHERE id = ?", horseId);
        LocalDateTime recordedAt = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO dbo.veterinary_examination
                    (horse_id, medical_case_id, performed_by_user_id, exam_type, examined_at, health_status_set)
                VALUES (?, NULL, ?, 'BASELINE', ?, 'FIT')
                """, horseId, veterinarianId, recordedAt);
        jdbc.update("""
                INSERT INTO dbo.weight_record
                    (horse_id, recorded_by_user_id, weight, measured_at, context)
                VALUES (?, ?, 500.00, ?, 'BASELINE')
                """, horseId, veterinarianId, recordedAt);
    }

    protected long count(String sql, long id) {
        Long count = jdbc.queryForObject(sql, Long.class, id);
        return count == null ? 0 : count;
    }

    protected void trackHorse(long horseId) {
        if (!horseIds.contains(horseId)) {
            horseIds.add(horseId);
        }
    }

    private void ensureRole(RoleCode role) {
        Long existing = jdbc.query("""
                        SELECT id FROM dbo.role WHERE role_code = ?
                        """, resultSet -> resultSet.next() ? resultSet.getLong("id") : null,
                role.name());
        if (existing == null) {
            jdbc.update("INSERT INTO dbo.role (id, role_code) VALUES (?, ?)",
                    uniqueId("dbo.role"), role.name());
        }
    }

    private long roleId(RoleCode role) {
        Long id = jdbc.queryForObject("SELECT id FROM dbo.role WHERE role_code = ?", Long.class, role.name());
        if (id == null) {
            throw new IllegalStateException("Test role was not created: " + role);
        }
        return id;
    }

    private long uniqueId(String table) {
        for (int attempt = 0; attempt < 10; attempt++) {
            long id = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Long.class, id);
            if (count != null && count == 0) {
                return id;
            }
        }
        throw new IllegalStateException("Could not allocate a unique test identity for " + table);
    }

    private static long generatedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The test database did not return a generated identity.");
        }
        return key.longValue();
    }
}
