package com.racehorse.exercise;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ExerciseRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExerciseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExerciseResponse> findAll(String query, String exerciseType) {
        String pattern = query == null ? null : "%" + query + "%";
        return jdbcTemplate.query("""
                SELECT id, name, exercise_type, description, target_metric
                FROM dbo.exercise_definition
                WHERE (? IS NULL OR UPPER(name) LIKE UPPER(?))
                  AND (? IS NULL OR exercise_type = ?)
                ORDER BY name, id
                """, (rs, rowNum) -> map(rs.getLong("id"), rs.getString("name"),
                rs.getString("exercise_type"), rs.getString("description"), rs.getString("target_metric")),
                query, pattern, exerciseType, exerciseType);
    }

    public Optional<ExerciseResponse> findById(long id) {
        List<ExerciseResponse> rows = jdbcTemplate.query("""
                SELECT id, name, exercise_type, description, target_metric
                FROM dbo.exercise_definition WHERE id = ?
                """, (rs, rowNum) -> map(rs.getLong("id"), rs.getString("name"),
                rs.getString("exercise_type"), rs.getString("description"), rs.getString("target_metric")), id);
        return rows.stream().findFirst();
    }

    public ExerciseResponse insert(ExerciseInput input) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.exercise_definition (name, exercise_type, description, target_metric)
                    VALUES (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, input.name());
            statement.setString(2, input.exerciseType());
            statement.setString(3, input.description());
            statement.setString(4, input.targetMetric());
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKeyList().stream()
                .map(ExerciseRepository::generatedId)
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .findFirst()
                .orElse(null);
        if (id == null) {
            throw new IllegalStateException("The database did not return the created exercise id.");
        }
        return findById(id.longValue()).orElseThrow();
    }

    public int update(long id, ExerciseInput input) {
        return jdbcTemplate.update("""
                UPDATE dbo.exercise_definition
                SET name = ?, exercise_type = ?, description = ?, target_metric = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, input.name(), input.exerciseType(), input.description(), input.targetMetric(), id);
    }

    private ExerciseResponse map(long id, String name, String exerciseType, String description, String targetMetric) {
        return new ExerciseResponse(id, name, exerciseType, description, targetMetric);
    }

    private static Object generatedId(Map<String, Object> generatedKeys) {
        return generatedKeys.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("id"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
