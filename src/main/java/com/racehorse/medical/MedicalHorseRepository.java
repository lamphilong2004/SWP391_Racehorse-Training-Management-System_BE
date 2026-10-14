package com.racehorse.medical;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Narrow access to CORE-012's horse table. Native SQL avoids registering a second
 * partial Horse entity, or implementing the horse profile owned by CORE-012.
 */
@Repository
public class MedicalHorseRepository {
    private final EntityManager em;
    public MedicalHorseRepository(EntityManager em) { this.em = em; }

    public boolean exists(long horseId) {
        return !em.createNativeQuery("SELECT id FROM dbo.horse WHERE id = :id")
                .setParameter("id", horseId).getResultList().isEmpty();
    }

    public String lockLifecycle(long horseId) {
        var rows = em.createNativeQuery(
                "SELECT lifecycle FROM dbo.horse WITH (UPDLOCK, HOLDLOCK) WHERE id = :id")
                .setParameter("id", horseId).getResultList();
        return rows.isEmpty() ? null : rows.getFirst().toString();
    }

    public void updateHealth(long horseId, HealthStatus status, Instant now) {
        em.createNativeQuery("UPDATE dbo.horse SET health_status = :status, updated_at = :now WHERE id = :id")
                .setParameter("status", status.name())
                .setParameter("now", java.sql.Timestamp.from(now))
                .setParameter("id", horseId).executeUpdate();
    }

    public boolean isAssignedTrainer(long horseId, long userId, LocalDate today) {
        return !em.createNativeQuery("""
                SELECT ta.id FROM dbo.trainer_assignment ta
                JOIN dbo.horse_training_program hp ON hp.id = ta.horse_training_program_id
                WHERE hp.horse_id = :horseId AND ta.trainer_user_id = :userId
                AND ta.effective_to IS NULL AND ta.effective_from <= :today
                """).setParameter("horseId", horseId).setParameter("userId", userId)
                .setParameter("today", today).getResultList().isEmpty();
    }
}
