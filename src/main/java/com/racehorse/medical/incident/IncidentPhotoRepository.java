package com.racehorse.medical.incident;

import com.racehorse.common.jdbc.GeneratedIdentity;
import org.springframework.dao.support.DataAccessUtils;
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
import java.util.Optional;

/** dbo.incident_photo (database/schema.sql, one of the 51 entities): photos attached to an incident report. */
@Repository
public class IncidentPhotoRepository {

    private final JdbcTemplate jdbc;

    public IncidentPhotoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long incidentId, String fileReference, Instant uploadedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.incident_photo (incident_id, file_reference, uploaded_at)
                    VALUES (?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, incidentId);
            statement.setString(2, fileReference);
            statement.setObject(3, LocalDateTime.ofInstant(uploadedAt, ZoneOffset.UTC));
            return statement;
        }, keyHolder);
        return GeneratedIdentity.requireId(keyHolder);
    }

    public int count(long incidentId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.incident_photo WHERE incident_id = ?",
                Integer.class, incidentId);
        return count == null ? 0 : count;
    }

    public List<PhotoRow> findByIncident(long incidentId) {
        return jdbc.query("""
                SELECT id, file_reference, uploaded_at
                FROM dbo.incident_photo
                WHERE incident_id = ?
                ORDER BY uploaded_at, id
                """, (rs, rowNum) -> new PhotoRow(
                rs.getLong("id"),
                rs.getString("file_reference"),
                rs.getObject("uploaded_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), incidentId);
    }

    public Optional<String> findReference(long incidentId, long photoId) {
        List<String> references = jdbc.query(
                "SELECT file_reference FROM dbo.incident_photo WHERE id = ? AND incident_id = ?",
                (rs, rowNum) -> rs.getString("file_reference"), photoId, incidentId);
        return Optional.ofNullable(DataAccessUtils.singleResult(references));
    }

    public record PhotoRow(long id, String fileReference, Instant uploadedAt) {
    }
}
