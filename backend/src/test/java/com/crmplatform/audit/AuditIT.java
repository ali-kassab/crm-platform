package com.crmplatform.audit;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.support.Invites;
import com.crmplatform.support.RecordingEmailSender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Audit events are persisted with the change, scoped per workspace, and append-only (BR-111). */
@IntegrationTest
class AuditIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RecordingEmailSender emails;

    @Test
    void changesAreRecordedPerWorkspaceAndCannotBeAltered() throws Exception {
        Session a = Api.register(mvc);
        Session b = Api.register(mvc);
        String project = createProjectWithJob(a);

        mvc.perform(json(patch("/api/v1/projects/" + project), "{\"budget\":\"1500\"}").with(a.auth()))
                .andExpect(status().isOk());
        String entries = mvc.perform(get("/api/v1/audit?entityType=PROJECT&entityId=" + project).with(a.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].action").value("UPDATE"))
                .andExpect(jsonPath("$.content[0].changes.budget.from").value("1000.00"))
                .andExpect(jsonPath("$.content[0].changes.budget.to").value("1500.00"))
                .andExpect(jsonPath("$.content[0].actorUserId").value(a.userId().toString()))
                .andExpect(jsonPath("$.content[1].action").value("CREATE"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/audit?projectId=" + project).with(a.auth()))
                .andExpect(jsonPath("$.content[*].entityType").value(org.hamcrest.Matchers.hasItems("PROJECT", "JOB")));

        // other workspaces see none of it
        mvc.perform(get("/api/v1/audit").with(b.auth())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/audit?entityId=" + project).with(b.auth()))
                .andExpect(jsonPath("$.totalElements").value(0));

        // members without audit:read cannot read the log
        Session member = Invites.invite(mvc, emails, a, "MEMBER");
        mvc.perform(get("/api/v1/audit").with(member.auth())).andExpect(status().isForbidden());

        // append-only at the database level
        String id = JsonPath.read(entries, "$.content[0].id");
        assertThatThrownBy(() -> jdbc.update("update audit_entries set action = 'X' where id = ?::uuid", id))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_entries where id = ?::uuid", id))
                .hasMessageContaining("append-only");
    }

    @Test
    void failedChangesLeaveNoAuditRow() throws Exception {
        Session a = Api.register(mvc);
        String project = createProjectWithJob(a);
        mvc.perform(json(patch("/api/v1/projects/" + project), "{\"budget\":\"-5\"}").with(a.auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit?entityType=PROJECT&entityId=" + project).with(a.auth()))
                .andExpect(jsonPath("$.content", hasSize(1))); // only the CREATE
    }

    private String createProjectWithJob(Session s) throws Exception {
        String contact = JsonPath.read(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","name":{"first":"Sam","last":"Rivera"},"email":"%s"}
                """.formatted(Api.uniqueEmail())).with(s.auth())).andReturn().getResponse().getContentAsString(), "$.id");
        String location = JsonPath.read(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"OFFICE","name":"HQ","address":{"city":"Cairo","country":"EG"}}
                """.formatted(contact)).with(s.auth())).andReturn().getResponse().getContentAsString(), "$.id");
        String project = JsonPath.read(mvc.perform(json(post("/api/v1/projects"), """
                {"name":"Tower","description":"Office tower",
                 "address":{"city":"Cairo","region":"Cairo","postalCode":"11511","country":"EG"},
                 "totalArea":"100","budget":"1000","startDate":"2026-01-01","estimatedEndDate":"2026-12-31",
                 "locationId":"%s","projectManagerUserId":"%s"}
                """.formatted(location, s.userId())).with(s.auth())).andReturn().getResponse().getContentAsString(),
                "$.id");
        mvc.perform(json(post("/api/v1/jobs"), """
                {"projectId":"%s","title":"Job","description":"Work","startDate":"2026-02-01",
                 "estimatedEndDate":"2026-03-01","budget":"100"}
                """.formatted(project)).with(s.auth())).andExpect(status().isCreated());
        return project;
    }
}
