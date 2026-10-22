package com.racehorse.management;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/** Implements the approved CORE-003 shared OpenAPI summary; never reads clinical or credential columns. */
@Service
public class ManagementSummaryService {
    private static final ZoneId CLUB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;

    public ManagementSummaryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        List<CountByKey> lifecycle = counts("SELECT lifecycle, COUNT(*) FROM dbo.horse GROUP BY lifecycle ORDER BY lifecycle");
        List<CountByKey> availability = counts("""
                SELECT availability, COUNT(*) FROM (
                    SELECT CASE
                        WHEN EXISTS (SELECT 1 FROM dbo.medical_restriction r
                                     WHERE r.horse_id = h.id AND r.lifted_at IS NULL AND r.restriction_level = 'LOCKED') THEN 'LOCKED'
                        WHEN EXISTS (SELECT 1 FROM dbo.medical_restriction r
                                     WHERE r.horse_id = h.id AND r.lifted_at IS NULL AND r.restriction_level = 'LIMITED') THEN 'LIMITED'
                        ELSE 'AVAILABLE' END AS availability
                    FROM dbo.horse h WHERE h.lifecycle = 'ACTIVE'
                ) active_horses GROUP BY availability ORDER BY availability
                """);
        long totalBoxes = count("SELECT COUNT(*) FROM dbo.stable_box");
        long occupied = count("""
                SELECT COUNT(*) FROM dbo.stable_box b WHERE EXISTS (
                    SELECT 1 FROM dbo.horse_stable_assignment a
                    WHERE a.stable_box_id = b.id AND a.effective_to IS NULL)
                """);
        LocalDate today = LocalDate.now(CLUB_ZONE);
        List<CountByKey> training = counts("""
                SELECT status, COUNT(*) FROM dbo.training_session
                WHERE scheduled_start >= ? AND scheduled_start < ? GROUP BY status ORDER BY status
                """, LocalDateTime.ofInstant(today.atStartOfDay(CLUB_ZONE).toInstant(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(today.plusDays(1).atStartOfDay(CLUB_ZONE).toInstant(), ZoneOffset.UTC));
        return new Dashboard(lifecycle, availability, new BoxOccupancy(totalBoxes, occupied, totalBoxes - occupied),
                counts("SELECT account_status, COUNT(*) FROM dbo.app_user GROUP BY account_status ORDER BY account_status"),
                new NeedsAttention(count("SELECT COUNT(*) FROM dbo.horse WHERE lifecycle = 'DRAFT'"),
                        count("SELECT COUNT(*) FROM dbo.registration_request WHERE status = 'PENDING_APPROVAL'")),
                new TrainingToday(training),
                count("SELECT COUNT(*) FROM dbo.incident WHERE status IN ('REPORTED','UNDER_REVIEW')"),
                recentAudit());
    }

    private List<AuditEntry> recentAudit() {
        // Select the approved safe projection explicitly; app_user password/email and arbitrary payloads stay private.
        return jdbc.query("""
                SELECT TOP 10 a.id, a.occurred_at, a.actor_role, a.action, a.target_type, a.target_id,
                              a.safe_summary, a.source, u.id AS user_id, u.full_name, u.user_code
                FROM dbo.audit_log a LEFT JOIN dbo.app_user u ON u.id = a.actor_user_id
                ORDER BY a.occurred_at DESC, a.id DESC
                """, (row, index) -> {
            Long userId = row.getObject("user_id", Long.class);
            return new AuditEntry(row.getLong("id"),
                    row.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    userId == null ? null : new AuditActor(userId, row.getString("full_name"), row.getString("user_code")),
                    row.getString("actor_role"), row.getString("action"), row.getString("target_type"),
                    row.getObject("target_id", Long.class), row.getString("safe_summary"), row.getString("source"));
        });
    }

    private List<CountByKey> counts(String sql, Object... arguments) {
        return jdbc.query(sql, (row, index) -> new CountByKey(row.getString(1), row.getLong(2)), arguments);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    public record CountByKey(String key, long count) {}
    public record BoxOccupancy(long total, long occupied, long free) {}
    public record NeedsAttention(long pendingActivations, long pendingRegistrations) {}
    public record TrainingToday(List<CountByKey> byStatus) {}
    public record AuditActor(long id, String fullName, String userCode) {}
    public record AuditEntry(long id, Instant occurredAt, AuditActor actor, String actorRole, String action,
                             String targetType, Long targetId, String safeSummary, String source) {}
    public record Dashboard(List<CountByKey> horsesByLifecycle, List<CountByKey> horsesByAvailability,
                            BoxOccupancy boxOccupancy, List<CountByKey> accountsByStatus,
                            NeedsAttention needsAttention, TrainingToday trainingToday, long openIncidents,
                            List<AuditEntry> recentAudit) {}
}
