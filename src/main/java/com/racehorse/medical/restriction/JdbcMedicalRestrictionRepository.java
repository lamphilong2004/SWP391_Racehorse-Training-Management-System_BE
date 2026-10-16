package com.racehorse.medical.restriction;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcMedicalRestrictionRepository {

    private static final RowMapper<RestrictionRecord> RESTRICTION_ROW_MAPPER = (resultSet, rowNumber) -> {
        Timestamp liftedAt = resultSet.getTimestamp("lifted_at");
        java.sql.Date reviewDate = resultSet.getDate("review_date");
        long medicalCaseIdValue = resultSet.getLong("medical_case_id");
        Long medicalCaseId = resultSet.wasNull() ? null : medicalCaseIdValue;
        return new RestrictionRecord(
                resultSet.getLong("id"),
                resultSet.getLong("horse_id"),
                medicalCaseId,
                resultSet.getLong("set_by_user_id"),
                RestrictionLevel.valueOf(resultSet.getString("restriction_level")),
                resultSet.getString("reason"),
                resultSet.getTimestamp("effective_from").toInstant(),
                liftedAt == null ? null : liftedAt.toInstant(),
                reviewDate == null ? null : reviewDate.toLocalDate());
    };

    private final JdbcTemplate jdbcTemplate;

    public JdbcMedicalRestrictionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean horseExists(long horseId) {
        return count("SELECT COUNT(*) FROM dbo.horse WHERE id = ?", horseId) > 0;
    }

    public boolean medicalCaseBelongsToHorse(long medicalCaseId, long horseId) {
        return count("SELECT COUNT(*) FROM dbo.medical_case WHERE id = ? AND horse_id = ?", medicalCaseId, horseId) > 0;
    }

    public boolean exerciseExists(long exerciseId) {
        return count("SELECT COUNT(*) FROM dbo.exercise_definition WHERE id = ?", exerciseId) > 0;
    }

    public Optional<String> findExerciseType(long exerciseId) {
        return jdbcTemplate.query("SELECT exercise_type FROM dbo.exercise_definition WHERE id = ?",
                resultSet -> resultSet.next() ? Optional.ofNullable(resultSet.getString("exercise_type")) : Optional.empty(),
                exerciseId);
    }

    public boolean trainerAssignedToHorse(long horseId, long userId, LocalDate today) {
        return count("""
                SELECT COUNT(*)
                FROM dbo.trainer_assignment ta
                JOIN dbo.horse_training_program hp ON hp.id = ta.horse_training_program_id
                WHERE hp.horse_id = ? AND ta.trainer_user_id = ?
                  AND ta.effective_to IS NULL AND ta.effective_from <= ?
                """, horseId, userId, java.sql.Date.valueOf(today)) > 0;
    }

    public boolean workRiderAssignedToHorse(long horseId, long userId) {
        return count("""
                SELECT COUNT(*)
                FROM dbo.training_session ts
                JOIN dbo.horse_program_phase hpp ON hpp.id = ts.horse_program_phase_id
                JOIN dbo.horse_training_program hp ON hp.id = hpp.horse_training_program_id
                                WHERE hp.horse_id = ? AND ts.work_rider_user_id = ?
                                    AND ts.status IN ('SCHEDULED','IN_PROGRESS','PAUSED')
                """, horseId, userId) > 0;
    }

    public boolean hasActiveRestriction(long horseId) {
        return count("SELECT COUNT(*) FROM dbo.medical_restriction WHERE horse_id = ? AND lifted_at IS NULL", horseId) > 0;
    }

    public Optional<RestrictionRecord> findActiveRestriction(long horseId) {
        return queryOne("SELECT * FROM dbo.medical_restriction WHERE horse_id = ? AND lifted_at IS NULL", horseId);
    }

    public Optional<RestrictionRecord> findRestriction(long restrictionId) {
        return queryOne("SELECT * FROM dbo.medical_restriction WHERE id = ?", restrictionId);
    }

    public List<ExerciseRule> findRules(long restrictionId) {
        return jdbcTemplate.query("""
                SELECT exercise_definition_id, rule_type, exercise_type_note
                FROM dbo.restriction_exercise_rule
                WHERE medical_restriction_id = ?
                ORDER BY id
                """, (resultSet, rowNumber) -> {
            long exerciseIdValue = resultSet.getLong("exercise_definition_id");
            Long exerciseId = resultSet.wasNull() ? null : exerciseIdValue;
            return new ExerciseRule(exerciseId,
                    ExerciseRuleType.fromDatabaseValue(resultSet.getString("rule_type")),
                    resultSet.getString("exercise_type_note"));
        }, restrictionId);
    }

    public long insertRestriction(long horseId, Long medicalCaseId, long actorId, RestrictionLevel level,
                                  String reason, Instant effectiveAt, LocalDate reviewDate) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.medical_restriction
                        (horse_id, medical_case_id, set_by_user_id, restriction_level, reason,
                         effective_from, review_date, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, horseId);
            if (medicalCaseId == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, medicalCaseId);
            }
            statement.setLong(3, actorId);
            statement.setString(4, level.name());
            statement.setString(5, reason);
            statement.setTimestamp(6, Timestamp.from(effectiveAt));
            if (reviewDate == null) {
                statement.setNull(7, java.sql.Types.DATE);
            } else {
                statement.setDate(7, java.sql.Date.valueOf(reviewDate));
            }
            statement.setTimestamp(8, Timestamp.from(Instant.now()));
            return statement;
        }, keyHolder);

        Object keyValue = keyHolder.getKeys().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("id"))
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        Number key = keyValue instanceof Number number ? number : null;
        if (key == null) {
            throw new IllegalStateException("The database did not return the created restriction id.");
        }
        return key.longValue();
    }

    public void replaceRules(long restrictionId, List<ExerciseRule> rules) {
        jdbcTemplate.update("DELETE FROM dbo.restriction_exercise_rule WHERE medical_restriction_id = ?", restrictionId);
        if (rules.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO dbo.restriction_exercise_rule
                    (medical_restriction_id, exercise_definition_id, rule_type, exercise_type_note)
                VALUES (?, ?, ?, ?)
                """, rules, rules.size(), (statement, rule) -> {
            statement.setLong(1, restrictionId);
            if (rule.exerciseId() == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, rule.exerciseId());
            }
            statement.setString(3, rule.ruleType().databaseValue());
            statement.setString(4, rule.exerciseTypeNote());
        });
    }

    public int liftRestriction(long restrictionId, Instant liftedAt) {
        return jdbcTemplate.update("""
                UPDATE dbo.medical_restriction
                SET lifted_at = ?, updated_at = ?
                WHERE id = ? AND lifted_at IS NULL
                """, Timestamp.from(liftedAt), Timestamp.from(liftedAt), restrictionId);
    }

    public List<RestrictionRecord> findRestrictions(int page, int size, Long trainerUserId, LocalDate today) {
        String trainerScope = trainerUserId == null ? "" : """
                AND EXISTS (
                    SELECT 1
                    FROM dbo.trainer_assignment ta
                    JOIN dbo.horse_training_program hp ON hp.id = ta.horse_training_program_id
                    WHERE hp.horse_id = mr.horse_id AND ta.trainer_user_id = ?
                      AND ta.effective_to IS NULL AND ta.effective_from <= ?
                )
                """;
        String sql = "SELECT mr.* FROM dbo.medical_restriction mr WHERE 1 = 1 " + trainerScope
                + " ORDER BY mr.effective_from DESC, mr.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        long offset = (long) page * size;
        if (trainerUserId == null) {
            return jdbcTemplate.query(sql, RESTRICTION_ROW_MAPPER, offset, size);
        }
        return jdbcTemplate.query(sql, RESTRICTION_ROW_MAPPER, trainerUserId,
                java.sql.Date.valueOf(today), offset, size);
    }

    public long countRestrictions(Long trainerUserId, LocalDate today) {
        String trainerScope = trainerUserId == null ? "" : """
                AND EXISTS (
                    SELECT 1
                    FROM dbo.trainer_assignment ta
                    JOIN dbo.horse_training_program hp ON hp.id = ta.horse_training_program_id
                    WHERE hp.horse_id = mr.horse_id AND ta.trainer_user_id = ?
                      AND ta.effective_to IS NULL AND ta.effective_from <= ?
                )
                """;
        String sql = "SELECT COUNT(*) FROM dbo.medical_restriction mr WHERE 1 = 1 " + trainerScope;
        if (trainerUserId == null) {
            return jdbcTemplate.queryForObject(sql, Long.class);
        }
        return jdbcTemplate.queryForObject(sql, Long.class, trainerUserId, java.sql.Date.valueOf(today));
    }

    private Optional<RestrictionRecord> queryOne(String sql, long id) {
        List<RestrictionRecord> results = jdbcTemplate.query(sql, RESTRICTION_ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    private long count(String sql, Object... arguments) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return result == null ? 0 : result;
    }
}