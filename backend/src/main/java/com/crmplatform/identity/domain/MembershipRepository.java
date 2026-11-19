package com.crmplatform.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<Membership> findByUserIdAndActiveTrue(UUID userId);

    Page<Membership> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    @Query("select count(m) from Membership m where m.workspaceId = :workspaceId and m.role = 'OWNER' and m.active = true")
    long countActiveOwners(UUID workspaceId);

    boolean existsByWorkspaceIdAndUserIdAndActiveTrue(UUID workspaceId, UUID userId);
}
