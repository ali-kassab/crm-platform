package com.crmplatform.identity.application;

import com.crmplatform.identity.MemberDeactivated;
import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.identity.domain.Membership;
import com.crmplatform.identity.domain.MembershipRepository;
import com.crmplatform.identity.domain.Role;
import com.crmplatform.identity.domain.User;
import com.crmplatform.identity.domain.UserRepository;
import com.crmplatform.identity.domain.Workspace;
import com.crmplatform.identity.domain.WorkspaceRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.tenant.TenantContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Workspace membership administration (FR-IAM-11) and the public {@link WorkspaceMembers} API. */
@Service
public class MemberService implements WorkspaceMembers {

    private final MembershipRepository memberships;
    private final UserRepository users;
    private final MembershipLookup lookup;
    private final RefreshTokenService refreshTokens;
    private final ApplicationEventPublisher events;
    private final WorkspaceRepository workspaces;

    public MemberService(MembershipRepository memberships, UserRepository users, MembershipLookup lookup,
            RefreshTokenService refreshTokens, ApplicationEventPublisher events, WorkspaceRepository workspaces) {
        this.memberships = memberships;
        this.users = users;
        this.lookup = lookup;
        this.refreshTokens = refreshTokens;
        this.events = events;
        this.workspaces = workspaces;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceProfile currentWorkspace() {
        Workspace w = workspaces.findById(TenantContext.requireWorkspaceId())
                .orElseThrow(() -> ApiException.notFound("Workspace"));
        return new WorkspaceProfile(w.getId(), w.getName(), w.getDefaultLanguage(), w.getCurrency(), w.getTimeZone());
    }

    @Transactional(readOnly = true)
    public Page<MemberView> list(UUID workspaceId, Pageable pageable) {
        Page<Membership> page = memberships.findByWorkspaceId(workspaceId, pageable);
        Map<UUID, User> byId = new HashMap<>();
        users.findAllById(page.map(Membership::getUserId).getContent()).forEach(u -> byId.put(u.getId(), u));
        return page.map(m -> new MemberView(m, byId.get(m.getUserId())));
    }

    /** Changes role and/or active flag, enforcing AZ-03..AZ-05. */
    @Transactional
    public MemberView update(UUID workspaceId, UUID actorId, Role actorRole, UUID userId, Role newRole, Boolean active) {
        if (actorId.equals(userId)) {
            throw ApiException.forbidden("You cannot change your own membership");
        }
        Membership membership = memberships.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.notFound("Member"));
        boolean targetIsOwner = membership.getRole() == Role.OWNER;
        if (targetIsOwner && actorRole != Role.OWNER) {
            throw ApiException.forbidden("Only an owner can change another owner");
        }
        if (newRole != null && newRole != membership.getRole()) {
            if (!actorRole.canGrant(newRole)) {
                throw ApiException.forbidden("You cannot grant this role");
            }
            if (targetIsOwner) {
                ensureNotLastOwner(workspaceId);
            }
            membership.changeRole(newRole);
        }
        if (active != null && active != membership.isActive()) {
            if (!active && membership.getRole() == Role.OWNER) {
                ensureNotLastOwner(workspaceId);
            }
            membership.setActive(active);
            if (!active) {
                refreshTokens.revokeAllForMembership(userId, workspaceId);
                events.publishEvent(new MemberDeactivated(workspaceId, userId));
            }
        }
        lookup.evict(userId, workspaceId);
        return new MemberView(membership, users.findById(userId).orElse(null));
    }

    @Transactional
    public void remove(UUID workspaceId, UUID actorId, Role actorRole, UUID userId) {
        if (actorId.equals(userId)) {
            throw ApiException.forbidden("You cannot remove yourself");
        }
        Membership membership = memberships.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.notFound("Member"));
        if (membership.getRole() == Role.OWNER) {
            if (actorRole != Role.OWNER) {
                throw ApiException.forbidden("Only an owner can remove another owner");
            }
            ensureNotLastOwner(workspaceId);
        }
        memberships.delete(membership);
        refreshTokens.revokeAllForMembership(userId, workspaceId);
        lookup.evict(userId, workspaceId);
        events.publishEvent(new MemberDeactivated(workspaceId, userId));
    }

    private void ensureNotLastOwner(UUID workspaceId) {
        if (memberships.countActiveOwners(workspaceId) <= 1) {
            throw ApiException.conflict("LAST_OWNER", "A workspace must keep at least one owner");
        }
    }

    // ------------------------------------------------------------ WorkspaceMembers (public API)

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveMember(UUID userId) {
        UUID workspaceId = TenantContext.requireWorkspaceId();
        return userId != null && memberships.existsByWorkspaceIdAndUserIdAndActiveTrue(workspaceId, userId)
                && users.findById(userId).map(User::isActive).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberSummary> find(UUID userId) {
        return Optional.ofNullable(findAll(java.util.List.of(userId)).get(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MemberSummary> findAll(Collection<UUID> userIds) {
        UUID workspaceId = TenantContext.requireWorkspaceId();
        Map<UUID, MemberSummary> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        for (User u : users.findAllById(userIds)) {
            memberships.findByWorkspaceIdAndUserId(workspaceId, u.getId()).ifPresent(m -> result.put(u.getId(),
                    new MemberSummary(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), m.getRole().name())));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Set<UUID> managers() {
        UUID workspaceId = TenantContext.requireWorkspaceId();
        java.util.Set<UUID> ids = new java.util.HashSet<>(memberships.findActiveManagerIds(workspaceId));
        users.findAllById(ids).stream().filter(u -> !u.isActive()).forEach(u -> ids.remove(u.getId()));
        return ids;
    }

    public record MemberView(Membership membership, User user) {
    }
}
