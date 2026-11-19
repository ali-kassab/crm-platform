package com.crmplatform.notifications.web;

import com.crmplatform.notifications.application.NotificationService;
import com.crmplatform.notifications.domain.Notification;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own notifications (no permission needed beyond being a workspace member). */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private static final Map<String, String> SORT = Map.of("createdAt", "createdAt");

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public PageResponse<NotificationResponse> mine(@RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(notifications.mine(unread, pageable), NotificationResponse::of);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", notifications.unreadCount());
    }

    @PostMapping("/{id}/read")
    public NotificationResponse read(@PathVariable UUID id) {
        return NotificationResponse.of(notifications.markRead(id));
    }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("updated", notifications.markAllRead());
    }

    public record NotificationResponse(UUID id, String type, String entityType, UUID entityId, UUID actorUserId,
            Map<String, Object> payload, boolean read, Instant readAt, Instant createdAt) {

        static NotificationResponse of(Notification n) {
            return new NotificationResponse(n.getId(), n.getType().name(), n.getEntityType(), n.getEntityId(),
                    n.getActorUserId(), n.getPayload(), n.isRead(), n.getReadAt(), n.getCreatedAt());
        }
    }
}
