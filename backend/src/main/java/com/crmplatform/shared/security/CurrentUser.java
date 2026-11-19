package com.crmplatform.shared.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Static accessor for the authenticated caller of the current request. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser require() {
        return get().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }

    public static UUID id() {
        return require().userId();
    }
}
