package com.crmplatform.support;

import static com.crmplatform.support.Api.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.crmplatform.support.Api.Session;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Adds a second member to a workspace through the real invitation flow. */
public final class Invites {

    private static final Pattern TOKEN = Pattern.compile("accept-invite\\?token=([A-Za-z0-9_-]+)");

    private Invites() {
    }

    /** Invites a new user with {@code role} into the owner's workspace and signs them in. */
    public static Session invite(MockMvc mvc, RecordingEmailSender emails, Session owner, String role)
            throws Exception {
        String email = Api.uniqueEmail();
        MockHttpServletResponse created = mvc.perform(json(post("/api/v1/workspace/invitations"),
                "{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role)).with(owner.auth())).andReturn().getResponse();
        if (created.getStatus() != 201) {
            throw new IllegalStateException("invite failed: " + created.getStatus() + " " + created.getContentAsString());
        }
        Matcher m = TOKEN.matcher(emails.lastTo(email).orElseThrow().html());
        if (!m.find()) {
            throw new IllegalStateException("no invitation token in the email");
        }
        MockHttpServletResponse accepted = mvc.perform(json(post("/api/v1/auth/invitations/accept"),
                "{\"token\":\"%s\",\"firstName\":\"Mia\",\"lastName\":\"Member\",\"password\":\"%s\"}"
                        .formatted(m.group(1), Api.PASSWORD))).andReturn().getResponse();
        if (accepted.getStatus() != 200) {
            throw new IllegalStateException("accept failed: " + accepted.getStatus() + " " + accepted.getContentAsString());
        }
        String body = accepted.getContentAsString();
        return new Session(JsonPath.read(body, "$.accessToken"), null, UUID.fromString(JsonPath.read(body, "$.user.id")),
                UUID.fromString(JsonPath.read(body, "$.workspace.id")), email);
    }
}
