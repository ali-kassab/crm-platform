package com.crmplatform.billing;

import static com.crmplatform.support.Api.json;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.billing.application.BillingIntegration.SubscriptionLifecycle;
import com.crmplatform.identity.application.AccessTokenService;
import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.support.Invites;
import com.crmplatform.support.RecordingEmailSender;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDate;
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

/** Subscriptions with manual payments, read-only expiry, admin actions and plan limits (R7). */
@IntegrationTest
class BillingIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingEmailSender emails;
    @Autowired
    AccessTokenService tokens;
    @Autowired
    SubscriptionLifecycle lifecycle;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void trialThenManualPaymentApprovedByThePlatformAdmin() throws Exception {
        Session owner = Api.register(mvc);
        Session member = Invites.invite(mvc, emails, owner, "MEMBER");
        RequestPostProcessor admin = admin();

        mvc.perform(get("/api/v1/subscription").with(member.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAL"))
                .andExpect(jsonPath("$.plan.code").value("TEAM"))
                .andExpect(jsonPath("$.readOnly").value(false))
                .andExpect(jsonPath("$.daysLeft").value(greaterThanOrEqualTo(13)))
                .andExpect(jsonPath("$.usage.users").value(2));
        mvc.perform(get("/api/v1/subscription/plans").with(owner.auth()))
                .andExpect(jsonPath("$[*].code").value(hasItems("SOLO", "TEAM", "BUSINESS")));

        String method = id(mvc.perform(json(post("/api/v1/admin/payment-methods"), """
                {"kind":"VODAFONE_CASH","labelAr":"فودافون كاش","labelEn":"Vodafone Cash","account":"01000000000",
                 "instructionsAr":"حوّل المبلغ ثم ارفع صورة الإيصال"}
                """).with(admin)).andExpect(status().isCreated()), "$.method.id");
        mvc.perform(get("/api/v1/subscription/payment-methods").with(owner.auth()))
                .andExpect(jsonPath("$[*].id").value(hasItem(method)));
        String team = planId(owner, "TEAM");
        String solo = planId(owner, "SOLO");

        String receipt = receipt(owner);
        mvc.perform(json(post("/api/v1/subscription/requests"), request(team, method, receipt)).with(member.auth()))
                .andExpect(status().isForbidden()); // owners/admins only
        mvc.perform(json(post("/api/v1/subscription/requests"), request(solo, method, receipt)).with(owner.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_TOO_SMALL")); // 2 users > 1
        String req = id(mvc.perform(json(post("/api/v1/subscription/requests"), request(team, method, receipt))
                .with(owner.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value("900.00")), "$.id");
        mvc.perform(json(post("/api/v1/subscription/requests"), request(team, method, receipt(owner)))
                .with(owner.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ALREADY_PENDING"));
        mvc.perform(get("/api/v1/subscription").with(owner.auth()))
                .andExpect(jsonPath("$.pendingRequest.id").value(req));

        // the tenant cannot review its own request
        mvc.perform(post("/api/v1/admin/subscription-requests/" + req + "/approve").with(owner.auth()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/subscription-requests?status=PENDING&size=100").with(admin))
                .andExpect(jsonPath("$.content[*].request.id").value(hasItem(req)));
        mvc.perform(get("/api/v1/admin/subscription-requests/" + req + "/receipt-url").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("http")));

        mvc.perform(json(post("/api/v1/admin/subscription-requests/" + req + "/approve"), "{\"note\":\"تم التحقق\"}")
                .with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("APPROVED"));
        mvc.perform(post("/api/v1/admin/subscription-requests/" + req + "/approve").with(admin))
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_PENDING"));
        mvc.perform(get("/api/v1/subscription").with(owner.auth()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.billingPeriod").value("MONTHLY"))
                .andExpect(jsonPath("$.daysLeft").value(greaterThanOrEqualTo(27)))
                .andExpect(jsonPath("$.pendingRequest").doesNotExist());
        mvc.perform(get("/api/v1/notifications").with(owner.auth()))
                .andExpect(jsonPath("$.content[*].type").value(hasItem("SUBSCRIPTION_REQUEST_APPROVED")));
        mvc.perform(get("/api/v1/admin/workspaces/" + owner.workspaceId()).with(admin))
                .andExpect(jsonPath("$.subscription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.history[*].type")
                        .value(hasItems("TRIAL_STARTED", "REQUEST_SUBMITTED", "PAYMENT_APPROVED")));
        mvc.perform(get("/api/v1/admin/workspaces?q=" + owner.workspaceId().toString().substring(0, 4)).with(admin))
                .andExpect(status().isOk());
    }

    @Test
    void anEndedSubscriptionIsReadOnlyAndNothingIsDeleted() throws Exception {
        Session owner = Api.register(mvc);
        RequestPostProcessor admin = admin();
        String customer = customer(owner, "شركة النور").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String customerId = JsonPath.read(customer, "$.id");

        // the period ended 2 days ago: grace — still writable, with a warning date
        moveEnd(owner, "now() - interval '2 days'");
        mvc.perform(get("/api/v1/subscription").with(owner.auth()))
                .andExpect(jsonPath("$.status").value("GRACE"))
                .andExpect(jsonPath("$.graceEndsAt").value(notNullValue()))
                .andExpect(jsonPath("$.readOnly").value(false));
        customer(owner, "عميل أثناء المهلة").andExpect(status().isCreated());

        // grace over: read-only, data intact
        moveEnd(owner, "now() - interval '8 days'");
        lifecycle.runOnce();
        lifecycle.runOnce();
        mvc.perform(get("/api/v1/subscription").with(owner.auth()))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.readOnly").value(true));
        mvc.perform(get("/api/v1/contacts/" + customerId).with(owner.auth())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/contacts").with(owner.auth())).andExpect(jsonPath("$.totalElements").value(2));
        customer(owner, "عميل جديد")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));
        mvc.perform(json(patch("/api/v1/contacts/" + customerId), "{\"company\":\"x\"}").with(owner.auth()))
                .andExpect(status().isPaymentRequired());
        receipt(owner); // renewing stays possible
        mvc.perform(get("/api/v1/notifications").with(owner.auth()))
                .andExpect(jsonPath("$.content[?(@.type == 'SUBSCRIPTION_EXPIRED')]", hasSize(1)));

        // admin goodwill extension → writable again
        mvc.perform(json(post("/api/v1/admin/workspaces/" + owner.workspaceId() + "/subscription"),
                "{\"action\":\"EXTEND\",\"days\":30,\"note\":\"تعويض\"}").with(admin))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        customer(owner, "عميل بعد التمديد").andExpect(status().isCreated());

        // suspension: read-only until reactivated; payment requests refused meanwhile
        mvc.perform(json(post("/api/v1/admin/workspaces/" + owner.workspaceId() + "/subscription"),
                "{\"action\":\"SUSPEND\"}").with(admin)).andExpect(status().isBadRequest()); // note required
        mvc.perform(json(post("/api/v1/admin/workspaces/" + owner.workspaceId() + "/subscription"),
                "{\"action\":\"SUSPEND\",\"note\":\"مخالفة الشروط\"}").with(admin))
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.readOnly").value(true));
        customer(owner, "x").andExpect(status().isPaymentRequired());
        mvc.perform(get("/api/v1/contacts").with(owner.auth())).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(json(post("/api/v1/admin/workspaces/" + owner.workspaceId() + "/subscription"),
                "{\"action\":\"REACTIVATE\"}").with(admin))
                .andExpect(jsonPath("$.status").value("TRIAL"));
        customer(owner, "عميل بعد إعادة التفعيل").andExpect(status().isCreated());

        // "ends soon" reminder, sent once
        moveEnd(owner, "now() + interval '2 days'");
        lifecycle.runOnce();
        lifecycle.runOnce();
        mvc.perform(get("/api/v1/notifications").with(owner.auth()))
                .andExpect(jsonPath("$.content[?(@.type == 'SUBSCRIPTION_ENDING')]", hasSize(1)));
    }

    @Test
    void planLimitsAreEnforcedAndOnlyThePlatformAdminManagesPlans() throws Exception {
        Session owner = Api.register(mvc);
        RequestPostProcessor admin = admin();
        mvc.perform(get("/api/v1/admin/plans").with(owner.auth())).andExpect(status().isForbidden());

        String code = ("TINY_" + UUID.randomUUID().toString().substring(0, 6)).toUpperCase(java.util.Locale.ROOT);
        String tiny = id(mvc.perform(json(post("/api/v1/admin/plans"), """
                {"code":"%s","nameAr":"تجريبية صغيرة","nameEn":"Tiny","priceMonthly":"10","priceYearly":"100",
                 "maxUsers":1,"maxJobsPerMonth":1,"active":false}
                """.formatted(code)).with(admin)).andExpect(status().isCreated()), "$.plan.id");
        mvc.perform(json(post("/api/v1/admin/plans"), """
                {"code":"%s","nameAr":"x","nameEn":"x","priceMonthly":"1","priceYearly":"1"}
                """.formatted(code)).with(admin)).andExpect(jsonPath("$.code").value("PLAN_CODE_TAKEN"));
        mvc.perform(json(patch("/api/v1/admin/plans/" + tiny), "{\"priceMonthly\":\"12.5\"}").with(admin))
                .andExpect(jsonPath("$.plan.priceMonthly").value("12.50"))
                .andExpect(jsonPath("$.plan.limits.maxUsers").value(1)); // limits untouched by a price edit
        mvc.perform(json(post("/api/v1/admin/workspaces/" + owner.workspaceId() + "/subscription"),
                "{\"action\":\"CHANGE_PLAN\",\"planId\":\"%s\"}".formatted(tiny)).with(admin))
                .andExpect(jsonPath("$.plan.code").value(code));

        mvc.perform(json(post("/api/v1/workspace/invitations"),
                "{\"email\":\"%s\",\"role\":\"MEMBER\"}".formatted(Api.uniqueEmail())).with(owner.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
        String contact = JsonPath.read(customer(owner, "Limit Co").andReturn().getResponse().getContentAsString(),
                "$.id");
        String site = JsonPath.read(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"COMMERCIAL","name":"HQ","address":{"line1":"x","city":"Cairo","country":"EG"}}
                """.formatted(contact)).with(owner.auth())).andReturn().getResponse().getContentAsString(), "$.id");
        String job = "{\"contactId\":\"%s\",\"locationId\":\"%s\",\"title\":\"صيانة\"}".formatted(contact, site);
        mvc.perform(json(post("/api/v1/jobs"), job).with(owner.auth())).andExpect(status().isCreated());
        mvc.perform(json(post("/api/v1/jobs"), job).with(owner.auth()).header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.limit").value("JOBS_PER_MONTH"));
        // an inactive plan is not offered to tenants
        mvc.perform(get("/api/v1/subscription/plans").with(owner.auth()))
                .andExpect(jsonPath("$[?(@.code == '%s')]".formatted(code), hasSize(0)));
    }

    // ------------------------------------------------------------------ helpers

    /** A platform-admin session for a throwaway user (the bootstrap itself is covered by PlatformAdminIT). */
    private RequestPostProcessor admin() throws Exception {
        Session someone = Api.register(mvc);
        String token = tokens.issuePlatform(someone.userId(), Duration.ofMinutes(10), "en").value();
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    private void moveEnd(Session s, String sqlInstant) {
        jdbc.update("update subscriptions set current_period_end = " + sqlInstant + " where workspace_id = ?",
                s.workspaceId());
    }

    private String planId(Session s, String code) throws Exception {
        String body = mvc.perform(get("/api/v1/subscription/plans").with(s.auth())).andReturn().getResponse()
                .getContentAsString();
        return ((java.util.List<String>) JsonPath.read(body, "$[?(@.code == '" + code + "')].id")).get(0);
    }

    private String request(String plan, String method, String receipt) {
        return """
                {"planId":"%s","billingPeriod":"MONTHLY","paymentMethodId":"%s","payerReference":"01012345678",
                 "paidOn":"%s","receiptFileId":"%s"}
                """.formatted(plan, method, LocalDate.now(), receipt);
    }

    private String receipt(Session s) throws Exception {
        BufferedImage image = new BufferedImage(400, 600, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return id(mvc.perform(multipart("/api/v1/files")
                .file(new MockMultipartFile("files", "receipt.png", "image/png", png.toByteArray()))
                .param("kind", "IMAGE").param("purpose", "RECEIPT").with(s.auth()))
                .andExpect(status().isCreated()), "$.files[0].id");
    }

    private ResultActions customer(Session s, String company) throws Exception {
        return mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","kind":"COMPANY","company":"%s","phone":"+20223456789"}
                """.formatted(company)).with(s.auth()));
    }

    private static String id(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
