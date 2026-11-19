package com.crmplatform.identity.application;

import com.crmplatform.identity.domain.Membership;
import com.crmplatform.identity.domain.MembershipRepository;
import com.crmplatform.identity.domain.User;
import com.crmplatform.identity.domain.UserRepository;
import com.crmplatform.identity.domain.Workspace;
import com.crmplatform.identity.domain.WorkspaceRepository;
import com.crmplatform.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Workspace settings, profile and platform statistics (FR-IAM-08, 12, 13). */
@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;

    public WorkspaceService(WorkspaceRepository workspaces, MembershipRepository memberships, UserRepository users,
            RefreshTokenService refreshTokens, Clock clock) {
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Workspace get(UUID workspaceId) {
        return workspaces.findById(workspaceId).orElseThrow(() -> ApiException.notFound("Workspace"));
    }

    @Transactional
    public Workspace rename(UUID workspaceId, String name) {
        Workspace workspace = get(workspaceId);
        workspace.rename(name);
        return workspace;
    }

    @Transactional
    public void delete(UUID workspaceId, String confirmName) {
        Workspace workspace = get(workspaceId);
        if (confirmName == null || !confirmName.trim().equals(workspace.getName())) {
            throw ApiException.badRequest("CONFIRMATION_MISMATCH", "Type the workspace name exactly to confirm");
        }
        Instant now = Instant.now(clock);
        workspace.delete(now);
        memberships.findByWorkspaceId(workspaceId, org.springframework.data.domain.Pageable.unpaged())
                .forEach(m -> {
                    m.setActive(false);
                    refreshTokens.revokeAllForMembership(m.getUserId(), workspaceId);
                });
    }

    @Transactional(readOnly = true)
    public Profile profile(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        List<Membership> active = memberships.findByUserIdAndActiveTrue(userId);
        Map<UUID, Workspace> byId = workspaces.findAllById(active.stream().map(Membership::getWorkspaceId).toList())
                .stream().collect(Collectors.toMap(Workspace::getId, Function.identity()));
        List<Profile.WorkspaceMembership> list = active.stream()
                .filter(m -> byId.containsKey(m.getWorkspaceId()))
                .map(m -> new Profile.WorkspaceMembership(byId.get(m.getWorkspaceId()), m.getRole().name()))
                .toList();
        return new Profile(user, list);
    }

    @Transactional
    public User updateProfile(UUID userId, String firstName, String lastName, String jobTitle, String phone,
            UUID avatarFileId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        user.updateProfile(firstName, lastName, jobTitle, phone, avatarFileId);
        return user;
    }

    @Transactional(readOnly = true)
    public PlatformStats platformStats() {
        return new PlatformStats(workspaces.count(), users.count(), memberships.count());
    }

    public record Profile(User user, List<WorkspaceMembership> workspaces) {
        public record WorkspaceMembership(Workspace workspace, String role) {
        }
    }

    public record PlatformStats(long workspaces, long users, long memberships) {
    }
}
