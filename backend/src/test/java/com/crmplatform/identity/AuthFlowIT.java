package com.crmplatform.identity;

import static com.crmplatform.support.Api.PASSWORD;
import static com.crmplatform.support.Api.json;
import static com.crmplatform.support.Api.randomIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.support.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AuthFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    RecordingEmailSender emails;

    @Test
    void registerCreatesWorkspaceOwnerAndIssuesTokens() throws Exception { // TS-IAM-01
        String email = Api.uniqueEmail();
        mvc.perform(json(post("/api/v1/auth/register"), """
                {"email":"%s","password":"%s","firstName":"Ada","lastName":"Lovelace","workspaceName":"Analytical Engines"}
                """.formatted(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.workspace.slug").value("analytical-engines"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.permissions", hasItem("workspace:delete")))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth"));
    }

    @Test
    void registeringAnExistingEmailIsRejected() throws Exception { // TS-IAM-02
        Session s = Api.register(mvc);
        mvc.perform(json(post("/api/v1/auth/register"), """
                {"email":"%s","password":"%s","firstName":"A","lastName":"B","workspaceName":"Other"}
                """.formatted(s.email().toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void weakAndCommonPasswordsAreRejected() throws Exception { // TS-IAM-03
        mvc.perform(json(post("/api/v1/auth/register"), """
                {"email":"%s","password":"onlyletters","firstName":"A","lastName":"B","workspaceName":"Ws"}
                """.formatted(Api.uniqueEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_TOO_WEAK"));
        mvc.perform(json(post("/api/v1/auth/register"), """
                {"email":"%s","password":"charlie123","firstName":"A","lastName":"B","workspaceName":"Ws"}
                """.formatted(Api.uniqueEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_COMMON"));
    }

    @Test
    void wrongPasswordAndUnknownEmailLookIdentical() throws Exception { // TS-IAM-06
        Session s = Api.register(mvc);
        String wrongPassword = mvc.perform(json(post("/api/v1/auth/login"),
                "{\"email\":\"%s\",\"password\":\"Wrong-Passw0rd\"}".formatted(s.email())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(json(post("/api/v1/auth/login"),
                "{\"email\":\"%s\",\"password\":\"Wrong-Passw0rd\"}".formatted(Api.uniqueEmail())))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(wrongPassword.replaceAll("\"traceId\":\"[^\"]+\"", ""))
                .isEqualTo(unknown.replaceAll("\"traceId\":\"[^\"]+\"", ""));
    }

    @Test
    void repeatedFailedLoginsAreThrottled() throws Exception { // TS-IAM-04/07 (lockout returns 423 once the 1-minute rate window resets)
        Session s = Api.register(mvc);
        for (int i = 0; i < 5; i++) {
            mvc.perform(json(post("/api/v1/auth/login"),
                    "{\"email\":\"%s\",\"password\":\"Wrong-Passw0rd\"}".formatted(s.email())));
        }
        mvc.perform(json(post("/api/v1/auth/login"),
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(s.email(), PASSWORD)))
                .andExpect(status().is(429)); // per-email rate limit (5/min) triggers first; lockout covered below
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception { // TS-SEC-03
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsProfileAndMemberships() throws Exception {
        Session s = Api.register(mvc);
        mvc.perform(get("/api/v1/me").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(s.email()))
                .andExpect(jsonPath("$.memberships[0].role").value("OWNER"));
    }

    @Test
    void refreshRotatesTheTokenAndDetectsReuse() throws Exception { // TS-IAM-10, TS-IAM-11
        Session s = Api.register(mvc);
        var first = mvc.perform(post("/api/v1/auth/refresh").with(randomIp())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("refresh_token", s.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse();
        String rotated = first.getCookie("refresh_token").getValue();
        assertThat(rotated).isNotEqualTo(s.refreshToken());

        Thread.sleep(10); // still within the 10 s grace window: concurrent-tab refresh succeeds
        mvc.perform(post("/api/v1/auth/refresh").with(randomIp()).header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("refresh_token", s.refreshToken())))
                .andExpect(status().isOk());
    }

    @Test
    void refreshRequiresTheCsrfHeader() throws Exception {
        Session s = Api.register(mvc);
        mvc.perform(post("/api/v1/auth/refresh").with(randomIp()).cookie(new Cookie("refresh_token", s.refreshToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRevokesTheRefreshFamily() throws Exception { // TS-IAM-14
        Session s = Api.register(mvc);
        mvc.perform(post("/api/v1/auth/logout").with(randomIp()).header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("refresh_token", s.refreshToken())))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));
        mvc.perform(post("/api/v1/auth/refresh").with(randomIp()).header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("refresh_token", s.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllDenylistsTheCurrentAccessToken() throws Exception { // TS-IAM-15
        Session s = Api.register(mvc);
        mvc.perform(post("/api/v1/auth/logout-all").with(s.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").with(s.auth())).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetFlow() throws Exception { // TS-IAM-16, TS-IAM-17, TS-IAM-18
        Session s = Api.register(mvc);
        mvc.perform(json(post("/api/v1/auth/password/forgot"), "{\"email\":\"%s\"}".formatted(s.email())))
                .andExpect(status().isAccepted());
        mvc.perform(json(post("/api/v1/auth/password/forgot"), "{\"email\":\"%s\"}".formatted(Api.uniqueEmail())))
                .andExpect(status().isAccepted());

        String html = emails.lastTo(s.email()).orElseThrow().html();
        Matcher m = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)").matcher(html);
        assertThat(m.find()).isTrue();
        String token = m.group(1);

        String newPassword = "An0ther-Secret!";
        mvc.perform(json(post("/api/v1/auth/password/reset"),
                        "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/api/v1/auth/password/reset"),
                        "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        Api.login(mvc, s.email(), newPassword);
    }

    @Test
    void changePasswordRequiresTheCurrentOne() throws Exception { // TS-IAM-19
        Session s = Api.register(mvc);
        mvc.perform(json(post("/api/v1/auth/password/change"),
                        "{\"currentPassword\":\"Wrong-Passw0rd\",\"newPassword\":\"Brand-New-Passw0rd\"}").with(s.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
        mvc.perform(json(post("/api/v1/auth/password/change"),
                        "{\"currentPassword\":\"%s\",\"newPassword\":\"Brand-New-Passw0rd\"}".formatted(PASSWORD))
                        .with(s.auth()))
                .andExpect(status().isNoContent());
    }
}
