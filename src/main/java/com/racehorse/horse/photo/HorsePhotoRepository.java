package com.racehorse.horse.photo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class HorsePhotoRepository {

    private final JdbcTemplate jdbc;

    public HorsePhotoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean horseExists(long horseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.horse WHERE id = ?) THEN 1 ELSE 0 END",
                Boolean.class, horseId));
    }

    public long insert(long horseId, String fileReference, Instant uploadedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.horse_photo (horse_id, file_reference, uploaded_at)
                    VALUES (?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, horseId);
            statement.setString(2, fileReference);
            statement.setObject(3, LocalDateTime.ofInstant(uploadedAt, ZoneOffset.UTC));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The database did not return a horse photo identifier.");
        }
        return key.longValue();
    }

    public List<PhotoRow> findByHorseId(long horseId) {
        return jdbc.query("""
                SELECT id, file_reference, uploaded_at
                FROM dbo.horse_photo
                WHERE horse_id = ?
                ORDER BY uploaded_at DESC, id DESC
                """, (rs, rowNum) -> new PhotoRow(
                rs.getLong("id"),
                rs.getString("file_reference"),
                rs.getObject("uploaded_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), horseId);
    }

    public record PhotoRow(long id, String fileReference, Instant uploadedAt) {
    }
}
