package com.crmplatform.notifications.application;

import com.crmplatform.notifications.NotificationEvents.NotificationsCreated;
import com.crmplatform.notifications.domain.Notification;
import com.crmplatform.notifications.domain.NotificationRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

    public NotificationService(NotificationRepository notifications, Clock clock, ApplicationEventPublisher events) {
        this.notifications = notifications;
        this.clock = clock;
        this.events = events;
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
        Set<UUID> to = recipients.stream().filter(Objects::nonNull).filter(r -> !r.equals(actorUserId))
                .collect(Collectors.toSet());
        to.forEach(r -> notifications.save(new Notification(r, type, entityType, entityId, actorUserId, payload)));
        if (!to.isEmpty()) { // realtime hint after commit (ADR-0015)
            events.publishEvent(new NotificationsCreated(TenantContext.requireWorkspaceId(), to));
        }
    }

    private static UUID currentUser() {
        return TenantContext.userId().orElseThrow(() -> ApiException.notFound("Notification"));
    }
}
