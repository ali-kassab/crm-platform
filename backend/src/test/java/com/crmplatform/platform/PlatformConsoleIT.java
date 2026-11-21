package com.crmplatform.platform;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.identity.application.AccessTokenService;
import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Platform admin console: authorization, company management (search, filter, paging, suspension),
 * per-company usage accuracy and isolation, privacy of aggregate endpoints, user administration,
 * the platform audit log and health.
 */
@IntegrationTest
class PlatformConsoleIT {

    private static final List<String> CONSOLE_READS = List.of("/api/v1/admin/dashboard", "/api/v1/admin/companies",
            "/api/v1/admin/usage", "/api/v1/admin/activity", "/api/v1/admin/health", "/api/v1/admin/audit-log",
            "/api/v1/admin/audit-log/actions", "/api/v1/admin/users");

    @Autowired
    MockMvc mvc;
    @Autowired
    AccessTokenService tokens;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void onlyPlatformAdminsReachTheConsoleAndTenantsStayIsolated() throws Exception {
        Session owner = Api.register(mvc);
        Session other = Api.register(mvc);
        for (String path : CONSOLE_READS) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(owner.auth())).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/admin/companies/" + owner.workspaceId()).with(owner.auth()))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/admin/users/" + other.userId() + "/deactivate"), "{\"reason\":\"x\"}")
                .with(owner.auth())).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/admin/workspaces/" + other.workspaceId() + "/subscription"),
                "{\"action\":\"SUSPEND\",\"note\":\"x\"}").with(owner.auth())).andExpect(status().isForbidden());

        // a tenant administrator cannot read another company's records
        String foreign = customer(other, "Foreign Co " + UUID.randomUUID());
        mvc.perform(get("/api/v1/contacts/" + foreign).with(owner.auth())).andExpect(status().isNotFound());

        RequestPostProcessor admin = admin();
        for (String path : CONSOLE_READS) {
            mvc.perform(get(path).with(admin)).andExpect(status().isOk());
        }
    }

    @Test
    void companiesAreSearchableFilterablePagedAndSuspensionKeepsEveryRecord() throws Exception {
        String tag = "Plat" + UUID.randomUUID().toString().substring(0, 6);
        Session alpha = Api.register(mvc, Api.uniqueEmail(), tag + " Alpha");
        Session beta = Api.register(mvc, Api.uniqueEmail(), tag + " Beta");
        RequestPostProcessor admin = admin();
        customer(alpha, tag + "-customer-1");
        customer(alpha, tag + "-customer-2");

        // search + sort + pagination
        mvc.perform(get("/api/v1/admin/companies").param("q", tag).param("size", "1").param("sort", "name,asc")
                .with(admin))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value(tag + " Alpha"));
        mvc.perform(get("/api/v1/admin/companies").param("q", tag).param("size", "1").param("page", "1")
                .param("sort", "name,asc").with(admin))
                .andExpect(jsonPath("$.content[0].name").value(tag + " Beta"));
        mvc.perform(get("/api/v1/admin/companies").param("status", "NOPE").with(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/companies").param("sort", "password").with(admin))
                .andExpect(status().isBadRequest());

        long suspendedBefore = dashboardNumber(admin, "$.companies.suspended");

        // suspend: read-only, nothing deleted, filterable, audited
        mvc.perform(json(post("/api/v1/admin/workspaces/" + alpha.workspaceId() + "/subscription"),
                "{\"action\":\"SUSPEND\",\"note\":\"Unpaid invoice\"}").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/v1/admin/companies").param("q", tag).param("status", "SUSPENDED").with(admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(alpha.workspaceId().toString()));
        mvc.perform(get("/api/v1/admin/companies").param("q", tag).param("status", "OPERATIONAL").with(admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(beta.workspaceId().toString()));
        assertThat(dashboardNumber(admin, "$.companies.suspended")).isEqualTo(suspendedBefore + 1);

        createCustomer(alpha, tag + "-blocked").andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));
        mvc.perform(get("/api/v1/contacts").with(alpha.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)); // data kept and still readable
        assertThat(jdbc.queryForObject("select count(*) from users u join memberships m on m.user_id = u.id "
                + "where m.workspace_id = ? and u.active and m.active", Long.class, alpha.workspaceId())).isEqualTo(1L);
        mvc.perform(get("/api/v1/admin/audit-log").param("action", "SUBSCRIPTION_SUSPEND").with(admin))
                .andExpect(jsonPath("$.content[*].changes.note", hasItem("Unpaid invoice")));

        // reactivate: writes work again
        mvc.perform(json(post("/api/v1/admin/workspaces/" + alpha.workspaceId() + "/subscription"),
                "{\"action\":\"REACTIVATE\"}").with(admin)).andExpect(status().isOk());
        createCustomer(alpha, tag + "-after").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/admin/companies/" + alpha.workspaceId()).with(admin))
                .andExpect(jsonPath("$.status").value("TRIAL"))
                .andExpect(jsonPath("$.usage.customers").value(3));
        mvc.perform(get("/api/v1/admin/companies/" + UUID.randomUUID()).with(admin)).andExpect(status().isNotFound());
    }

    @Test
    void usageIsAccuratePerCompanyAndAggregateEndpointsExposeNoBusinessData() throws Exception {
        String secret = "Confidential-" + UUID.randomUUID();
        Session a = Api.register(mvc);
        Session b = Api.register(mvc);
        customer(a, secret + "-1");
        customer(a, secret + "-2");
        customer(b, secret + "-3");
        upload(a);
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/contacts").with(a.auth())).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/contacts").with(b.auth())).andExpect(status().isOk());
        RequestPostProcessor admin = admin();

        String ca = body(mvc.perform(get("/api/v1/admin/companies/" + a.workspaceId()).with(admin)));
        String cb = body(mvc.perform(get("/api/v1/admin/companies/" + b.workspaceId()).with(admin)));
        assertThat(num(ca, "$.usage.customers")).isEqualTo(2);
        assertThat(num(cb, "$.usage.customers")).isEqualTo(1);
        assertThat(num(ca, "$.usage.activeUsers")).isEqualTo(1);
        assertThat(num(ca, "$.usage.files")).isEqualTo(1);
        assertThat(num(ca, "$.usage.storageBytes")).isPositive();
        assertThat(num(cb, "$.usage.files")).isZero();
        assertThat(num(cb, "$.usage.storageBytes")).isZero();
        // every authenticated workspace request is counted for its own workspace only
        assertThat(num(ca, "$.usage.apiRequests30d")).isGreaterThanOrEqualTo(5);
        assertThat(num(cb, "$.usage.apiRequests30d")).isBetween(1L, num(ca, "$.usage.apiRequests30d") - 1);
        assertThat(JsonPath.<String>read(ca, "$.ownerEmail")).isEqualTo(a.email());

        String usage = body(mvc.perform(get("/api/v1/admin/usage").param("sort", "customers,desc").with(admin)));
        assertThat(JsonPath.<List<Object>>read(usage, "$.metrics[?(@.key == 'apiRequests')].available"))
                .containsExactly(true);
        assertThat(JsonPath.<List<Object>>read(usage, "$.metrics[?(@.key == 'bandwidth')].available"))
                .containsExactly(false);
        assertThat(num(usage, "$.totals.customers")).isGreaterThanOrEqualTo(3);

        // no aggregate or listing endpoint leaks tenant business data
        for (String path : List.of("/api/v1/admin/dashboard", "/api/v1/admin/companies?size=100",
                "/api/v1/admin/companies/" + a.workspaceId(), "/api/v1/admin/usage?size=100",
                "/api/v1/admin/activity?limit=100", "/api/v1/admin/audit-log?size=100", "/api/v1/admin/users?size=100")) {
            String response = body(mvc.perform(get(path).with(admin)).andExpect(status().isOk()));
            assertThat(response).as(path).doesNotContain(secret).doesNotContain("+20223456789")
                    .doesNotContain("password").doesNotContain("passwordHash");
        }
    }

    @Test
    void deactivatingAUserBlocksSignInKeepsDataAndIsAudited() throws Exception {
        Session member = Api.register(mvc);
        customer(member, "Kept " + UUID.randomUUID());
        RequestPostProcessor admin = admin();

        mvc.perform(get("/api/v1/admin/users").param("q", member.email()).with(admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].platformAdmin").value(false))
                .andExpect(jsonPath("$.content[0].companies[0].id").value(member.workspaceId().toString()))
                .andExpect(jsonPath("$.content[0].companies[0].role").value("OWNER"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());

        mvc.perform(json(post("/api/v1/admin/users/" + member.userId() + "/deactivate"), "{}").with(admin))
                .andExpect(status().isBadRequest()); // a reason is required
        mvc.perform(json(post("/api/v1/admin/users/" + member.userId() + "/deactivate"),
                "{\"reason\":\"Abuse report\"}").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(json(post("/api/v1/auth/login"),
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.email(), Api.PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/contacts").with(member.auth())).andExpect(status().isForbidden()); // token loses its role
        mvc.perform(get("/api/v1/admin/users").param("q", member.email()).param("status", "INACTIVE").with(admin))
                .andExpect(jsonPath("$.totalElements").value(1));
        assertThat(jdbc.queryForObject("select count(*) from contacts where workspace_id = ?", Long.class,
                member.workspaceId())).isEqualTo(1L);

        mvc.perform(json(post("/api/v1/admin/users/" + member.userId() + "/activate"), "{}").with(admin))
                .andExpect(jsonPath("$.active").value(true));
        Api.login(mvc, member.email(), Api.PASSWORD);

        String audit = body(mvc.perform(get("/api/v1/admin/audit-log").param("entityType", "USER")
                .param("entityId", member.userId().toString()).with(admin)));
        assertThat(JsonPath.<List<String>>read(audit, "$.content[*].action"))
                .containsExactly("USER_ACTIVATED", "USER_DEACTIVATED");
        assertThat(JsonPath.<List<String>>read(audit, "$.content[*].changes.reason")).containsExactly("Abuse report");
        assertThat(audit).doesNotContain(Api.PASSWORD).doesNotContain("hash").doesNotContain("token");
    }

    @Test
    void platformAdminAccountsAreProtectedAndManageTheirOwnName() throws Exception {
        Session someone = Api.register(mvc);
        // promoted only for this test, then demoted again so PlatformAdminIT keeps its fresh-bootstrap path
        jdbc.update("update users set super_admin = true where id = ?", someone.userId());
        try {
            RequestPostProcessor self = bearer(tokens.issuePlatform(someone.userId(), Duration.ofMinutes(10), "en").value());
            mvc.perform(json(post("/api/v1/admin/users/" + someone.userId() + "/deactivate"),
                    "{\"reason\":\"test\"}").with(self))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLATFORM_ADMIN_PROTECTED"));
            mvc.perform(get("/api/v1/admin/users").param("kind", "PLATFORM_ADMIN").param("q", someone.email())
                    .with(self)).andExpect(jsonPath("$.content[0].platformAdmin").value(true));
            mvc.perform(json(patch("/api/v1/admin/account"), "{\"firstName\":\" \",\"lastName\":\"Owner\"}").with(self))
                    .andExpect(status().isBadRequest());
            mvc.perform(json(patch("/api/v1/admin/account"), "{\"firstName\":\"Nadia\",\"lastName\":\"Owner\"}")
                    .with(self))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Nadia"));
            mvc.perform(get("/api/v1/admin/account").with(self)).andExpect(jsonPath("$.lastName").value("Owner"));
            mvc.perform(get("/api/v1/admin/audit-log").param("action", "ADMIN_PROFILE_UPDATED")
                    .param("actorUserId", someone.userId().toString()).with(self))
                    .andExpect(jsonPath("$.content[0].changes.firstName.to").value("Nadia"))
                    .andExpect(jsonPath("$.content[0].actorEmail").value(someone.email()));
        } finally {
            jdbc.update("update users set super_admin = false where id = ?", someone.userId());
        }
    }

    @Test
    void healthComesFromLiveChecksAndRevealsNoInfrastructureSecrets() throws Exception {
        String health = body(mvc.perform(get("/api/v1/admin/health").with(admin())).andExpect(status().isOk()));
        Map<String, String> byName = new java.util.HashMap<>();
        List<Map<String, Object>> components = JsonPath.read(health, "$.components");
        components.forEach(c -> byName.put((String) c.get("name"), (String) c.get("status")));
        assertThat(byName).containsEntry("database", "UP").containsEntry("redis", "UP")
                .containsEntry("objectStorage", "UP").containsEntry("application", "UP");
        assertThat(JsonPath.<String>read(health, "$.overall")).isEqualTo("UP");
        assertThat(JsonPath.<String>read(health, "$.database.schemaVersion")).isNotBlank();
        assertThat(health).doesNotContain("jdbc:").doesNotContain("test-secret").doesNotContain("secret-key")
                .doesNotContain("localhost");
    }

    // ------------------------------------------------------------------ helpers

    /** A platform session for a throwaway (non-admin) user, as BillingIT does. */
    private RequestPostProcessor admin() throws Exception {
        Session someone = Api.register(mvc);
        return bearer(tokens.issuePlatform(someone.userId(), Duration.ofMinutes(10), "en").value());
    }

    private static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    private ResultActions createCustomer(Session s, String company) throws Exception {
        return mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","kind":"COMPANY","company":"%s","phone":"+20223456789"}
                """.formatted(company)).with(s.auth()));
    }

    private String customer(Session s, String company) throws Exception {
        return JsonPath.read(body(createCustomer(s, company).andExpect(status().isCreated())), "$.id");
    }

    private void upload(Session s) throws Exception {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        mvc.perform(multipart("/api/v1/files")
                .file(new MockMultipartFile("files", "photo.png", "image/png", png.toByteArray()))
                .param("kind", "IMAGE").param("purpose", "OTHER").with(s.auth()))
                .andExpect(status().isCreated());
    }

    private long dashboardNumber(RequestPostProcessor admin, String path) throws Exception {
        return num(body(mvc.perform(get("/api/v1/admin/dashboard").with(admin)).andExpect(status().isOk())), path);
    }

    private static long num(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
