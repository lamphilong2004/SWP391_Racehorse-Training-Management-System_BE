package com.racehorse.horse.timeline;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class TimelineRepository {

    private static final String EVENTS = """
            WITH horse_start AS (
                SELECT id, activated_at
                FROM dbo.horse
                WHERE id = ?
            ),
            events AS (
                SELECT h.activated_at AS occurred_at, 'HORSE_ACTIVATED' AS event_type,
                       'Horse activated' AS title, 'HORSE' AS target_type,
                       h.id AS target_id, 0 AS source_order, h.id AS source_id
                FROM horse_start h
                WHERE h.activated_at IS NOT NULL
                UNION ALL
                SELECT CAST(a.effective_from AS DATETIME2(3)), 'STABLE_ASSIGNED',
                       'Stable assignment started', 'STABLE_ASSIGNMENT',
                       a.id, 0, a.id
                FROM dbo.horse_stable_assignment a
                WHERE a.horse_id = ?
                UNION ALL
                SELECT CAST(o.effective_from AS DATETIME2(3)), 'OWNERSHIP_CHANGED',
                       'Ownership changed', 'OWNERSHIP',
                       o.id, 0, o.id
                FROM dbo.horse_ownership o
                WHERE o.horse_id = ?
                UNION ALL
                SELECT e.examined_at, 'VETERINARY_EXAMINATION',
                       'Veterinary examination recorded', 'VETERINARY_EXAMINATION',
                       e.id, 0, e.id
                FROM dbo.veterinary_examination e
                WHERE e.horse_id = ?
                UNION ALL
                SELECT r.effective_from, 'RESTRICTION_CHANGED',
                       'Medical restriction applied', 'MEDICAL_RESTRICTION',
                       r.id, 0, r.id
                FROM dbo.medical_restriction r
                WHERE r.horse_id = ?
                UNION ALL
                SELECT r.lifted_at, 'RESTRICTION_CHANGED',
                       'Medical restriction lifted', 'MEDICAL_RESTRICTION',
                       r.id, 1, r.id
                FROM dbo.medical_restriction r
                WHERE r.horse_id = ? AND r.lifted_at IS NOT NULL
                UNION ALL
                SELECT p.activated_at, 'PROGRAM_ACTIVATED',
                       'Training program activated', 'TRAINING_PROGRAM',
                       p.id, 0, p.id
                FROM dbo.horse_training_program p
                WHERE p.horse_id = ?
                UNION ALL
                SELECT s.actual_start, 'TRAINING_SESSION',
                       'Training session started', 'TRAINING_SESSION',
                       s.id, 0, s.id
                FROM dbo.training_session s
                JOIN dbo.horse_program_phase ph ON ph.id = s.horse_program_phase_id
                JOIN dbo.horse_training_program p ON p.id = ph.horse_training_program_id
                WHERE p.horse_id = ? AND s.actual_start IS NOT NULL
                UNION ALL
                SELECT i.reported_at, 'INCIDENT_REPORTED',
                       'Incident reported', 'INCIDENT',
                       i.id, 0, i.id
                FROM dbo.incident i
                WHERE i.horse_id = ?
            )
            """;

    private static final String ELIGIBLE_EVENTS = """
            SELECT e.occurred_at, e.event_type, e.title, e.target_type, e.target_id,
                   e.source_order, e.source_id
            FROM events e
            CROSS JOIN horse_start h
            WHERE h.activated_at IS NOT NULL
              AND e.occurred_at >= h.activated_at
            """;

    private final JdbcTemplate jdbc;

    public TimelineRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Result page(long horseId, int page, int size) {
        long totalItems = jdbc.queryForObject(EVENTS + """
                SELECT COUNT(*)
                FROM events e
                CROSS JOIN horse_start h
                WHERE h.activated_at IS NOT NULL
                  AND e.occurred_at >= h.activated_at
                """, Long.class, sourceIds(horseId));

        long offset = (long) page * size;
        List<TimelineEvent> items = jdbc.query(EVENTS + ELIGIBLE_EVENTS + """
                ORDER BY e.occurred_at DESC, e.event_type ASC, e.source_order ASC, e.source_id ASC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """, (rs, rowNum) -> {
            LocalDateTime occurredAt = rs.getObject("occurred_at", LocalDateTime.class);
            return new TimelineEvent(
                    occurredAt.toInstant(ZoneOffset.UTC),
                    TimelineEventType.valueOf(rs.getString("event_type")),
                    rs.getString("title"),
                    rs.getString("target_type"),
                    rs.getLong("target_id"));
        }, append(sourceIds(horseId), offset, size));

        int totalPages = totalItems == 0 ? 0 : Math.toIntExact((totalItems + size - 1) / size);
        return new Result(items, page, size, totalItems, totalPages);
    }

    private static Object[] sourceIds(long horseId) {
        return new Object[]{horseId, horseId, horseId, horseId, horseId, horseId, horseId, horseId, horseId};
    }

    private static Object[] append(Object[] values, Object... trailing) {
        Object[] parameters = java.util.Arrays.copyOf(values, values.length + trailing.length);
        System.arraycopy(trailing, 0, parameters, values.length, trailing.length);
        return parameters;
    }

    public record Result(List<TimelineEvent> items, int page, int size, long totalItems, int totalPages) {
        public Result {
            items = List.copyOf(items);
        }
    }
}
