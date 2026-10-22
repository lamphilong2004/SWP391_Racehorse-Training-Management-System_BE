package com.racehorse.notification;

import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.racehorse.notification.NotificationApi.*;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    static final String COLUMNS = "id,category,notification_type,title,message,read_at,target_type,target_id,is_simulated,created_at";
    static final RowMapper<Notification> ROW = (rs, row) -> {
        LocalDateTime readAt = rs.getObject("read_at", LocalDateTime.class);
        return new Notification(rs.getLong("id"), Category.valueOf(rs.getString("category")),
                rs.getString("notification_type"), rs.getString("title"), rs.getString("message"),
                readAt == null ? null : readAt.toInstant(ZoneOffset.UTC), rs.getString("target_type"),
                rs.getObject("target_id", Long.class), rs.getBoolean("is_simulated"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    };

    public NotificationService(JdbcTemplate jdbc, CurrentActorProvider actors) {
        this.jdbc = jdbc;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public Page list(Category category, Boolean read, int page, int size) {
        long userId = recipient();
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid notification pagination.");
        }
        StringBuilder where = new StringBuilder(" WHERE recipient_user_id=?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (category != null) { where.append(" AND category=?"); args.add(category.name()); }
        if (read != null) { where.append(read ? " AND read_at IS NOT NULL" : " AND read_at IS NULL"); }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification" + where, Long.class, args.toArray());
        args.add((long) page * size);
        args.add(size);
        List<Notification> items = jdbc.query("SELECT " + COLUMNS + " FROM dbo.notification" + where
                + " ORDER BY created_at DESC,id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", ROW, args.toArray());
        return new Page(List.copyOf(items), page, size, total, total == 0 ? 0 : (total - 1) / size + 1);
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount() { return count(recipient()); }

    @Transactional
    public Notification markRead(long id) {
        long userId = recipient();
        if (id < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Notification ID must be positive.");
        }
        // Scope both the mutation and readback. Repeated reads keep their original read time.
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        jdbc.update("UPDATE dbo.notification SET read_at=?,updated_at=?"
                + " WHERE id=? AND recipient_user_id=? AND read_at IS NULL", now, now, id, userId);
        return jdbc.query("SELECT " + COLUMNS + " FROM dbo.notification WHERE id=? AND recipient_user_id=?",
                ROW, id, userId).stream().findFirst().orElseThrow(() ->
                new BusinessException(ErrorCode.NOT_FOUND, "Notification was not found."));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public UnreadCount markAllRead() {
        long userId = recipient();
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        jdbc.update("UPDATE dbo.notification SET read_at=?,updated_at=?"
                + " WHERE recipient_user_id=? AND read_at IS NULL", now, now, userId);
        return count(userId);
    }

    private UnreadCount count(long userId) {
        return new UnreadCount(jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.notification WHERE recipient_user_id=? AND read_at IS NULL", Long.class, userId));
    }

    private long recipient() {
        var actor = actors.currentActor().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required."));
        if (actor.roles().isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "An active role is required.");
        }
        return actor.userId();
    }
}
