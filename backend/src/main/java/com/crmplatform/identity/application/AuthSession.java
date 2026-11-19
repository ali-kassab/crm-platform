package com.crmplatform.identity.application;

import com.crmplatform.identity.domain.Role;
import com.crmplatform.identity.domain.User;
import com.crmplatform.identity.domain.Workspace;
import java.time.Instant;
import java.util.Set;

/** Result of any token-issuing flow; the web layer turns it into a body plus refresh cookie. */
public record AuthSession(
        String accessToken,
        long expiresIn,
        String refreshToken,
        Instant refreshExpiresAt,
        User user,
        Workspace workspace,
        Role role,
        Set<String> permissions) {
}
