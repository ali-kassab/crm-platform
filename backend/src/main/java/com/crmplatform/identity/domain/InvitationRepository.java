package com.crmplatform.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("select i from Invitation i where i.workspaceId = :workspaceId and i.email = :email "
            + "and i.acceptedAt is null and i.revokedAt is null")
    Optional<Invitation> findPending(UUID workspaceId, String email);

    @Query("select i from Invitation i where i.workspaceId = :workspaceId and i.acceptedAt is null "
            + "and i.revokedAt is null order by i.createdAt desc")
    List<Invitation> findAllPending(UUID workspaceId);
}
