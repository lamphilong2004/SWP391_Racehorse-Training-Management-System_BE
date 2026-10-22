package com.racehorse.notification;

import java.time.Instant;
import java.util.List;

/** CORE-053 representations from the approved shared OpenAPI contract. */
public final class NotificationApi {
    private NotificationApi() {}
    public enum Category { ALERTS, MEDICAL, SCHEDULE }
    public record Notification(long id, Category category, String notificationType, String title,
            String message, Instant readAt, String targetType, Long targetId,
            boolean isSimulated, Instant createdAt) {}
    public record Page(List<Notification> items, int page, int size, long totalItems, long totalPages) {}
    public record UnreadCount(long unread) {}
}
