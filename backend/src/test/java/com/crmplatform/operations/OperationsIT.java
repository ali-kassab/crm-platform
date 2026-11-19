package com.crmplatform.operations;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.operations.warranties.application.WarrantyService;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.support.Invites;
import com.crmplatform.support.RecordingEmailSender;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Jobs and warranties (TS-OPS-01..11) plus their effect on projects and boards. */
@IntegrationTest
class OperationsIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    RecordingEmailSender emails;

    @Autowired
    WarrantyService warrantyService;

    // ------------------------------------------------------------------ jobs

    @Test
    void createJobBuildsBoardAndReducesProjectRemaining() throws Exception { // TS-OPS-01, BR-055, BR-062
        Session s = Api.register(mvc);
        String project = createProject(s, "1000");
        String job = id(createJob(s, project, "300", "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.budget").value("300.00"))
                .andExpect(jsonPath("$.boardId").value(notNullValue()))
                .andExpect(jsonPath("$.projectId").value(project)));
        mvc.perform(get("/api/v1/jobs/" + job + "/board").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerType").value("JOB"))
                .andExpect(jsonPath("$.columns", hasSize(4)));
        mvc.perform(get("/api/v1/projects/" + project).with(s.auth()))
                .andExpect(jsonPath("$.remainingBudget").value("700.00"));
    }

    @Test
    void managerCannotBeAMemberAndDatesAreChecked() throws Exception { // TS-OPS-02, TS-OPS-03, BR-061
        Session s = Api.register(mvc);
        String project = createProject(s, "1000");
        createJob(s, project, "10", ",\"teamManagerUserId\":\"%s\",\"teamMemberUserIds\":[\"%s\"]"
                .formatted(s.userId(), s.userId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MANAGER_IN_TEAM"));
        mvc.perform(json(post("/api/v1/jobs"), """
                {"projectId":"%s","title":"Bad","description":"d","startDate":"2026-05-02",
                 "estimatedEndDate":"2026-05-01","budget":"1"}
                """.formatted(project)).with(s.auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/jobs"), """
                {"projectId":"%s","title":"Neg","description":"d","startDate":"2026-05-01",
                 "estimatedEndDate":"2026-05-02","budget":"-1"}
                """.formatted(project)).with(s.auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/jobs?projectId=" + project).with(s.auth()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void jobTeamUsersAreAddedToTheProjectTeam() throws Exception { // BR-060
        Session s = Api.register(mvc);
        Session member = invite(s);
        String project = createProject(s, "1000");
        String job = id(createJob(s, project, "10", ",\"teamManagerUserId\":\"%s\",\"teamMemberUserIds\":[\"%s\"]"
                .formatted(member.userId(), s.userId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamManager.userId").value(member.userId().toString()))
                .andExpect(jsonPath("$.teamMembers[0].userId").value(s.userId().toString())));
        mvc.perform(get("/api/v1/projects/" + project + "/team-members").with(s.auth()))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(json(patch("/api/v1/jobs/" + job), "{\"teamManagerUserId\":null}").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamManager").doesNotExist())
                .andExpect(jsonPath("$.teamMembers", hasSize(1)));
        mvc.perform(json(patch("/api/v1/jobs/" + job), "{\"projectId\":\"%s\"}".formatted(UUID.randomUUID()))
                .with(s.auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filterJobsByProjectAndBudgetChanges() throws Exception { // TS-OPS-04, TS-OPS-05, TS-WRK-05, EC-15
        Session s = Api.register(mvc);
        String p1 = createProject(s, "1000");
        String p2 = createProject(s, "100");
        String job = id(createJob(s, p1, "200", ""));
        createJob(s, p2, "50", "");
        mvc.perform(get("/api/v1/jobs?projectId=" + p1).with(s.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(job)));
        mvc.perform(json(patch("/api/v1/jobs/" + job), "{\"budget\":\"1500\"}").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Foundation"));
        mvc.perform(get("/api/v1/projects/" + p1).with(s.auth()))
                .andExpect(jsonPath("$.remainingBudget").value("-500.00"));
        mvc.perform(get("/api/v1/projects?overBudget=true").with(s.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(p1)));
        mvc.perform(get("/api/v1/projects?overBudget=false").with(s.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(p2)));
    }

    @Test
    void jobFilesAttachAndRemove() throws Exception { // TS-OPS-06, BR-063
        Session s = Api.register(mvc);
        String job = id(createJob(s, createProject(s, "1000"), "10", ""));
        String file = uploadPdf(s);
        mvc.perform(json(post("/api/v1/jobs/" + job + "/files"), "{\"fileIds\":[\"%s\"]}".formatted(file))
                .with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].id").value(file));
        mvc.perform(get("/api/v1/jobs/" + job).with(s.auth()))
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.media", hasSize(0)));
        mvc.perform(delete("/api/v1/jobs/" + job + "/files/" + UUID.randomUUID()).with(s.auth()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/jobs/" + job + "/files/" + file).with(s.auth()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/files/" + file).with(s.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/jobs/" + job).with(s.auth())).andExpect(jsonPath("$.documents", hasSize(0)));
    }

    @Test
    void deleteJobRestoresBudgetAndHidesTasks() throws Exception { // TS-OPS-07, EC-23
        Session s = Api.register(mvc);
        String project = createProject(s, "1000");
        String job = id(createJob(s, project, "400", ""));
        String task = id(mvc.perform(json(post("/api/v1/tasks"), """
                {"jobId":"%s","title":"Dig","description":"","startDate":"2026-05-01","endDate":"2026-05-03"}
                """.formatted(job)).with(s.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(job)));
        mvc.perform(get("/api/v1/tasks?jobId=" + job).with(s.auth())).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(delete("/api/v1/jobs/" + job).with(s.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/jobs/" + job).with(s.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/jobs/" + job + "/board").with(s.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tasks/" + task).with(s.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + project).with(s.auth()))
                .andExpect(jsonPath("$.remainingBudget").value("1000.00"));
    }

    @Test
    void deletingAProjectDeletesItsJobsAndWarranties() throws Exception { // TS-WRK-09, BR-056
        Session s = Api.register(mvc);
        String project = createProject(s, "1000");
        String job = id(createJob(s, project, "10", ""));
        String warranty = id(createWarranty(s, project, null, "2026-01-01", "2030-01-01"));
        mvc.perform(delete("/api/v1/projects/" + project).with(s.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/jobs/" + job).with(s.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/warranties/" + warranty).with(s.auth())).andExpect(status().isNotFound());
    }

    @Test
    void jobsAreIsolatedPerWorkspace() throws Exception {
        Session a = Api.register(mvc);
        Session b = Api.register(mvc);
        String job = id(createJob(a, createProject(a, "1000"), "10", ""));
        mvc.perform(get("/api/v1/jobs/" + job).with(b.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/jobs/" + job + "/board").with(b.auth())).andExpect(status().isNotFound());
        createWarranty(b, null, job, "2026-01-01", "2027-01-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("jobId"));
    }

    // ------------------------------------------------------------------ warranties

    @Test
    void warrantyCrudAndRules() throws Exception { // TS-OPS-08..10, BR-064
        Session s = Api.register(mvc);
        String p1 = createProject(s, "1000");
        String p2 = createProject(s, "1000");
        String job = id(createJob(s, p1, "10", ""));
        String w = id(createWarranty(s, null, job, "2026-01-01", "2028-01-01")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.projectId").value(p1)));
        createWarranty(s, p2, job, "2026-01-01", "2028-01-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JOB_NOT_IN_PROJECT"));
        createWarranty(s, p1, null, "2026-02-01", "2026-01-01").andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/warranties"), """
                {"title":"ab","provider":"Acme","startDate":"2026-01-01","endDate":"2026-02-01"}
                """).with(s.auth())).andExpect(status().isBadRequest());
        mvc.perform(json(patch("/api/v1/warranties/" + w), "{\"status\":\"CANCELLED\",\"note\":\"Replaced\"}")
                .with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.note").value("Replaced"))
                .andExpect(jsonPath("$.provider").value("RoofCo"));
        mvc.perform(get("/api/v1/warranties?status=CANCELLED&provider=roof").with(s.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(w)));
        mvc.perform(get("/api/v1/warranties?jobId=" + job).with(s.auth())).andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(delete("/api/v1/jobs/" + job).with(s.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/warranties/" + w).with(s.auth()))
                .andExpect(jsonPath("$.jobId").doesNotExist())
                .andExpect(jsonPath("$.projectId").value(p1));
        mvc.perform(delete("/api/v1/warranties/" + w).with(s.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/warranties/" + w).with(s.auth())).andExpect(status().isNotFound());
    }

    @Test
    void expiryFlipsOnlyActiveWarrantiesPastTheirEnd() throws Exception { // TS-OPS-11, BR-065, EC-38
        Session s = Api.register(mvc);
        String project = createProject(s, "1000");
        LocalDate today = LocalDate.now();
        String ended = id(createWarranty(s, project, null, today.minusYears(2).toString(),
                today.minusDays(1).toString()));
        String endsToday = id(createWarranty(s, project, null, today.minusYears(1).toString(), today.toString()));
        String cancelled = id(createWarranty(s, project, null, today.minusYears(2).toString(),
                today.minusDays(3).toString()));
        mvc.perform(json(patch("/api/v1/warranties/" + cancelled), "{\"status\":\"CANCELLED\"}").with(s.auth()))
                .andExpect(status().isOk());

        int first = TenantContext.runAs(TenantContext.SYSTEM, null, warrantyService::expireEnded);
        assertThat(first).isGreaterThanOrEqualTo(1);
        mvc.perform(get("/api/v1/warranties/" + ended).with(s.auth())).andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get("/api/v1/warranties/" + endsToday).with(s.auth()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/warranties/" + cancelled).with(s.auth()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        int second = TenantContext.runAs(TenantContext.SYSTEM, null, warrantyService::expireEnded);
        assertThat(second).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private String createProject(Session s, String budget) throws Exception {
        String contact = id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","name":{"first":"Sam","last":"Rivera"},"email":"%s"}
                """.formatted(Api.uniqueEmail())).with(s.auth())).andExpect(status().isCreated()));
        String location = id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"OFFICE","name":"HQ","address":{"city":"Austin","country":"US"}}
                """.formatted(contact)).with(s.auth())).andExpect(status().isCreated()));
        return id(mvc.perform(json(post("/api/v1/projects"), """
                {"name":"Tower A","description":"Office tower",
                 "address":{"city":"Austin","region":"TX","postalCode":"78701","country":"US"},
                 "totalArea":"100","budget":"%s","startDate":"2026-01-01","estimatedEndDate":"2026-12-31",
                 "locationId":"%s","projectManagerUserId":"%s"}
                """.formatted(budget, location, s.userId())).with(s.auth())).andExpect(status().isCreated()));
    }

    private ResultActions createJob(Session s, String projectId, String budget, String extra) throws Exception {
        return mvc.perform(json(post("/api/v1/jobs"), """
                {"projectId":"%s","title":"Foundation","description":"Pour the foundation",
                 "startDate":"2026-02-01","estimatedEndDate":"2026-03-01","budget":"%s"%s}
                """.formatted(projectId, budget, extra)).with(s.auth()));
    }

    private ResultActions createWarranty(Session s, String projectId, String jobId, String start, String end)
            throws Exception {
        String links = (projectId == null ? "" : ",\"projectId\":\"%s\"".formatted(projectId))
                + (jobId == null ? "" : ",\"jobId\":\"%s\"".formatted(jobId));
        return mvc.perform(json(post("/api/v1/warranties"), """
                {"title":"Roof warranty","provider":"RoofCo","startDate":"%s","endDate":"%s"%s}
                """.formatted(start, end, links)).with(s.auth()));
    }

    private String uploadPdf(Session s) throws Exception {
        String json = mvc.perform(multipart("/api/v1/files").file(new MockMultipartFile("files", "plan.pdf",
                        "application/pdf", "%PDF-1.4\n%âãÏÓ\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF"
                                .getBytes(StandardCharsets.ISO_8859_1)))
                        .param("kind", "DOCUMENT").with(s.auth()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.files[0].id");
    }

    private Session invite(Session owner) throws Exception {
        return Invites.invite(mvc, emails, owner, "MEMBER");
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
