package com.crmplatform.security;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.support.RecordingEmailSender;
import com.crmplatform.work.projects.domain.TeamMemberNote;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Negative tenant-isolation tests: missing context denies, and one workspace never reaches another's data. */
@IntegrationTest
class TenantIsolationIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    EntityManager em;

    @Autowired
    PlatformTransactionManager tm;

    @Autowired
    RecordingEmailSender emails;

    // ------------------------------------------------------------------ fail-closed persistence

    @Test
    void queriesWithoutTenantContextSeeNothing() throws Exception {
        Session a = Api.register(mvc);
        contact(a);
        TransactionTemplate tx = new TransactionTemplate(tm);

        long withoutContext = tx.execute(s -> count());
        long inOwnWorkspace = TenantContext.runAs(a.workspaceId(), a.userId(), () -> tx.execute(s -> count()));
        long inRandomWorkspace = TenantContext.runAs(UUID.randomUUID(), null, () -> tx.execute(s -> count()));
        long asSystem = TenantContext.runAs(TenantContext.SYSTEM, null, () -> tx.execute(s -> count()));

        assertThat(withoutContext).isZero();
        assertThat(inOwnWorkspace).isEqualTo(1);
        assertThat(inRandomWorkspace).isZero();
        assertThat(asSystem).isGreaterThanOrEqualTo(1); // explicit platform scope only
    }

    @Test
    void writesWithoutWorkspaceContextAreRejected() {
        TransactionTemplate tx = new TransactionTemplate(tm);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            em.persist(new TeamMemberNote(UUID.randomUUID(), "orphan"));
            em.flush();
        })).hasStackTraceContaining("only be created inside a workspace context");
        assertThatThrownBy(() -> TenantContext.runAs(TenantContext.SYSTEM, null, () -> tx.executeWithoutResult(s -> {
            em.persist(new TeamMemberNote(UUID.randomUUID(), "system"));
            em.flush();
        }))).hasStackTraceContaining("only be created inside a workspace context");
    }

    @Test
    void deactivatingAMemberInOneWorkspaceLeavesOtherWorkspacesAlone() throws Exception {
        Session a = Api.register(mvc);
        Session b = Api.register(mvc);
        String contactInA = representedContact(a, a.userId());
        Session aInB = joinAsExistingUser(b, a);
        String contactInB = representedContact(b, a.userId());

        mvc.perform(json(patch("/api/v1/workspace/members/" + a.userId()), "{\"active\":false}").with(b.auth()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/contacts/" + contactInB).with(b.auth()))
                .andExpect(jsonPath("$.representative").doesNotExist());
        mvc.perform(get("/api/v1/contacts/" + contactInA).with(a.auth()))
                .andExpect(jsonPath("$.representative.userId").value(a.userId().toString()));
        mvc.perform(get("/api/v1/contacts").with(aInB.auth())).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ HTTP cross-workspace matrix

    @Test
    void anotherWorkspaceCannotReadChangeOrDeleteAnyResource() throws Exception {
        Session a = Api.register(mvc);
        Session b = Api.register(mvc);
        Map<String, String> r = seedWorkspace(a);

        List<String> readable = List.of("/contacts/" + r.get("contact"), "/locations/" + r.get("location"),
                "/notes/" + r.get("note"), "/projects/" + r.get("project"), "/projects/" + r.get("project") + "/board",
                "/projects/" + r.get("project") + "/team-members", "/boards/" + r.get("board"),
                "/team-members/" + r.get("teamMember"), "/team-members/" + r.get("teamMember") + "/notes",
                "/tasks/" + r.get("task"), "/tasks/" + r.get("task") + "/comments", "/jobs/" + r.get("job"),
                "/jobs/" + r.get("job") + "/board", "/jobs/" + r.get("job") + "/accounting",
                "/projects/" + r.get("project") + "/accounting", "/contacts/" + r.get("contact") + "/accounting",
                "/warranties/" + r.get("warranty"), "/transactions/" + r.get("transaction"), "/files/" + r.get("file"),
                "/files/" + r.get("file") + "/url", "/flowcharts/" + r.get("flowchart"),
                "/flowcharts/" + r.get("flowchart") + "/diagram", "/contacts/" + r.get("contact") + "/milestone");
        for (String path : readable) {
            int code = mvc.perform(get("/api/v1" + path).with(b.auth())).andReturn().getResponse().getStatus();
            assertThat(code).as("GET %s from another workspace", path).isEqualTo(404);
        }

        Map<String, String> patches = new LinkedHashMap<>();
        patches.put("/contacts/" + r.get("contact"), "{\"company\":\"x\"}");
        patches.put("/locations/" + r.get("location"), "{\"name\":\"x\"}");
        patches.put("/projects/" + r.get("project"), "{\"name\":\"x\"}");
        patches.put("/tasks/" + r.get("task"), "{\"title\":\"x\"}");
        patches.put("/jobs/" + r.get("job"), "{\"title\":\"x\"}");
        patches.put("/warranties/" + r.get("warranty"), "{\"note\":\"x\"}");
        patches.put("/team-members/" + r.get("teamMember"), "{\"position\":\"x\"}");
        patches.put("/flowcharts/" + r.get("flowchart"), "{\"favorite\":true}");
        for (var e : patches.entrySet()) {
            int code = mvc.perform(json(patch("/api/v1" + e.getKey()), e.getValue()).with(b.auth())).andReturn()
                    .getResponse().getStatus();
            assertThat(code).as("PATCH %s from another workspace", e.getKey()).isEqualTo(404);
        }

        List<String> deletable = List.of("/notes/" + r.get("note"), "/tasks/" + r.get("task"), "/jobs/" + r.get("job"),
                "/warranties/" + r.get("warranty"), "/files/" + r.get("file"), "/flowcharts/" + r.get("flowchart"),
                "/projects/" + r.get("project"), "/locations/" + r.get("location"), "/contacts/" + r.get("contact"));
        for (String path : deletable) {
            int code = mvc.perform(delete("/api/v1" + path).with(b.auth())).andReturn().getResponse().getStatus();
            assertThat(code).as("DELETE %s from another workspace", path).isEqualTo(404);
        }

        mvc.perform(json(post("/api/v1/transactions/" + r.get("transaction") + "/void"), "{\"reason\":\"not mine\"}")
                .with(b.auth())).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/v1/tasks/" + r.get("task") + "/move"),
                "{\"columnId\":\"%s\",\"position\":1,\"boardVersion\":0}".formatted(UUID.randomUUID())).with(b.auth()))
                .andExpect(status().isNotFound());

        for (String list : List.of("/contacts", "/locations", "/projects", "/tasks", "/jobs", "/warranties",
                "/transactions", "/flowcharts")) {
            mvc.perform(get("/api/v1" + list).with(b.auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        // references to another workspace's records are rejected, not silently linked
        String projectInB = project(b, location(b, contact(b)));
        mvc.perform(json(post("/api/v1/tasks"), """
                {"projectId":"%s","title":"x","description":"","startDate":"2026-05-01","endDate":"2026-05-02"}
                """.formatted(r.get("project"))).with(b.auth())).andExpect(status().isBadRequest());
        String jobInB = id(job(b, projectInB));
        mvc.perform(json(post("/api/v1/jobs/" + jobInB + "/files"), "{\"fileIds\":[\"%s\"]}".formatted(r.get("file")))
                .with(b.auth())).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/transactions"), """
                {"jobId":"%s","type":"PAID","amount":"1","title":"x","payee":"y","method":"CASH",
                 "commitmentDate":"2026-03-01","dueDate":"2026-03-01"}
                """.formatted(r.get("job"))).with(b.auth())).andExpect(status().isBadRequest());

        // A still sees all its data intact
        mvc.perform(get("/api/v1/contacts/" + r.get("contact")).with(a.auth())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/transactions/" + r.get("transaction")).with(a.auth()))
                .andExpect(jsonPath("$.voided").value(false));
    }

    // ------------------------------------------------------------------ helpers

    private long count() {
        return em.createQuery("select count(c) from Contact c", Long.class).getSingleResult();
    }

    private Map<String, String> seedWorkspace(Session s) throws Exception {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("contact", contact(s));
        r.put("location", location(s, r.get("contact")));
        r.put("note", id(mvc.perform(json(post("/api/v1/notes"), "{\"contactId\":\"%s\",\"title\":\"Note\",\"body\":\"Text\"}"
                .formatted(r.get("contact"))).with(s.auth())).andExpect(status().isCreated())));
        r.put("project", project(s, r.get("location")));
        String projectJson = body(mvc.perform(get("/api/v1/projects/" + r.get("project")).with(s.auth())));
        r.put("board", JsonPath.read(projectJson, "$.boardId"));
        r.put("teamMember", JsonPath.read(projectJson, "$.team[0].id"));
        r.put("task", id(mvc.perform(json(post("/api/v1/tasks"), """
                {"projectId":"%s","title":"T","description":"","startDate":"2026-05-01","endDate":"2026-05-02"}
                """.formatted(r.get("project"))).with(s.auth())).andExpect(status().isCreated())));
        mvc.perform(json(post("/api/v1/tasks/" + r.get("task") + "/comments"), "{\"text\":\"hi\"}").with(s.auth()))
                .andExpect(status().isCreated());
        r.put("job", id(job(s, r.get("project")).andExpect(status().isCreated())));
        r.put("warranty", id(mvc.perform(json(post("/api/v1/warranties"), """
                {"title":"Roof","provider":"RoofCo","startDate":"2026-01-01","endDate":"2028-01-01","jobId":"%s"}
                """.formatted(r.get("job"))).with(s.auth())).andExpect(status().isCreated())));
        r.put("transaction", id(mvc.perform(json(post("/api/v1/transactions"), """
                {"jobId":"%s","type":"RECEIVED","amount":"10","title":"Deposit","payee":"Client","method":"CASH",
                 "commitmentDate":"2026-03-01","dueDate":"2026-03-01"}
                """.formatted(r.get("job"))).with(s.auth())).andExpect(status().isCreated())));
        String upload = body(mvc.perform(multipart("/api/v1/files").file(new MockMultipartFile("files", "plan.pdf",
                        "application/pdf", "%PDF-1.4\n%âãÏÓ\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF"
                                .getBytes(StandardCharsets.ISO_8859_1)))
                .param("kind", "DOCUMENT").with(s.auth())).andExpect(status().isCreated()));
        r.put("file", JsonPath.read(upload, "$.files[0].id"));
        r.put("flowchart", id(mvc.perform(json(post("/api/v1/flowcharts"), "{\"title\":\"Flow\"}").with(s.auth()))
                .andExpect(status().isCreated())));
        return r;
    }

    private String contact(Session s) throws Exception {
        return id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","name":{"first":"Sam","last":"Rivera"},"email":"%s"}
                """.formatted(Api.uniqueEmail())).with(s.auth())).andExpect(status().isCreated()));
    }

    private String representedContact(Session s, UUID representative) throws Exception {
        return id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","name":{"first":"Rana","last":"Adel"},"email":"%s","representativeUserId":"%s"}
                """.formatted(Api.uniqueEmail(), representative)).with(s.auth())).andExpect(status().isCreated()));
    }

    private String location(Session s, String contact) throws Exception {
        return id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"OFFICE","name":"HQ","address":{"city":"Cairo","country":"EG"}}
                """.formatted(contact)).with(s.auth())).andExpect(status().isCreated()));
    }

    private String project(Session s, String location) throws Exception {
        return id(mvc.perform(json(post("/api/v1/projects"), """
                {"name":"Tower","description":"Office tower",
                 "address":{"city":"Cairo","region":"Cairo","postalCode":"11511","country":"EG"},
                 "totalArea":"100","budget":"1000","startDate":"2026-01-01","estimatedEndDate":"2026-12-31",
                 "locationId":"%s","projectManagerUserId":"%s"}
                """.formatted(location, s.userId())).with(s.auth())).andExpect(status().isCreated()));
    }

    private ResultActions job(Session s, String project) throws Exception {
        return mvc.perform(json(post("/api/v1/jobs"), """
                {"projectId":"%s","title":"Job","description":"Work","startDate":"2026-02-01",
                 "estimatedEndDate":"2026-03-01","budget":"100"}
                """.formatted(project)).with(s.auth()));
    }

    /** {@code existing} joins {@code owner}'s workspace through an invitation and switches into it. */
    private Session joinAsExistingUser(Session owner, Session existing) throws Exception {
        mvc.perform(json(post("/api/v1/workspace/invitations"), "{\"email\":\"%s\",\"role\":\"MEMBER\"}"
                .formatted(existing.email())).with(owner.auth())).andExpect(status().isCreated());
        Matcher m = Pattern.compile("accept-invite\\?token=([A-Za-z0-9_-]+)")
                .matcher(emails.lastTo(existing.email()).orElseThrow().html());
        assertThat(m.find()).isTrue();
        mvc.perform(json(post("/api/v1/auth/invitations/accept"), "{\"token\":\"%s\"}".formatted(m.group(1)))
                .with(existing.auth())).andExpect(status().isOk());
        String switched = body(mvc.perform(json(post("/api/v1/auth/switch-workspace"),
                "{\"workspaceId\":\"%s\"}".formatted(owner.workspaceId())).with(existing.auth()))
                .andExpect(status().isOk()));
        return new Session(JsonPath.read(switched, "$.accessToken"), null, existing.userId(), owner.workspaceId(),
                existing.email());
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.id");
    }
}
