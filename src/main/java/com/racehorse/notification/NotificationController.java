package com.racehorse.notification;

import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import static com.racehorse.notification.NotificationApi.*;

/** All authenticated roles use their own recipient scope; no user ID is accepted from a request. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    @Operation(operationId = "listNotifications", summary = "List my notifications")
    public ApiResponse<Page> list(@RequestParam(required = false) Category category,
            @RequestParam(required = false) Boolean read,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(category, read, page, size));
    }

    @GetMapping("/unread-count")
    @Operation(operationId = "getUnreadNotificationCount", summary = "Count my unread notifications")
    public ApiResponse<UnreadCount> unreadCount() { return ApiResponse.ok(service.unreadCount()); }

    @PatchMapping("/{id}/read")
    @Operation(operationId = "markNotificationRead", summary = "Mark my notification read")
    public ApiResponse<Notification> markRead(@PathVariable long id) { return ApiResponse.ok(service.markRead(id)); }

    @PostMapping("/read-all")
    @Operation(operationId = "markAllNotificationsRead", summary = "Mark all my notifications read")
    public ApiResponse<UnreadCount> markAllRead() { return ApiResponse.ok(service.markAllRead()); }
}
