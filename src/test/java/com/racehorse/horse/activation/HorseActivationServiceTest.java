package com.racehorse.horse.activation;

import com.racehorse.auth.RoleCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorseActivationServiceTest extends HorseActivationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @ParameterizedTest
    @EnumSource(MissingCondition.class)
    void everyMissingMandatoryConditionLeavesHorseDraft(MissingCondition missing) {
        long horseId = createHorse("Activation candidate");
        trackHorse(horseId);
        seedAllExcept(horseId, missing);

        assertFalse(activationService.reevaluate(horseId));
        assertEquals("DRAFT", lifecycle(horseId));
        assertEquals(0L, count("SELECT COUNT(*) FROM dbo.horse WHERE id = ? AND activated_at IS NOT NULL", horseId));
    }

    @Test
    void addingTheLastBaselineConditionActivatesOnceAndPersistsTimestamp() {
        long horseId = createHorse("Ready candidate");
        setValidNamedPedigree(horseId);
        long managerId = createAccount(RoleCode.CLUB_MANAGER);
        long ownerId = createAccount(RoleCode.HORSE_OWNER);
        long veterinarianId = createAccount(RoleCode.VETERINARIAN);
        addOwnership(horseId, ownerId);
        addStableAssignment(horseId, managerId);

        assertFalse(activationService.reevaluate(horseId));
        assertEquals("DRAFT", lifecycle(horseId));

        addBaseline(horseId, veterinarianId);
        assertTrue(activationService.reevaluate(horseId));
        assertEquals("ACTIVE", lifecycle(horseId));
        LocalDateTime activatedAt = jdbcTemplate.queryForObject(
                "SELECT activated_at FROM dbo.horse WHERE id = ?", LocalDateTime.class, horseId);
        assertNotNull(activatedAt);

        assertFalse(activationService.reevaluate(horseId));
        assertEquals(activatedAt, jdbcTemplate.queryForObject(
                "SELECT activated_at FROM dbo.horse WHERE id = ?", LocalDateTime.class, horseId));
        assertNotNull(activatedAt.toInstant(ZoneOffset.UTC));
    }

    @Test
    void failedActivationReevaluationRollsBackTheCallerWrite() {
        long horseId = createHorse("Rollback candidate");
        setValidNamedPedigree(horseId);
        long managerId = createAccount(RoleCode.CLUB_MANAGER);
        long ownerId = createAccount(RoleCode.HORSE_OWNER);
        long veterinarianId = createAccount(RoleCode.VETERINARIAN);
        addStableAssignment(horseId, managerId);
        addBaseline(horseId, veterinarianId);

        jdbcTemplate.execute("""
                ALTER TABLE dbo.horse
                ADD CONSTRAINT ck_test_activation_no_active CHECK (lifecycle <> 'ACTIVE')
                """);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            assertThrows(RuntimeException.class, () -> transaction.executeWithoutResult(status -> {
                addOwnership(horseId, ownerId);
                activationService.reevaluate(horseId);
            }));

            assertEquals(0L, count("SELECT COUNT(*) FROM dbo.horse_ownership WHERE horse_id = ?", horseId));
            assertEquals("DRAFT", lifecycle(horseId));
            assertEquals(0L, count("SELECT COUNT(*) FROM dbo.horse WHERE id = ? AND activated_at IS NOT NULL", horseId));
        } finally {
            jdbcTemplate.execute("ALTER TABLE dbo.horse DROP CONSTRAINT ck_test_activation_no_active");
        }
    }

    private void seedAllExcept(long horseId, MissingCondition missing) {
        if (missing == MissingCondition.IDENTITY) {
            jdbcTemplate.update("UPDATE dbo.horse SET name = '  ' WHERE id = ?", horseId);
        }
        if (missing == MissingCondition.PEDIGREE) {
            jdbcTemplate.update("UPDATE dbo.horse SET sire_horse_id = ?, sire_name = NULL WHERE id = ?",
                    Long.MAX_VALUE, horseId);
        } else {
            setValidNamedPedigree(horseId);
        }
        long managerId = createAccount(RoleCode.CLUB_MANAGER);
        if (missing != MissingCondition.OWNER) {
            addOwnership(horseId, createAccount(RoleCode.HORSE_OWNER));
        }
        if (missing != MissingCondition.FREE_STABLE_BOX) {
            addStableAssignment(horseId, managerId);
        }
        if (missing != MissingCondition.VETERINARIAN_BASELINE) {
            addBaseline(horseId, createAccount(RoleCode.VETERINARIAN));
        }
    }

    private String lifecycle(long horseId) {
        return jdbcTemplate.queryForObject(
                "SELECT lifecycle FROM dbo.horse WHERE id = ?", String.class, horseId);
    }

    private enum MissingCondition {
        IDENTITY,
        PEDIGREE,
        OWNER,
        FREE_STABLE_BOX,
        VETERINARIAN_BASELINE
    }
}
