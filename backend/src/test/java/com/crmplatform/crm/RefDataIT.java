package com.crmplatform.crm;

import static com.crmplatform.support.Api.json;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class RefDataIT {

    @Autowired
    MockMvc mvc;

    @Test
    void newWorkspaceIsSeededWithDefaultCategoriesAndStatuses() throws Exception { // BR-016
        Session s = Api.register(mvc);
        mvc.perform(get("/api/v1/categories").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Client", "Lead", "Partner")));
        mvc.perform(get("/api/v1/statuses").with(s.auth()))
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("New Client", "New Lead", "New Partner")));
    }

    @Test
    void tagNamesAreUniquePerWorkspaceCaseInsensitive() throws Exception { // TS-CRM-07, BR-003
        Session a = Api.register(mvc);
        Session b = Api.register(mvc);
        String catA = firstCategory(a);
        String catB = firstCategory(b);
        createTag(a, catA, "VIP").andExpect(status().isCreated());
        createTag(a, catA, "vip").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NAME_TAKEN"));
        createTag(b, catB, "VIP").andExpect(status().isCreated()); // other tenant may reuse the name
    }

    @Test
    void otherWorkspacesRecordsAreInvisible() throws Exception { // TS-SEC-01
        Session a = Api.register(mvc);
        Session b = Api.register(mvc);
        String tagId = JsonPath.read(createTag(a, firstCategory(a), "Secret").andReturn().getResponse()
                .getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/tags/" + tagId).with(b.auth())).andExpect(status().isNotFound());
        mvc.perform(json(patch("/api/v1/tags/" + tagId), "{\"name\":\"x\",\"categoryId\":\"%s\"}"
                .formatted(firstCategory(b))).with(b.auth())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/tags/" + tagId).with(b.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tags").with(b.auth())).andExpect(jsonPath("$.content", hasSize(0)));
        // a category of workspace A cannot be referenced from workspace B
        createTag(b, firstCategory(a), "Cross").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFERENCE_NOT_FOUND"));
    }

    @Test
    void categoryInUseCannotBeDeleted() throws Exception { // TS-CRM-03
        Session s = Api.register(mvc);
        String cat = firstCategory(s);
        mvc.perform(delete("/api/v1/categories/" + cat).with(s.auth()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IN_USE"));
    }

    @Test
    void statusValidation() throws Exception { // TS-CRM-04
        Session s = Api.register(mvc);
        mvc.perform(json(post("/api/v1/statuses"), "{\"name\":\"ab\",\"categoryId\":\"%s\",\"backgroundColor\":\"red\"}"
                        .formatted(firstCategory(s))).with(s.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void invalidSortIsRejected() throws Exception { // TS-SEC-05
        Session s = Api.register(mvc);
        mvc.perform(get("/api/v1/tags?sort=workspaceId,asc").with(s.auth()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SORT"));
        mvc.perform(get("/api/v1/tags?size=1000").with(s.auth())).andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions createTag(Session s, String categoryId, String name)
            throws Exception {
        return mvc.perform(json(post("/api/v1/tags"), "{\"name\":\"%s\",\"categoryId\":\"%s\",\"backgroundColor\":\"#112233\"}"
                .formatted(name, categoryId)).with(s.auth()));
    }

    private String firstCategory(Session s) throws Exception {
        String json = mvc.perform(get("/api/v1/categories").with(s.auth())).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.content[0].id");
    }
}
