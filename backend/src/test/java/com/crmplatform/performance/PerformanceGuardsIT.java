package com.crmplatform.performance;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.work.Projects;
import com.crmplatform.work.projects.application.ProjectService;
import com.crmplatform.work.projects.application.ProjectService.ProjectFilter;
import com.crmplatform.work.projects.domain.Project;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guards against query regressions with measured statement counts (Hibernate statistics), not
 * assumptions. Each check fails if a list operation goes back to per-row queries.
 */
@IntegrationTest
class PerformanceGuardsIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManagerFactory emf;
    @Autowired
    Projects projects;
    @Autowired
    ProjectService projectService;

    @Test
    void projectSummariesUseAConstantNumberOfQueries() throws Exception {
        Session s = Api.register(mvc);
        List<UUID> ids = createProjects(s, 6);

        Statistics stats = stats();
        TenantContext.runAs(s.workspaceId(), s.userId(), () -> projects.findAll(ids.subList(0, 2)));
        long forTwo = stats.getPrepareStatementCount();

        stats = stats();
        var summaries = TenantContext.runAs(s.workspaceId(), s.userId(), () -> projects.findAll(ids));
        long forSix = stats.getPrepareStatementCount();

        assertThat(summaries).hasSize(6);
        assertThat(summaries.values()).allSatisfy(p -> assertThat(p.managerUserId()).isEqualTo(s.userId()));
        // projects + batched tags + managers (+ their batched responsibilities): constant, not per row
        assertThat(forSix).as("statements for 6 vs 2 project summaries").isEqualTo(forTwo);
        assertThat(forSix).isLessThanOrEqualTo(4);
    }

    @Test
    void overBudgetFilterLoadsOnlyTheRequestedPage() throws Exception {
        Session s = Api.register(mvc);
        createProjects(s, 6);
        Statistics stats = stats();

        var page = TenantContext.runAs(s.workspaceId(), s.userId(), () -> projectService.search(
                new ProjectFilter(null, null, null, null, null, null, false, null), PageRequest.of(0, 2)));

        assertThat(page.getTotalElements()).isEqualTo(6);
        assertThat(page.getContent()).hasSize(2);
        long loaded = stats.getEntityStatistics(Project.class.getName()).getLoadCount();
        assertThat(loaded).as("Project entities hydrated for a 2-row page").isLessThanOrEqualTo(2);
    }

    private Statistics stats() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        return stats;
    }

    private List<UUID> createProjects(Session s, int count) throws Exception {
        String contact = JsonPath.read(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","name":{"first":"Sam","last":"Rivera"},"email":"%s"}
                """.formatted(Api.uniqueEmail())).with(s.auth())).andReturn().getResponse().getContentAsString(), "$.id");
        String location = JsonPath.read(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"OFFICE","name":"HQ","address":{"city":"Cairo","country":"EG"}}
                """.formatted(contact)).with(s.auth())).andReturn().getResponse().getContentAsString(), "$.id");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String body = mvc.perform(json(post("/api/v1/projects"), """
                    {"name":"Tower %d","description":"Office tower",
                     "address":{"city":"Cairo","region":"Cairo","postalCode":"11511","country":"EG"},
                     "totalArea":"100","budget":"1000","startDate":"2026-01-01","estimatedEndDate":"2026-12-31",
                     "locationId":"%s","projectManagerUserId":"%s"}
                    """.formatted(i, location, s.userId())).with(s.auth()))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            ids.add(UUID.fromString(JsonPath.read(body, "$.id")));
        }
        return ids;
    }
}
