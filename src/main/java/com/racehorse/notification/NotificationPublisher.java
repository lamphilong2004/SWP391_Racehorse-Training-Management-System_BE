package com.racehorse.notification;

import com.racehorse.common.jdbc.GeneratedIdentity;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Existing producers call this hook with each inserted notification ID inside their write transaction. */
@Component
public class NotificationPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);
    private final JdbcTemplate jdbc;
    private final SimpMessagingTemplate broker;

    public NotificationPublisher(JdbcTemplate jdbc, SimpMessagingTemplate broker) {
        this.jdbc = jdbc;
        this.broker = broker;
    }

    /** Persists the canonical row and schedules its user-queue delivery in the producer's transaction. */
    public long persistAndPublish(long recipient, NotificationApi.Category category, String type, String title,
            String message, String targetType, Long targetId, boolean simulated) {
        requireTransaction();
        var keys = new GeneratedKeyHolder();
        LocalDateTime createdAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO dbo.notification"
                    + " (recipient_user_id,category,notification_type,title,message,target_type,target_id,is_simulated,created_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, recipient);
            ps.setString(2, category.name());
            ps.setString(3, type);
            ps.setString(4, title);
            ps.setString(5, message);
            ps.setString(6, targetType);
            if (targetId == null) ps.setNull(7, java.sql.Types.BIGINT); else ps.setLong(7, targetId);
            ps.setBoolean(8, simulated);
            ps.setObject(9, createdAt);
            return ps;
        }, keys);
        long id = GeneratedIdentity.requireId(keys);
        publishAfterCommit(id);
        return id;
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Notification publishing requires the producer's active transaction.");
        }
    }

    public void publishAfterCommit(long notificationId) {
        requireTransaction();
        var notification = jdbc.query("SELECT " + NotificationService.COLUMNS
                + " FROM dbo.notification WHERE id=?", NotificationService.ROW, notificationId)
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Persisted notification is required."));
        long recipient = jdbc.queryForObject("SELECT recipient_user_id FROM dbo.notification WHERE id=?",
                Long.class, notificationId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    broker.convertAndSendToUser(Long.toString(recipient), "/queue/notifications", notification);
                } catch (RuntimeException unavailable) {
                    // REST remains durable; delivery failure must not report a committed business write as failed.
                    log.warn("Notification {} persisted; realtime delivery unavailable. Refresh the notification list.", notificationId);
                }
            }
        });
    }
}
