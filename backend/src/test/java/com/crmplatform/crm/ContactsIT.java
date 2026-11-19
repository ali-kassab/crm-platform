package com.crmplatform.crm;

import static com.crmplatform.support.Api.json;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class ContactsIT {

    @Autowired
    MockMvc mvc;

    @Test
    void createAndReadContact() throws Exception { // TS-CRM-10
        Session s = Api.register(mvc);
        String id = id(createContact(s, "sam@rivera.example")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Sam Rivera"))
                .andExpect(jsonPath("$.phone").value("+15125550101"))
                .andExpect(jsonPath("$.metadata.source").value("referral"))
                .andExpect(jsonPath("$.warnings[0]").value("GEOCODING_FAILED")));
        mvc.perform(get("/api/v1/contacts/" + id).with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.city").value("Austin"))
                .andExpect(jsonPath("$.representative.userId").value(s.userId().toString()));
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception { // TS-CRM-11, EC-07
        Session s = Api.register(mvc);
        createContact(s, "dup@example.test").andExpect(status().isCreated());
        createContact(s, "DUP@Example.TEST").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void invalidPhoneIsRejected() throws Exception {
        Session s = Api.register(mvc);
        mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"LEAD","name":{"first":"A","last":"B"},"email":"%s","phone":"123"}
                """.formatted(Api.uniqueEmail())).with(s.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PHONE_INVALID"));
    }

    @Test
    void searchAndFilter() throws Exception { // TS-CRM-14, TS-CRM-15, EC-40
        Session s = Api.register(mvc);
        createContact(s, "zed@acme.example");
        mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"PARTNER","name":{"first":"Lena","last":"Ortiz"},"email":"lena@partner.example","company":"100% Steel"}
                """).with(s.auth())).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/contacts?q=ortiz").with(s.auth())).andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/v1/contacts?type=PARTNER").with(s.auth())).andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/v1/contacts").param("q", "100%").with(s.auth())).andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/v1/contacts").param("q", "%").with(s.auth())).andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/v1/contacts?q=5125550101").with(s.auth())).andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void patchChangesOnlySentFieldsAndNullClears() throws Exception { // TS-CRM-16, BR-004
        Session s = Api.register(mvc);
        String id = id(createContact(s, Api.uniqueEmail()));
        mvc.perform(json(patch("/api/v1/contacts/" + id), "{\"company\":null,\"position\":\"CEO\"}").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").doesNotExist())
                .andExpect(jsonPath("$.position").value("CEO"))
                .andExpect(jsonPath("$.email").isNotEmpty())
                .andExpect(jsonPath("$.fullName").value("Sam Rivera"));
        mvc.perform(json(patch("/api/v1/contacts/" + id), "{\"email\":null}").with(s.auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tagsAreValidatedPerWorkspace() throws Exception {
        Session a = Api.register(mvc);
        Session b = Api.register(mvc);
        String catB = JsonPath.read(mvc.perform(get("/api/v1/categories").with(b.auth())).andReturn().getResponse()
                .getContentAsString(), "$.content[0].id");
        String tagB = id(mvc.perform(json(post("/api/v1/tags"), "{\"name\":\"B\",\"categoryId\":\"%s\"}".formatted(catB))
                .with(b.auth())));
        mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"LEAD","name":{"first":"A","last":"B"},"email":"%s","tagIds":["%s"]}
                """.formatted(Api.uniqueEmail(), tagB)).with(a.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFERENCE_NOT_FOUND"))
                .andExpect(jsonPath("$.field").value("tagIds"));
    }

    @Test
    void batchDeleteReportsPerId() throws Exception { // TS-CRM-19, EC-09
        Session s = Api.register(mvc);
        String id = id(createContact(s, Api.uniqueEmail()));
        String unknown = UUID.randomUUID().toString();
        mvc.perform(json(post("/api/v1/contacts/batch-delete"), "{\"ids\":[\"%s\",\"%s\"]}".formatted(id, unknown))
                        .with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted[0]").value(id))
                .andExpect(jsonPath("$.notFound[0]").value(unknown));
        mvc.perform(get("/api/v1/contacts/" + id).with(s.auth())).andExpect(status().isNotFound());
    }

    @Test
    void locationsBelongToContactsAndValidateHours() throws Exception { // TS-CRM-21..24
        Session s = Api.register(mvc);
        String contact = id(createContact(s, Api.uniqueEmail()));
        mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"OFFICE","name":"HQ","address":{"city":"Austin","country":"US"}}
                """.formatted(contact)).with(s.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workingHours.MONDAY.open").value("09:00"))
                .andExpect(jsonPath("$.workingHours.SUNDAY.closed").value(true));
        mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"OFFICE","name":"Bad","workingHours":{"MONDAY":{"open":"18:00","close":"09:00","closed":false}}}
                """.formatted(contact)).with(s.auth()))
                .andExpect(status().isBadRequest());
        Session other = Api.register(mvc);
        mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"OFFICE","name":"X"}
                """.formatted(contact)).with(other.auth()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REFERENCE_NOT_FOUND"));
    }

    @Test
    void notesNeedExactlyOneParent() throws Exception { // TS-CRM-25
        Session s = Api.register(mvc);
        String contact = id(createContact(s, Api.uniqueEmail()));
        mvc.perform(json(post("/api/v1/notes"), "{\"title\":\"Call\",\"body\":\"Discussed scope\"}").with(s.auth()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTE_PARENT_REQUIRED"));
        String note = id(mvc.perform(json(post("/api/v1/notes"),
                "{\"contactId\":\"%s\",\"title\":\"Call\",\"body\":\"Discussed scope\"}".formatted(contact))
                .with(s.auth())).andExpect(status().isCreated()));
        mvc.perform(get("/api/v1/notes?contactId=" + contact).with(s.auth())).andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(json(patch("/api/v1/notes/" + note), "{\"body\":\"Updated\"}").with(s.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body").value("Updated"));
        mvc.perform(delete("/api/v1/notes/" + note).with(s.auth())).andExpect(status().isNoContent());
    }

    @Test
    void contactMilestoneDefaultsAndProgress() throws Exception { // BR-044, TS-WRK-20..22
        Session s = Api.register(mvc);
        String contact = id(createContact(s, Api.uniqueEmail()));
        String json = mvc.perform(json(put("/api/v1/contacts/" + contact + "/milestone"), "{\"name\":\"Onboarding\"}")
                        .with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps", hasSize(2)))
                .andExpect(jsonPath("$.steps[0].label").value("Initial Contact"))
                .andExpect(jsonPath("$.progress").value(0))
                .andReturn().getResponse().getContentAsString();
        String step = JsonPath.read(json, "$.steps[0].id");
        mvc.perform(post("/api/v1/contacts/" + contact + "/milestone/steps/" + step + "/complete").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(50))
                .andExpect(jsonPath("$.currentStep").value(1));
        mvc.perform(post("/api/v1/contacts/" + contact + "/milestone/steps/" + step + "/complete").with(s.auth()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/locations/" + UUID.randomUUID() + "/milestone").with(s.auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void milestoneTemplatesNeedTwoSteps() throws Exception { // TS-WRK-23
        Session s = Api.register(mvc);
        mvc.perform(json(post("/api/v1/milestone-templates"), "{\"name\":\"T\",\"steps\":[{\"label\":\"One\"}]}")
                .with(s.auth())).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/milestone-templates"),
                "{\"name\":\"Sales\",\"steps\":[{\"label\":\"Lead\"},{\"label\":\"Won\"}]}").with(s.auth()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.steps", hasSize(2)));
    }

    private ResultActions createContact(Session s, String email) throws Exception {
        return mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","name":{"title":"Mr","first":"Sam","last":"Rivera"},"company":"Rivera Builders",
                 "email":"%s","phone":"(512) 555-0101","address":{"line1":"10 Main St","city":"Austin","region":"TX",
                 "postalCode":"78701","country":"us"},"representativeUserId":"%s","metadata":{"source":"referral"}}
                """.formatted(email, s.userId())).with(s.auth()));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
