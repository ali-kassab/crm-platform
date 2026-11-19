package com.crmplatform.notifications.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query is scoped to one recipient (and, through the tenant, one workspace). */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientUserId(UUID recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadAtIsNull(UUID recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    /** The workspace is repeated explicitly so a bulk update can never cross workspaces. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now, n.updatedAt = :now, n.version = n.version + 1 "
            + "where n.workspaceId = :workspaceId and n.recipientUserId = :userId and n.readAt is null "
            + "and n.deletedAt is null")
    int markAllRead(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId, @Param("now") Instant now);
}
