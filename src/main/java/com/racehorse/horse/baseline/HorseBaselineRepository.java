package com.racehorse.horse.baseline;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Repository
public class HorseBaselineRepository {

    private final JdbcTemplate jdbc;

    public HorseBaselineRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean horseExists(long horseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1 FROM dbo.horse WHERE id = ?
                ) THEN 1 ELSE 0 END
                """, Boolean.class, horseId));
    }

    public boolean userIsActive(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1 FROM dbo.app_user
                    WHERE id = ? AND account_status = 'ACTIVE'
                ) THEN 1 ELSE 0 END
                """, Boolean.class, userId));
    }

    public boolean trainerHasHorse(long trainerUserId, long horseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM dbo.horse_training_program p
                    JOIN dbo.trainer_assignment ta
                      ON ta.horse_training_program_id = p.id
                    WHERE p.horse_id = ?
                      AND ta.trainer_user_id = ?
                      AND ta.effective_to IS NULL
                ) THEN 1 ELSE 0 END
                """, Boolean.class, horseId, trainerUserId));
    }

    public boolean hasBaseline(long horseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM dbo.veterinary_examination
                    WHERE horse_id = ? AND exam_type = 'BASELINE'
                ) THEN 1 ELSE 0 END
                """, Boolean.class, horseId));
    }

    public long insertBaseline(long horseId, long veterinarianId, LocalDateTime recordedAt,
                               HorseHealthStatus healthStatus, String clinicalNotes) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.veterinary_examination
                        (horse_id, medical_case_id, performed_by_user_id, exam_type, examined_at,
                         findings, health_status_set)
                    VALUES (?, NULL, ?, 'BASELINE', ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, horseId);
            statement.setLong(2, veterinarianId);
            statement.setObject(3, recordedAt);
            statement.setString(4, clinicalNotes);
            statement.setString(5, healthStatus.name());
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    public void insertBaselineWeight(long horseId, long veterinarianId, java.math.BigDecimal weightKg,
                                     LocalDateTime recordedAt) {
        jdbc.update("""
                INSERT INTO dbo.weight_record
                    (horse_id, recorded_by_user_id, weight, measured_at, context)
                VALUES (?, ?, ?, ?, 'BASELINE')
                """, horseId, veterinarianId, weightKg, recordedAt);
    }

    public int updateHealthStatus(long horseId, HorseHealthStatus healthStatus, LocalDateTime updatedAt) {
        return jdbc.update("""
                UPDATE dbo.horse
                SET health_status = ?, updated_at = ?
                WHERE id = ?
                """, healthStatus.name(), updatedAt, horseId);
    }

    public long insertWeight(long horseId, long veterinarianId, java.math.BigDecimal weightKg,
                             LocalDateTime recordedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.weight_record
                        (horse_id, recorded_by_user_id, weight, measured_at)
                    VALUES (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, horseId);
            statement.setLong(2, veterinarianId);
            statement.setBigDecimal(3, weightKg);
            statement.setObject(4, recordedAt);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    public WeightPage findWeights(long horseId, int page, int size) {
        Long total = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CAST(1 AS BIGINT)), 0)
                FROM dbo.weight_record WHERE horse_id = ?
                """, Long.class, horseId);
        List<WeightRecord> items = jdbc.query("""
                SELECT id, horse_id, weight, measured_at
                FROM dbo.weight_record
                WHERE horse_id = ?
                ORDER BY measured_at DESC, id DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """, (resultSet, rowNumber) -> new WeightRecord(
                resultSet.getLong("id"),
                resultSet.getLong("horse_id"),
                resultSet.getBigDecimal("weight"),
                resultSet.getObject("measured_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                horseId, (long) page * size, size);
        return new WeightPage(items, total == null ? 0 : total);
    }

    private static long generatedId(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        Object id = keys == null ? null : keys.entrySet().stream()
                .filter(entry -> "id".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (!(id instanceof Number generatedId)) {
            throw new IllegalStateException("The database did not return a generated identity.");
        }
        return generatedId.longValue();
    }

    public record WeightPage(List<WeightRecord> items, long totalElements) {
    }
}
