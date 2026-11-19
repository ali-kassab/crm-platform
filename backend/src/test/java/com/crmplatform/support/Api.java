package com.crmplatform.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Small helpers for API tests: unique identities, random client IPs, sign-up shortcuts. */
public final class Api {

    public static final String PASSWORD = "Str0ng-Passw0rd!";

    private Api() {
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    /** Gives each request a random client IP so per-IP rate limits do not interfere across tests. */
    public static RequestPostProcessor randomIp() {
        String ip = "10." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256)
                + "." + ThreadLocalRandom.current().nextInt(1, 255);
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    public static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body).with(randomIp());
    }

    public static Session register(MockMvc mvc, String email, String workspaceName) throws Exception {
        String body = """
                {"email":"%s","password":"%s","firstName":"Test","lastName":"User","workspaceName":"%s"}
                """.formatted(email, PASSWORD, workspaceName);
        MockHttpServletResponse response = mvc.perform(json(post("/api/v1/auth/register"), body)).andReturn().getResponse();
        if (response.getStatus() != 201) {
            throw new IllegalStateException("register failed: " + response.getStatus() + " " + response.getContentAsString());
        }
        return Session.from(response);
    }

    public static Session register(MockMvc mvc) throws Exception {
        return register(mvc, uniqueEmail(), "Acme " + UUID.randomUUID().toString().substring(0, 4));
    }

    public static Session login(MockMvc mvc, String email, String password) throws Exception {
        MockHttpServletResponse response = mvc.perform(json(post("/api/v1/auth/login"),
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))).andReturn().getResponse();
        if (response.getStatus() != 200) {
            throw new IllegalStateException("login failed: " + response.getStatus() + " " + response.getContentAsString());
        }
        return Session.from(response);
    }

    public record Session(String accessToken, String refreshToken, UUID userId, UUID workspaceId, String email) {

        static Session from(MockHttpServletResponse response) throws Exception {
            String json = response.getContentAsString();
            Cookie cookie = response.getCookie("refresh_token");
            return new Session(JsonPath.read(json, "$.accessToken"), cookie == null ? null : cookie.getValue(),
                    UUID.fromString(JsonPath.read(json, "$.user.id")),
                    UUID.fromString(JsonPath.read(json, "$.workspace.id")), JsonPath.read(json, "$.user.email"));
        }

        public RequestPostProcessor auth() {
            return request -> {
                request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
                return request;
            };
        }
    }
}
