package com.crmplatform.identity.application;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.identity.domain.PlatformMarkerRepository;
import com.crmplatform.identity.domain.UserRepository;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;

/** First platform admin bootstrap, admin sign-in, account changes and their audit trail. */
@IntegrationTest
class PlatformAdminIT {

    private static final String ADMIN_PASSWORD = "Owner-Secret-2026-x";

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    PlatformMarkerRepository markers;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    PasswordPolicy policy;
    @Autowired
    AuditTrail audit;
    @Autowired
    PlatformTransactionManager tm;
    @Autowired
    Clock clock;

    @Test
    void bootstrapLoginAccountChangesAndAudit() throws Exception {
        // ---- bootstrap guards: needs a fresh database (skipped if Testcontainers reuse kept an old one)
        org.junit.jupiter.api.Assumptions.assumeFalse(users.existsBySuperAdminTrue()
                || markers.existsById(com.crmplatform.identity.domain.PlatformMarker.ADMIN_BOOTSTRAPPED),
                "requires a database without a platform admin");
        assertThat(bootstrap(null, null)).isEqualTo(PlatformAdminBootstrap.Outcome.NOT_CONFIGURED);
        Session existing = Api.register(mvc);
        assertThat(bootstrap(existing.email(), ADMIN_PASSWORD)).isEqualTo(PlatformAdminBootstrap.Outcome.EMAIL_TAKEN);
        assertThat(users.findByEmail(existing.email()).orElseThrow().isSuperAdmin()).isFalse(); // never promoted
        String adminEmail = Api.uniqueEmail();
        assertThat(bootstrap(adminEmail, "password1")).isEqualTo(PlatformAdminBootstrap.Outcome.WEAK_PASSWORD);

        assertThat(bootstrap(adminEmail, ADMIN_PASSWORD)).isEqualTo(PlatformAdminBootstrap.Outcome.CREATED);
        assertThat(users.findByEmail(adminEmail).orElseThrow().isSuperAdmin()).isTrue();
        assertThat(users.findByEmail(adminEmail).orElseThrow().getPasswordHash()).doesNotContain(ADMIN_PASSWORD);
        // cannot be reused, even with different credentials
        assertThat(bootstrap(Api.uniqueEmail(), ADMIN_PASSWORD + "y"))
                .isEqualTo(PlatformAdminBootstrap.Outcome.ALREADY_DONE);

        // ---- sign-in
        mvc.perform(json(post("/api/v1/admin/auth/login"), login(adminEmail, "wrong-password-123")))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/admin/auth/login"), login(existing.email(), Api.PASSWORD)))
                .andExpect(status().isUnauthorized()); // a normal user is not an admin
        String token = adminToken(adminEmail, ADMIN_PASSWORD);
        mvc.perform(get("/api/v1/admin/stats").with(bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/account").with(bearer(token)))
                .andExpect(jsonPath("$.email").value(adminEmail))
                .andExpect(jsonPath("$.superAdmin").value(true));
        // a platform session reaches no tenant data
        mvc.perform(get("/api/v1/contacts").with(bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_WORKSPACE"));
        // a tenant owner reaches no platform endpoint
        mvc.perform(get("/api/v1/admin/stats").with(existing.auth())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/audit").with(existing.auth())).andExpect(status().isForbidden());

        // ---- password change
        String newPassword = "Even-Longer-Secret-77";
        mvc.perform(json(post("/api/v1/admin/account/password"),
                "{\"currentPassword\":\"%s\",\"newPassword\":\"Short-1a\"}".formatted(ADMIN_PASSWORD)).with(bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/admin/account/password"),
                "{\"currentPassword\":\"nope\",\"newPassword\":\"%s\"}".formatted(newPassword)).with(bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
        mvc.perform(json(post("/api/v1/auth/password/change"),
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(ADMIN_PASSWORD, newPassword))
                .with(bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USE_ADMIN_ACCOUNT_ENDPOINT"));
        mvc.perform(json(post("/api/v1/admin/account/password"),
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(ADMIN_PASSWORD, newPassword))
                .with(bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/api/v1/admin/auth/login"), login(adminEmail, ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized());

        // ---- email (username) change
        String newEmail = Api.uniqueEmail();
        String token2 = adminToken(adminEmail, newPassword);
        mvc.perform(json(post("/api/v1/admin/account/email"),
                "{\"currentPassword\":\"%s\",\"newEmail\":\"%s\"}".formatted(newPassword, existing.email()))
                .with(bearer(token2)))
                .andExpect(status().isConflict());
        mvc.perform(json(post("/api/v1/admin/account/email"),
                "{\"currentPassword\":\"%s\",\"newEmail\":\"%s\"}".formatted(newPassword, newEmail)).with(bearer(token2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
        String token3 = adminToken(newEmail, newPassword);

        // ---- every sensitive change is in the platform audit log, without secrets
        String auditJson = mvc.perform(get("/api/v1/admin/audit?size=50").with(bearer(token3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action", hasItem("ADMIN_BOOTSTRAPPED")))
                .andExpect(jsonPath("$.content[*].action", hasItem("ADMIN_LOGIN")))
                .andExpect(jsonPath("$.content[*].action", hasItem("ADMIN_LOGIN_FAILED")))
                .andExpect(jsonPath("$.content[*].action", hasItem("ADMIN_PASSWORD_CHANGED")))
                .andExpect(jsonPath("$.content[*].action", hasItem("ADMIN_EMAIL_CHANGED")))
                .andReturn().getResponse().getContentAsString();
        assertThat(auditJson).doesNotContain(ADMIN_PASSWORD).doesNotContain(newPassword);
        // platform entries never leak into a workspace's audit log
        mvc.perform(get("/api/v1/audit?entityType=PLATFORM_ADMIN").with(existing.auth()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private PlatformAdminBootstrap.Outcome bootstrap(String email, String password) {
        var props = new PlatformAdminProperties(new PlatformAdminProperties.Bootstrap(email, password, "Ali", "Owner"),
                java.time.Duration.ofMinutes(10));
        return new PlatformAdminBootstrap(props, users, markers, encoder, policy, audit, tm, clock).bootstrap();
    }

    private String adminToken(String email, String password) throws Exception {
        String body = mvc.perform(json(post("/api/v1/admin/auth/login"), login(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.superAdmin").value(true))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String login(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }
}
