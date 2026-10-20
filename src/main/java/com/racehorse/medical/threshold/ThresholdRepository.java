package com.racehorse.medical.threshold;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ThresholdRepository {
    private final JdbcTemplate jdbc;

    public ThresholdRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean horseExists(long horseId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.horse WHERE id=?", Integer.class, horseId) > 0;
    }

    public boolean trainerAssignedToHorse(long horseId, long trainerId, Instant at) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.horse_training_program p
                JOIN dbo.trainer_assignment ta ON ta.horse_training_program_id=p.id
                WHERE p.horse_id=? AND p.status='ACTIVE' AND ta.trainer_user_id=?
                  AND ta.effective_from<=? AND (ta.effective_to IS NULL OR ta.effective_to>=CAST(? AS DATE))
                """, Integer.class, horseId, trainerId, java.sql.Date.from(at), java.sql.Date.from(at)) > 0;
    }

    public List<ThresholdRecord> findOverrides(long horseId) {
        return jdbc.query("""
                SELECT id,horse_id,set_by_user_id,metric,critical_value,reason,effective_from,effective_to
                FROM dbo.horse_threshold_override WHERE horse_id=? ORDER BY effective_from DESC,id DESC
                """, (rs, n) -> map(rs), horseId);
    }

    public Optional<ThresholdRecord> findEffectiveOverride(long horseId, Instant at) {
        return jdbc.query("""
                SELECT TOP 1 id,horse_id,set_by_user_id,metric,critical_value,reason,effective_from,effective_to
                FROM dbo.horse_threshold_override
                WHERE horse_id=? AND metric='HEART_RATE' AND effective_from<=?
                  AND (effective_to IS NULL OR effective_to>?)
                ORDER BY effective_from DESC,id DESC
                """, (rs, n) -> map(rs), horseId, java.sql.Timestamp.from(at), java.sql.Timestamp.from(at))
                .stream().findFirst();
    }

    public List<ThresholdRecord> findIntervalsOverlappingFrom(long horseId, Instant from) {
        return jdbc.query("""
                SELECT id,horse_id,set_by_user_id,metric,critical_value,reason,effective_from,effective_to
                FROM dbo.horse_threshold_override
                WHERE horse_id=? AND metric='HEART_RATE' AND (effective_to IS NULL OR effective_to>?)
                ORDER BY effective_from,id
                """, (rs, n) -> map(rs), horseId, java.sql.Timestamp.from(from));
    }

    public Optional<ThresholdRecord> findOpenOverride(long horseId) {
        return jdbc.query("""
                SELECT id,horse_id,set_by_user_id,metric,critical_value,reason,effective_from,effective_to
                FROM dbo.horse_threshold_override WHERE horse_id=? AND metric='HEART_RATE' AND effective_to IS NULL
                """, (rs, n) -> map(rs), horseId).stream().findFirst();
    }

    public int endInterval(long id, Instant effectiveTo) {
        return jdbc.update("UPDATE dbo.horse_threshold_override SET effective_to=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND effective_to IS NULL",
                java.sql.Timestamp.from(effectiveTo), id);
    }

    public long insertOverride(long horseId, long actorId, java.math.BigDecimal value, String reason, Instant from) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO dbo.horse_threshold_override(horse_id,set_by_user_id,metric,critical_value,reason,effective_from)
                    VALUES (?,?,'HEART_RATE',?,?,?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, horseId); statement.setLong(2, actorId); statement.setBigDecimal(3, value);
            statement.setString(4, reason); statement.setTimestamp(5, java.sql.Timestamp.from(from));
            return statement;
        }, keys);
        Number key = keys.getKeys().values().stream().filter(Number.class::isInstance)
                .map(Number.class::cast).findFirst().orElse(null);
        if (key == null) throw new IllegalStateException("SQL Server did not return the threshold override identity.");
        return key.longValue();
    }

    public Optional<ThresholdRecord> findById(long id) {
        return jdbc.query("""
                SELECT id,horse_id,set_by_user_id,metric,critical_value,reason,effective_from,effective_to
                FROM dbo.horse_threshold_override WHERE id=?
                """, (rs, n) -> map(rs), id).stream().findFirst();
    }

    private static ThresholdRecord map(ResultSet rs) throws SQLException {
        return new ThresholdRecord(rs.getLong("id"), rs.getLong("horse_id"), rs.getLong("set_by_user_id"),
                rs.getString("metric"), rs.getBigDecimal("critical_value"), rs.getString("reason"),
                rs.getTimestamp("effective_from").toInstant(),
                rs.getTimestamp("effective_to") == null ? null : rs.getTimestamp("effective_to").toInstant());
    }

    public record ThresholdRecord(long id, long horseId, long setByUserId, String metric,
                                  java.math.BigDecimal value, String reason, Instant from, Instant to) {}
}
