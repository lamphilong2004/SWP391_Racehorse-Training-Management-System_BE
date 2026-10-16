package com.racehorse.medical;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests real JPA repositories and transaction rollback using an isolated H2 fixture.
 * H2's FOR UPDATE replaces SQL Server's table hints only in this test.
 * Does not claim to validate SQL Server's canonical schema or locking behavior.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:medical_persistence;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
@ActiveProfiles("test")
@Import(MedicalPersistenceTest.FixtureConfiguration.class)
class MedicalPersistenceTest {
    @Autowired MedicalService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MedicalHorseRepository horses;

    @BeforeEach void fixture() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS dbo.horse (
                    id BIGINT PRIMARY KEY, lifecycle VARCHAR(20) NOT NULL,
                    health_status VARCHAR(20), updated_at DATETIME2(3))
                """);
        jdbc.update("DELETE FROM dbo.weight_record");
        jdbc.update("DELETE FROM dbo.veterinary_examination");
        jdbc.update("DELETE FROM dbo.horse");
        jdbc.update("INSERT INTO dbo.horse(id, lifecycle) VALUES (7, 'DRAFT')");
        // Deliberate test-only constraint used to force a second-insert failure.
        jdbc.execute("ALTER TABLE dbo.weight_record DROP CONSTRAINT IF EXISTS fixture_weight_limit");
        jdbc.execute("ALTER TABLE dbo.weight_record ADD CONSTRAINT fixture_weight_limit CHECK (weight < 500)");
    }

    @Test void baselinePersistsAndCanBeReadWithPagination() {
        var result = service.recordBaseline(7,
                new BaselineExaminationRequest(new BigDecimal("450.25"), HealthStatus.MONITORING, "Baseline findings"));
        assertTrue(result.id() > 0);
        assertEquals("MONITORING", jdbc.queryForObject("SELECT health_status FROM dbo.horse WHERE id = 7", String.class));
        assertEquals("Baseline findings", jdbc.queryForObject("SELECT findings FROM dbo.veterinary_examination", String.class));
        assertEquals(42L, jdbc.queryForObject("SELECT performed_by_user_id FROM dbo.veterinary_examination", Long.class));
        assertEquals(42L, jdbc.queryForObject("SELECT recorded_by_user_id FROM dbo.weight_record", Long.class));
        var page = service.listWeights(7, 0, 20);
        assertEquals(1, page.totalElements());
        assertEquals(new BigDecimal("450.25"), page.items().getFirst().weightKg());
        assertEquals(result.recordedAt(), page.items().getFirst().recordedAt());
        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class, () -> service.recordBaseline(7,
                new BaselineExaminationRequest(new BigDecimal("450"), HealthStatus.FIT, null))).code());
    }

    @Test void failedWeightInsertRollsBackExaminationAndPreservesHealth() {
        assertThrows(RuntimeException.class, () -> service.recordBaseline(7,
                new BaselineExaminationRequest(new BigDecimal("600"), HealthStatus.INJURED, null)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.veterinary_examination", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.weight_record", Integer.class));
        assertNull(jdbc.queryForObject("SELECT health_status FROM dbo.horse WHERE id = 7", String.class));
    }

    @Test void weightHistoryOrdersTiesByIdAndPaginates() {
        var measured = java.time.Instant.parse("2025-01-01T00:00:00Z");
        var first = service.recordWeight(7, new WeightRecordRequest(new BigDecimal("440"), measured));
        var second = service.recordWeight(7, new WeightRecordRequest(new BigDecimal("450"), measured));
        var page0 = service.listWeights(7, 0, 1);
        var page1 = service.listWeights(7, 1, 1);
        assertEquals(2, page0.totalElements());
        assertEquals(second.id(), page0.items().getFirst().id());
        assertEquals(first.id(), page1.items().getFirst().id());
        assertTrue(service.listWeights(7, 2, 1).items().isEmpty());
    }

    @Test void assignmentQueryRequiresMatchingHorseUserAndOpenEffectivePeriod() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS dbo.horse_training_program (id BIGINT PRIMARY KEY, horse_id BIGINT)");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS dbo.trainer_assignment (
                    id BIGINT PRIMARY KEY, horse_training_program_id BIGINT, trainer_user_id BIGINT,
                    effective_from DATE, effective_to DATE)
                """);
        jdbc.update("DELETE FROM dbo.trainer_assignment");
        jdbc.update("DELETE FROM dbo.horse_training_program");
        jdbc.update("INSERT INTO dbo.horse_training_program VALUES (1, 7)");
        var today = java.time.LocalDate.of(2026, 10, 13);
        jdbc.update("INSERT INTO dbo.trainer_assignment VALUES (1, 1, 42, ?, NULL)", today);
        assertTrue(horses.isAssignedTrainer(7, 42, today));
        assertFalse(horses.isAssignedTrainer(8, 42, today));
        assertFalse(horses.isAssignedTrainer(7, 43, today));
        assertFalse(horses.isAssignedTrainer(7, 42, today.minusDays(1)));
        jdbc.update("UPDATE dbo.trainer_assignment SET effective_to = ?", today);
        assertFalse(horses.isAssignedTrainer(7, 42, today));
    }

    @TestConfiguration
    static class FixtureConfiguration {
        @Bean MedicalIdentityProvider testIdentity() {
            return () -> new MedicalIdentityProvider.Identity(42, Set.of("VETERINARIAN"));
        }

        @Bean @Primary MedicalHorseRepository h2HorseAccess(EntityManager em) {
            return new MedicalHorseRepository(em) {
                @Override public String lockLifecycle(long horseId) {
                    var rows = em.createNativeQuery("SELECT lifecycle FROM dbo.horse WHERE id = :id FOR UPDATE")
                            .setParameter("id", horseId).getResultList();
                    return rows.isEmpty() ? null : rows.getFirst().toString();
                }
            };
        }
    }
}