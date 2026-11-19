package com.crmplatform.realtime;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.support.Invites;
import com.crmplatform.support.RecordingEmailSender;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.web.servlet.MockMvc;

/** After-commit hints on Redis and one-time socket tickets (R11, ADR-0015). */
@IntegrationTest
class RealtimeIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingEmailSender emails;
    @Autowired
    RedisConnectionFactory connections;
    @Autowired
    StringRedisTemplate redis;

    private RedisMessageListenerContainer container;
    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

    @BeforeEach
    void subscribe() throws Exception {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connections);
        container.addMessageListener((message, pattern) -> messages.add(
                new String(message.getBody(), StandardCharsets.UTF_8)), new ChannelTopic("rt:events"));
        container.afterPropertiesSet();
        container.start();
        Thread.sleep(300); // let the subscription settle
    }

    @AfterEach
    void unsubscribe() throws Exception {
        container.stop();
        container.destroy();
    }

    @Test
    void committedChangesPublishIdOnlyHintsAndNotificationsReachTheirUsers() throws Exception {
        Session owner = Api.register(mvc);
        Session tech = Invites.invite(mvc, emails, owner, "TECHNICIAN");
        String customer = JsonPath.read(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","kind":"COMPANY","company":"سري جداً","phone":"+20223456789"}
                """).with(owner.auth())).andReturn().getResponse().getContentAsString(), "$.id");
        String site = JsonPath.read(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"COMMERCIAL","name":"HQ","address":{"line1":"x","city":"Cairo","country":"EG"}}
                """.formatted(customer)).with(owner.auth())).andReturn().getResponse().getContentAsString(), "$.id");
        messages.clear();

        String job = JsonPath.read(mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"عنوان سري","budget":"999","assigneeUserIds":["%s"]}
                """.formatted(customer, site, tech.userId())).with(owner.auth()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        List<String> got = drain(2);
        String changed = got.stream().filter(m -> m.contains("\"changed\"")).findFirst().orElseThrow();
        assertThat((String) JsonPath.read(changed, "$.workspaceId")).isEqualTo(owner.workspaceId().toString());
        assertThat((String) JsonPath.read(changed, "$.entityType")).isEqualTo("JOB");
        assertThat((String) JsonPath.read(changed, "$.entityId")).isEqualTo(job);
        assertThat(changed).doesNotContain("عنوان سري").doesNotContain("999"); // ids only, no business data
        String notification = got.stream().filter(m -> m.contains("\"notification\"")).findFirst().orElseThrow();
        assertThat((List<String>) JsonPath.read(notification, "$.userIds")).containsExactly(tech.userId().toString());

        // a rejected change publishes nothing
        messages.clear();
        mvc.perform(json(post("/api/v1/jobs"), "{\"title\":\"x\"}").with(owner.auth()))
                .andExpect(status().isBadRequest());
        assertThat(messages.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void boardChangesPublishBoardIdAndTypeOnly() throws Exception {
        Session owner = Api.register(mvc);
        String customer = JsonPath.read(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","kind":"COMPANY","company":"Co","phone":"+20223456780"}
                """).with(owner.auth())).andReturn().getResponse().getContentAsString(), "$.id");
        String site = JsonPath.read(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"COMMERCIAL","name":"HQ","address":{"line1":"x","city":"Cairo","country":"EG"}}
                """.formatted(customer)).with(owner.auth())).andReturn().getResponse().getContentAsString(), "$.id");
        String job = JsonPath.read(mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"Install"}
                """.formatted(customer, site)).with(owner.auth()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String board = JsonPath.read(mvc.perform(get("/api/v1/jobs/" + job + "/board").with(owner.auth()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        messages.clear();

        mvc.perform(json(post("/api/v1/tasks"), """
                {"jobId":"%s","title":"مهمة سرية","description":"","startDate":"2026-05-01","endDate":"2026-05-03"}
                """.formatted(job)).with(owner.auth())).andExpect(status().isCreated());

        String hint = drain(1).stream().filter(m -> m.contains("\"BOARD\"")).findFirst().orElseThrow();
        assertThat((String) JsonPath.read(hint, "$.workspaceId")).isEqualTo(owner.workspaceId().toString());
        assertThat((String) JsonPath.read(hint, "$.entityId")).isEqualTo(board);
        assertThat((String) JsonPath.read(hint, "$.action")).isEqualTo("TASK_CREATED");
        assertThat(hint).doesNotContain("مهمة سرية");
    }

    @Test
    void ticketsAreRandomShortLivedAndBoundToTheCaller() throws Exception {
        Session s = Api.register(mvc);
        String body = mvc.perform(post("/api/v1/realtime/ticket").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(60))
                .andReturn().getResponse().getContentAsString();
        String ticket = JsonPath.read(body, "$.ticket");
        assertThat(ticket).hasSizeGreaterThanOrEqualTo(43); // 256 bits, base64url
        String claims = redis.opsForValue().get("rt:ticket:" + ticket);
        assertThat((String) JsonPath.read(claims, "$.userId")).isEqualTo(s.userId().toString());
        assertThat((String) JsonPath.read(claims, "$.workspaceId")).isEqualTo(s.workspaceId().toString());
        Long ttl = redis.getExpire("rt:ticket:" + ticket, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(1L, 60L);

        String second = JsonPath.read(mvc.perform(post("/api/v1/realtime/ticket").with(s.auth()))
                .andReturn().getResponse().getContentAsString(), "$.ticket");
        assertThat(second).isNotEqualTo(ticket);
        mvc.perform(post("/api/v1/realtime/ticket")).andExpect(status().isUnauthorized());
    }

    private List<String> drain(int atLeast) throws InterruptedException {
        List<String> out = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (out.size() < atLeast && System.nanoTime() < deadline) {
            String m = messages.poll(200, TimeUnit.MILLISECONDS);
            if (m != null) {
                out.add(m);
            }
        }
        Thread.sleep(200);
        messages.drainTo(out);
        return out;
    }
}
