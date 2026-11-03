package com.racehorse.horse.activation;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "core016.sqlserver.it", matches = "true")
class HorseActivationSqlServerIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HorseActivationService activationService;

    private final List<Long> horseIds = new ArrayList<>();
    private final List<Long> boxIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> roleIds = new ArrayList<>();
    private boolean targetVerified;

    @DynamicPropertySource
    static void sqlServerProperties(DynamicPropertyRegistry registry) {
        Core016SqlServerTestGuard.Configuration configuration = Core016SqlServerTestGuard.configuration();
        registry.add("spring.datasource.url", configuration::url);
        registry.add("spring.datasource.username", configuration::username);
        registry.add("spring.datasource.password", configuration::password);
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.liquibase.enabled", () -> "false");
    }

    @BeforeEach
    void verifyApprovedTargetBeforeWrites() {
        Core016SqlServerTestGuard.verifyConnection(jdbc, Core016SqlServerTestGuard.configuration());
        Core016SqlServerTestGuard.verifySchemaAndEmptyHorseData(jdbc);
        targetVerified = true;
    }

    @AfterEach
    void cleanOnlyRowsCreatedByThisTest() {
        if (!targetVerified) {
            return;
        }
        Core016SqlServerTestGuard.verifyConnection(jdbc, Core016SqlServerTestGuard.configuration());
        for (long horseId : horseIds) {
            jdbc.update("DELETE FROM dbo.weight_record WHERE horse_id = ?", horseId);
            jdbc.update("DELETE FROM dbo.veterinary_examination WHERE horse_id = ?", horseId);
            jdbc.update("DELETE FROM dbo.horse_stable_assignment WHERE horse_id = ?", horseId);
            jdbc.update("DELETE FROM dbo.horse_ownership WHERE horse_id = ?", horseId);
            jdbc.update("DELETE FROM dbo.horse WHERE id = ?", horseId);
        }
        for (long boxId : boxIds) {
            jdbc.update("DELETE FROM dbo.stable_box WHERE id = ?", boxId);
        }
        for (long userId : userIds) {
            jdbc.update("DELETE FROM dbo.user_role WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM dbo.app_user WHERE id = ?", userId);
        }
        for (long roleId : roleIds) {
            jdbc.update("""
                    DELETE FROM dbo.role
                    WHERE id = ?
                      AND NOT EXISTS (SELECT 1 FROM dbo.user_role WHERE role_id = ?)
                    """, roleId, roleId);
        }
    }

    @Test
    void completeBaselineAutomaticallyActivatesDraftAndPersistsOnSqlServer() {
        long horseId = createHorse();
        long ownerId = createAccount(RoleCode.HORSE_OWNER);
        long managerId = createAccount(RoleCode.CLUB_MANAGER);
        long veterinarianId = createAccount(RoleCode.VETERINARIAN);
        jdbc.update("UPDATE dbo.horse SET sire_name = ?, dam_name = ? WHERE id = ?",
                "SQL test sire", "SQL test dam", horseId);
        jdbc.update("""
                INSERT INTO dbo.horse_ownership (horse_id, owner_user_id, share_percentage, effective_from)
                VALUES (?, ?, 100.00, ?)
                """, horseId, ownerId, LocalDate.now());
        long boxId = insertId("""
                INSERT INTO dbo.stable_box (box_code, yard)
                OUTPUT INSERTED.id
                VALUES (?, ?)
                """, "A16-" + suffix().substring(0, 14), "CORE-016 SQL Test");
        boxIds.add(boxId);
        jdbc.update("""
                INSERT INTO dbo.horse_stable_assignment
                    (horse_id, stable_box_id, assigned_by_user_id, effective_from)
                VALUES (?, ?, ?, ?)
                """, horseId, boxId, managerId, LocalDate.now());

        assertFalse(activationService.reevaluate(horseId));
        assertEquals("DRAFT", lifecycle(horseId));

        LocalDateTime baselineAt = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("UPDATE dbo.horse SET health_status = 'FIT' WHERE id = ?", horseId);
        jdbc.update("""
                INSERT INTO dbo.veterinary_examination
                    (horse_id, medical_case_id, performed_by_user_id, exam_type, examined_at, health_status_set)
                VALUES (?, NULL, ?, 'BASELINE', ?, 'FIT')
                """, horseId, veterinarianId, baselineAt);
        jdbc.update("""
                INSERT INTO dbo.weight_record
                    (horse_id, recorded_by_user_id, weight, measured_at, context)
                VALUES (?, ?, 500.00, ?, 'BASELINE')
                """, horseId, veterinarianId, baselineAt);

        LocalDateTime activationStartedAt = LocalDateTime.now(ZoneOffset.UTC);
        assertTrue(activationService.reevaluate(horseId));
        LocalDateTime activationFinishedAt = LocalDateTime.now(ZoneOffset.UTC);
        assertEquals("ACTIVE", lifecycle(horseId));
        LocalDateTime activatedAt = jdbc.queryForObject(
                "SELECT activated_at FROM dbo.horse WHERE id = ?", LocalDateTime.class, horseId);
        assertNotNull(activatedAt);
        assertFalse(activatedAt.isBefore(activationStartedAt));
        assertFalse(activatedAt.isAfter(activationFinishedAt));
        assertFalse(activationService.reevaluate(horseId));
        assertEquals(1L, count("""
                SELECT COUNT_BIG(*) FROM dbo.horse
                WHERE id = ? AND lifecycle = 'ACTIVE' AND activated_at IS NOT NULL
                """, horseId));
    }

    private long createHorse() {
        long horseId = insertId("""
                INSERT INTO dbo.horse (horse_code, name, lifecycle)
                OUTPUT INSERTED.id
                VALUES (?, ?, 'DRAFT')
                """, "A16-" + suffix().substring(0, 14), "CORE-016 SQL Activation Test");
        horseIds.add(horseId);
        return horseId;
    }

    private long createAccount(RoleCode role) {
        long roleId = roleId(role);
        String suffix = suffix();
        long userId = insertId("""
                INSERT INTO dbo.app_user (user_code, full_name, email, account_status)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, 'ACTIVE')
                """, "A16-" + suffix.substring(0, 16),
                "CORE-016 SQL Test " + role.name(), "a16-" + suffix + "@example.test");
        userIds.add(userId);
        jdbc.update("""
                INSERT INTO dbo.user_role (user_id, role_id, assigned_at)
                VALUES (?, ?, SYSUTCDATETIME())
                """, userId, roleId);
        return userId;
    }

    private long roleId(RoleCode role) {
        Long existing = jdbc.query("""
                SELECT id FROM dbo.role WHERE role_code = ?
                """, resultSet -> resultSet.next() ? resultSet.getLong(1) : null, role.name());
        if (existing != null) {
            return existing;
        }
        long created = insertId("""
                INSERT INTO dbo.role (role_code, display_name)
                OUTPUT INSERTED.id
                VALUES (?, ?)
                """, role.name(), role.name());
        roleIds.add(created);
        return created;
    }

    private long insertId(String sql, Object... values) {
        Long id = jdbc.queryForObject(sql, Long.class, values);
        if (id == null) {
            throw new IllegalStateException("SQL Server did not return a generated identity.");
        }
        return id;
    }

    private String lifecycle(long horseId) {
        return jdbc.queryForObject("SELECT lifecycle FROM dbo.horse WHERE id = ?", String.class, horseId);
    }

    private long count(String sql, long horseId) {
        Long count = jdbc.queryForObject(sql, Long.class, horseId);
        return count == null ? 0 : count;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
