package com.crmplatform.identity.web;

import com.crmplatform.identity.application.IdentityProperties;
import com.crmplatform.shared.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token travels only in an HttpOnly, SameSite=Strict cookie scoped to the auth path.
 * Cookie-authenticated endpoints additionally require {@code X-Requested-With} (CSRF defence).
 */
@Component
public class RefreshCookies {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/v1/auth";

    private final IdentityProperties properties;
    private final Clock clock;

    public RefreshCookies(IdentityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String set(String token, Instant expiresAt) {
        long maxAge = Math.max(0, Duration.between(Instant.now(clock), expiresAt).toSeconds());
        return ResponseCookie.from(NAME, token).httpOnly(true).secure(properties.secureCookies()).sameSite("Strict")
                .path(PATH).maxAge(maxAge).build().toString();
    }

    public String clear() {
        return ResponseCookie.from(NAME, "").httpOnly(true).secure(properties.secureCookies()).sameSite("Strict")
                .path(PATH).maxAge(0).build().toString();
    }

    public static void requireXhr(HttpServletRequest request) {
        if (!"XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            throw ApiException.forbidden("Missing X-Requested-With header");
        }
    }

    public static String headerName() {
        return HttpHeaders.SET_COOKIE;
    }
}
