package com.crmplatform.shared.i18n;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.support.Invites;
import com.crmplatform.support.RecordingEmailSender;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Arabic-first defaults, workspace/user language settings, localized errors and Arabic data. */
@IntegrationTest
class LocalizationIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingEmailSender emails;

    @Test
    void newWorkspacesAreArabicFirst() throws Exception {
        mvc.perform(json(post("/api/v1/auth/register"), register(Api.uniqueEmail(), "شركة النور للتشطيبات", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.language").value("ar"))
                .andExpect(jsonPath("$.workspace.name").value("شركة النور للتشطيبات"))
                .andExpect(jsonPath("$.workspace.defaultLanguage").value("ar"))
                .andExpect(jsonPath("$.workspace.locale").value("ar-EG"))
                .andExpect(jsonPath("$.workspace.currency").value("EGP"))
                .andExpect(jsonPath("$.workspace.timeZone").value("Africa/Cairo"))
                .andExpect(jsonPath("$.workspace.slug").isNotEmpty());

        mvc.perform(json(post("/api/v1/auth/register"), register(Api.uniqueEmail(), "Cool Air", "en", "MAINTENANCE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.workspace.locale").value("en-EG"))
                .andExpect(jsonPath("$.workspace.currency").value("EGP"))
                .andExpect(jsonPath("$.workspace.sector").value("MAINTENANCE"));
    }

    @Test
    void errorsFollowTheRequestLanguageAndKeepTheirCode() throws Exception {
        Session s = Api.register(mvc); // default workspace language: ar
        String missing = "/api/v1/contacts/" + UUID.randomUUID();

        mvc.perform(get(missing).with(s.auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("غير موجود"))
                .andExpect(jsonPath("$.detail").value("العميل غير موجود"));
        mvc.perform(get(missing).header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9").with(s.auth()))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Contact not found"));

        // exact translation of a specific business rule message
        mvc.perform(json(post("/api/v1/tasks"), """
                {"projectId":"%s","jobId":"%s","title":"x","description":"","startDate":"2026-05-01","endDate":"2026-05-02"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID())).with(s.auth()))
                .andExpect(jsonPath("$.code").value("TASK_PARENT_AMBIGUOUS"))
                .andExpect(jsonPath("$.detail").value("تتبع المهمة مشروعًا أو أمر عمل، وليس الاثنين معًا"));

        // field validation
        mvc.perform(json(post("/api/v1/contacts"), "{\"type\":\"CLIENT\",\"email\":\"%s\"}".formatted(Api.uniqueEmail()))
                .with(s.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.title").value("بيانات غير صالحة"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[0].message").value("هذا الحقل مطلوب"));

        // errors written by security filters are localized too
        mvc.perform(get("/api/v1/contacts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("يلزم تسجيل الدخول"));
        mvc.perform(get("/api/v1/contacts").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void userLanguagePreferenceOverridesTheWorkspaceDefault() throws Exception {
        Session s = Api.register(mvc);
        mvc.perform(json(put("/api/v1/me/language"), "{\"language\":\"fr\"}").with(s.auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put("/api/v1/me/language"), "{\"language\":\"en\"}").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("en"));
        String token = JsonPath.read(mvc.perform(json(post("/api/v1/auth/login"),
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(s.email(), Api.PASSWORD)))
                .andExpect(jsonPath("$.language").value("en"))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/contacts/" + UUID.randomUUID()).with(bearer(token)))
                .andExpect(jsonPath("$.detail").value("Contact not found"));
        // an explicit header still wins
        mvc.perform(get("/api/v1/contacts/" + UUID.randomUUID()).header(HttpHeaders.ACCEPT_LANGUAGE, "ar")
                .with(bearer(token)))
                .andExpect(jsonPath("$.detail").value("العميل غير موجود"));
        // clearing the preference falls back to the workspace default
        mvc.perform(json(put("/api/v1/me/language"), "{\"language\":null}").with(bearer(token)))
                .andExpect(jsonPath("$.preferredLanguage").doesNotExist());
    }

    @Test
    void workspaceSettingsAreSeparateAndValidated() throws Exception {
        Session s = Api.register(mvc);
        mvc.perform(json(patch("/api/v1/workspace"),
                "{\"currency\":\"SAR\",\"timeZone\":\"Asia/Riyadh\",\"locale\":\"ar-SA\",\"sector\":\"SECURITY_SYSTEMS\"}")
                .with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("SAR"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Riyadh"))
                .andExpect(jsonPath("$.locale").value("ar-SA"))
                .andExpect(jsonPath("$.defaultLanguage").value("ar")) // untouched
                .andExpect(jsonPath("$.sector").value("SECURITY_SYSTEMS"));
        mvc.perform(json(patch("/api/v1/workspace"), "{\"name\":\"مؤسسة الأمان\",\"defaultLanguage\":\"en\"}")
                .with(s.auth()))
                .andExpect(jsonPath("$.name").value("مؤسسة الأمان"))
                .andExpect(jsonPath("$.defaultLanguage").value("en"))
                .andExpect(jsonPath("$.currency").value("SAR"));
        mvc.perform(json(patch("/api/v1/workspace"), "{\"currency\":\"XYZ\"}").with(s.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
        mvc.perform(json(patch("/api/v1/workspace"), "{\"timeZone\":\"Mars/Base\"}").with(s.auth()))
                .andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));
        mvc.perform(json(patch("/api/v1/workspace"), "{\"sector\":null}").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sector").doesNotExist());
        Session member = Invites.invite(mvc, emails, s, "MEMBER");
        mvc.perform(json(patch("/api/v1/workspace"), "{\"currency\":\"EGP\"}").with(member.auth()))
                .andExpect(status().isForbidden());
    }

    @Test
    void arabicBusinessDataIsStoredAsEnteredAndSearchable() throws Exception {
        Session s = Api.register(mvc);
        String id = JsonPath.read(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","name":{"first":"محمد","last":"عبد الرحمن"},"email":"%s",
                 "company":"شركة النيل للمقاولات","address":{"line1":"١٢ شارع التحرير","city":"القاهرة","country":"EG"}}
                """.formatted(Api.uniqueEmail())).with(s.auth()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/contacts/" + id).header(HttpHeaders.ACCEPT_LANGUAGE, "en").with(s.auth()))
                .andExpect(jsonPath("$.fullName").value("محمد عبد الرحمن")) // never translated
                .andExpect(jsonPath("$.company").value("شركة النيل للمقاولات"))
                .andExpect(jsonPath("$.address.line1").value("١٢ شارع التحرير"))
                .andExpect(jsonPath("$.address.city").value("القاهرة"));
        mvc.perform(get("/api/v1/contacts").param("q", "النيل").with(s.auth()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/contacts").param("q", "محمد").with(s.auth()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void invitationEmailsUseTheRecipientsLanguage() throws Exception {
        Session arabicOwner = Api.register(mvc);
        String invitee = Api.uniqueEmail();
        mvc.perform(json(post("/api/v1/workspace/invitations"), "{\"email\":\"%s\",\"role\":\"MEMBER\"}".formatted(invitee))
                .with(arabicOwner.auth())).andExpect(status().isCreated());
        var arabicMail = emails.lastTo(invitee).orElseThrow();
        assertThat(arabicMail.subject()).startsWith("دعوة للانضمام إلى");
        assertThat(arabicMail.html()).contains("dir=\"rtl\"").contains("قبول الدعوة").contains("عضو");

        String body = mvc.perform(json(post("/api/v1/auth/register"), register(Api.uniqueEmail(), "Cool Air", "en", null)))
                .andReturn().getResponse().getContentAsString();
        Session englishOwner = new Session(JsonPath.read(body, "$.accessToken"), null,
                UUID.fromString(JsonPath.read(body, "$.user.id")), UUID.fromString(JsonPath.read(body, "$.workspace.id")),
                JsonPath.read(body, "$.user.email"));
        String invitee2 = Api.uniqueEmail();
        mvc.perform(json(post("/api/v1/workspace/invitations"), "{\"email\":\"%s\",\"role\":\"ADMIN\"}".formatted(invitee2))
                .with(englishOwner.auth())).andExpect(status().isCreated());
        var englishMail = emails.lastTo(invitee2).orElseThrow();
        assertThat(englishMail.subject()).isEqualTo("You're invited to Cool Air");
        assertThat(englishMail.html()).contains("Accept invitation").contains("Admin").doesNotContain("rtl");
    }

    private static String register(String email, String workspace, String language, String sector) {
        return """
                {"email":"%s","password":"%s","firstName":"علي","lastName":"حسن","workspaceName":"%s"%s%s}
                """.formatted(email, Api.PASSWORD, workspace,
                language == null ? "" : ",\"language\":\"" + language + "\"",
                sector == null ? "" : ",\"sector\":\"" + sector + "\"");
    }

    private static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }
}
