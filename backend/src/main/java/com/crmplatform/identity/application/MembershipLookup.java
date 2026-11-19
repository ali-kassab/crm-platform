package com.crmplatform.identity.application;

import com.crmplatform.identity.domain.MembershipRepository;
import com.crmplatform.identity.domain.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a user's current role in a workspace for every request. Cached for 5 minutes and
 * evicted on any membership change, so role changes apply immediately (AUTHZ §4).
 */
@Service
public class MembershipLookup {

    public static final String CACHE = "membership-role";

    private final MembershipRepository memberships;
    private final UserRepository users;

    public MembershipLookup(MembershipRepository memberships, UserRepository users) {
        this.memberships = memberships;
        this.users = users;
    }

    /** Returns {@code ROLE} for an active member who is an active user, otherwise {@code NONE}. */
    @Cacheable(cacheNames = CACHE, key = "#userId + ':' + #workspaceId")
    @Transactional(readOnly = true)
    public String roleOf(UUID userId, UUID workspaceId) {
        boolean userActive = users.findById(userId).map(u -> u.isActive()).orElse(false);
        if (!userActive) {
            return "NONE";
        }
        return memberships.findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(m -> m.isActive())
                .map(m -> m.getRole().name())
                .orElse("NONE");
    }

    @CacheEvict(cacheNames = CACHE, key = "#userId + ':' + #workspaceId")
    public void evict(UUID userId, UUID workspaceId) {
        // eviction only
    }

    public static Optional<String> asRole(String cached) {
        return "NONE".equals(cached) ? Optional.empty() : Optional.of(cached);
    }
}
