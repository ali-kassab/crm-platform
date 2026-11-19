package com.crmplatform.notifications.application;

import com.crmplatform.notifications.domain.Notification;
import com.crmplatform.notifications.domain.NotificationRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller's own notifications. A user never sees, reads or counts anyone else's: every query is
 * bound to the current user, and someone else's notification id answers 404.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Notification> mine(boolean unreadOnly, Pageable pageable) {
        UUID me = currentUser();
        return unreadOnly ? notifications.findByRecipientUserIdAndReadAtIsNull(me, pageable)
                : notifications.findByRecipientUserId(me, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByRecipientUserIdAndReadAtIsNull(currentUser());
    }

    @Transactional
    public Notification markRead(UUID id) {
        Notification n = notifications.findByIdAndRecipientUserId(id, currentUser())
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.markRead(Instant.now(clock));
        return notifications.saveAndFlush(n);
    }

    @Transactional
    public int markAllRead() {
        return notifications.markAllRead(TenantContext.requireWorkspaceId(), currentUser(), Instant.now(clock));
    }

    /** Called by the event handlers inside the event's workspace scope. */
    @Transactional
    public void notify(Collection<UUID> recipients, Notification.Type type, String entityType, UUID entityId,
            UUID actorUserId, Map<String, Object> payload) {
        recipients.stream().filter(Objects::nonNull).filter(r -> !r.equals(actorUserId)).distinct()
                .map(r -> new Notification(r, type, entityType, entityId, actorUserId, payload))
                .forEach(notifications::save);
    }

    private static UUID currentUser() {
        return TenantContext.userId().orElseThrow(() -> ApiException.notFound("Notification"));
    }
}
