package com.crmplatform.identity.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Token, lockout and invitation settings (AUTHENTICATION_AND_AUTHORIZATION.md §5). */
@Validated
@ConfigurationProperties("app.security")
public record IdentityProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String jwtSecret,
        @DefaultValue("crm-platform") String issuer,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("14d") Duration refreshTokenTtl,
        @DefaultValue("60d") Duration refreshTokenAbsoluteTtl,
        @DefaultValue("10s") Duration refreshReuseGrace,
        @DefaultValue("true") boolean secureCookies,
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15m") Duration lockoutDuration,
        @DefaultValue("7d") Duration invitationTtl,
        @DefaultValue("30m") Duration passwordResetTtl) {
}
