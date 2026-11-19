package com.crmplatform.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Initial platform-admin bootstrap. Values come from the environment ({@code PLATFORM_ADMIN_EMAIL},
 * {@code PLATFORM_ADMIN_PASSWORD}, …) and are used at most once; they should be removed from the
 * environment after the first successful start.
 */
@ConfigurationProperties("app.platform-admin")
public record PlatformAdminProperties(
        Bootstrap bootstrap,
        @DefaultValue("10m") Duration sessionTtl) {

    public PlatformAdminProperties {
        bootstrap = bootstrap == null ? new Bootstrap(null, null, "Platform", "Admin") : bootstrap;
    }

    public record Bootstrap(String email, String password, @DefaultValue("Platform") String firstName,
            @DefaultValue("Admin") String lastName) {

        boolean configured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
