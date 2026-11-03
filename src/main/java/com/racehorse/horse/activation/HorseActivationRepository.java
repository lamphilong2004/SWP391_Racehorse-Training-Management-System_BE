package com.racehorse.horse.activation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class HorseActivationRepository {

    private static final String FACTS_QUERY = """
            SELECT h.id, h.horse_code, h.name, h.lifecycle, h.activated_at,
                   CASE WHEN NULLIF(TRIM(h.horse_code), '') IS NOT NULL
                              AND NULLIF(TRIM(h.name), '') IS NOT NULL
                        THEN 1 ELSE 0 END AS identity_valid,
                   CASE WHEN
                       ((h.sire_horse_id IS NULL
                             AND (h.sire_name IS NULL OR TRIM(h.sire_name) = ''))
                         OR (h.sire_horse_id IS NOT NULL AND h.sire_name IS NULL
                             AND EXISTS (SELECT 1 FROM dbo.horse sire WHERE sire.id = h.sire_horse_id))
                         OR (h.sire_horse_id IS NULL AND h.sire_name IS NOT NULL
                             AND TRIM(h.sire_name) <> ''))
                       AND
                       ((h.dam_horse_id IS NULL
                             AND (h.dam_name IS NULL OR TRIM(h.dam_name) = ''))
                         OR (h.dam_horse_id IS NOT NULL AND h.dam_name IS NULL
                             AND EXISTS (SELECT 1 FROM dbo.horse dam WHERE dam.id = h.dam_horse_id))
                         OR (h.dam_horse_id IS NULL AND h.dam_name IS NOT NULL
                             AND TRIM(h.dam_name) <> ''))
                       AND (h.sire_horse_id IS NULL OR h.sire_horse_id <> h.id)
                       AND (h.dam_horse_id IS NULL OR h.dam_horse_id <> h.id)
                       AND (h.sire_horse_id IS NULL OR h.dam_horse_id IS NULL
                            OR h.sire_horse_id <> h.dam_horse_id)
                        THEN 1 ELSE 0 END AS pedigree_valid,
                   CASE WHEN EXISTS (
                       SELECT 1
                       FROM dbo.horse_ownership ownership
                       JOIN dbo.app_user owner_user ON owner_user.id = ownership.owner_user_id
                       JOIN dbo.user_role owner_role ON owner_role.user_id = owner_user.id
                       JOIN dbo.role role ON role.id = owner_role.role_id
                       WHERE ownership.horse_id = h.id
                         AND ownership.effective_to IS NULL
                         AND owner_user.account_status = 'ACTIVE'
                         AND role.role_code = 'HORSE_OWNER'
                   ) THEN 1 ELSE 0 END AS owner_valid,
                   CASE WHEN EXISTS (
                       SELECT 1
                       FROM dbo.horse_stable_assignment assignment
                       JOIN dbo.stable_box box ON box.id = assignment.stable_box_id
                       WHERE assignment.horse_id = h.id
                         AND assignment.effective_to IS NULL
                         AND NOT EXISTS (
                             SELECT 1
                             FROM dbo.horse_stable_assignment competing
                             WHERE competing.stable_box_id = assignment.stable_box_id
                               AND competing.effective_to IS NULL
                               AND competing.horse_id <> h.id
                         )
                   ) THEN 1 ELSE 0 END AS free_stable_box,
                   CASE WHEN h.health_status IS NOT NULL AND EXISTS (
                       SELECT 1
                       FROM dbo.veterinary_examination examination
                       WHERE examination.horse_id = h.id
                         AND examination.exam_type = 'BASELINE'
                         AND examination.medical_case_id IS NULL
                         AND examination.health_status_set = h.health_status
                         AND EXISTS (
                             SELECT 1
                             FROM dbo.weight_record weight
                             WHERE weight.horse_id = h.id
                               AND weight.context = 'BASELINE'
                         )
                   ) THEN 1 ELSE 0 END AS veterinarian_baseline
            FROM dbo.horse h
            WHERE h.id = ?
            """;

    private final JdbcTemplate jdbc;

    public HorseActivationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<HorseFacts> findFacts(long horseId) {
        return jdbc.query(FACTS_QUERY, (resultSet, rowNumber) -> mapFacts(resultSet), horseId)
                .stream()
                .findFirst();
    }

    public long countDraftHorses() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.horse WHERE lifecycle = 'DRAFT'",
                Long.class);
        return count == null ? 0 : count;
    }

    public List<Long> findDraftHorseIds(int page, int size) {
        return jdbc.query("""
                        SELECT id
                        FROM dbo.horse
                        WHERE lifecycle = 'DRAFT'
                        ORDER BY id
                        OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                        """,
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                (long) page * size, size);
    }

    public boolean hasReadinessReview(long horseId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM dbo.audit_log
                    WHERE action = 'HEAD_TRAINER_READINESS_REVIEWED'
                      AND target_type = 'HORSE'
                      AND target_id = ?
                ) THEN 1 ELSE 0 END
                """, Boolean.class, horseId);
        return Boolean.TRUE.equals(exists);
    }

    public int activateDraft(long horseId, LocalDateTime activatedAt) {
        return jdbc.update("""
                UPDATE dbo.horse
                SET lifecycle = 'ACTIVE', activated_at = ?, updated_at = ?
                WHERE id = ? AND lifecycle = 'DRAFT' AND activated_at IS NULL
                """, activatedAt, activatedAt, horseId);
    }

    private static HorseFacts mapFacts(ResultSet resultSet) throws SQLException {
        LocalDateTime activatedAt = resultSet.getObject("activated_at", LocalDateTime.class);
        return new HorseFacts(
                resultSet.getLong("id"),
                resultSet.getString("horse_code"),
                resultSet.getString("name"),
                resultSet.getString("lifecycle"),
                activatedAt == null ? null : activatedAt.toInstant(ZoneOffset.UTC),
                resultSet.getInt("identity_valid") == 1,
                resultSet.getInt("pedigree_valid") == 1,
                resultSet.getInt("owner_valid") == 1,
                resultSet.getInt("free_stable_box") == 1,
                resultSet.getInt("veterinarian_baseline") == 1);
    }

    public record HorseFacts(
            long horseId,
            String horseCode,
            String name,
            String lifecycle,
            Instant activatedAt,
            boolean identityValid,
            boolean pedigreeValid,
            boolean ownerValid,
            boolean freeStableBox,
            boolean veterinarianBaseline) {

        public boolean allMandatoryConditionsSatisfied() {
            return identityValid && pedigreeValid && ownerValid && freeStableBox && veterinarianBaseline;
        }
    }
}
