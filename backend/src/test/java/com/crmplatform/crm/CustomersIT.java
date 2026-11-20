package com.crmplatform.crm;

import static com.crmplatform.support.Api.json;
import static org.hamcrest.Matchers.contains;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Individual and company customers, phone-first contact data, Arabic search and customer history (R2). */
@IntegrationTest
class CustomersIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingEmailSender emails;

    @Test
    void phoneOnlyIndividualsAndCompaniesAreSupported() throws Exception {
        Session s = Api.register(mvc);
        create(s, """
                {"type":"CLIENT","name":{"first":"حسن"},"mobile":"+201001234567"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.fullName").value("حسن"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.mobile").value("+201001234567"));
        // two customers without email never collide on the unique email rule
        create(s, """
                {"type":"CLIENT","name":{"first":"منى","last":"سعيد"},"phone":"+20223456789"}
                """).andExpect(status().isCreated());
        create(s, """
                {"type":"CLIENT","kind":"COMPANY","company":"شركة الدلتا للتكييف","phone":"+20233334444"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("COMPANY"))
                .andExpect(jsonPath("$.fullName").value("شركة الدلتا للتكييف"))
                .andExpect(jsonPath("$.name.first").doesNotExist());
        mvc.perform(get("/api/v1/contacts?kind=COMPANY").with(s.auth()))
                .andExpect(jsonPath("$.content[*].fullName").value(contains("شركة الدلتا للتكييف")));
        mvc.perform(get("/api/v1/contacts?kind=INDIVIDUAL").with(s.auth())).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/contacts?kind=TEAM").with(s.auth())).andExpect(status().isBadRequest());
        // "field,direction" in one sort parameter, exactly as the web client sends it (R13 live-run bug)
        mvc.perform(get("/api/v1/contacts?kind=INDIVIDUAL&sort=createdAt,desc").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].fullName").value(contains("منى سعيد", "حسن")));
        mvc.perform(get("/api/v1/contacts?kind=INDIVIDUAL&sort=createdAt,asc").with(s.auth()))
                .andExpect(jsonPath("$.content[*].fullName").value(contains("حسن", "منى سعيد")));
        mvc.perform(get("/api/v1/contacts?sort=desc").with(s.auth())).andExpect(status().isBadRequest());
    }

    @Test
    void kindRulesAndReachabilityAreEnforced() throws Exception {
        Session s = Api.register(mvc);
        create(s, "{\"type\":\"CLIENT\",\"phone\":\"+20223456789\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name.first"))
                .andExpect(jsonPath("$.errors[0].message").value("الاسم الأول مطلوب للعميل الفرد"));
        create(s, "{\"type\":\"CLIENT\",\"kind\":\"COMPANY\",\"name\":{\"first\":\"x\"},\"phone\":\"+20223456789\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("company"));
        create(s, "{\"type\":\"CLIENT\",\"name\":{\"first\":\"Ali\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
        String id = id(create(s, "{\"type\":\"CLIENT\",\"name\":{\"first\":\"Ali\"},\"email\":\"%s\"}"
                .formatted(Api.uniqueEmail())).andExpect(status().isCreated()));
        // switching to a company requires a company name
        mvc.perform(json(patch("/api/v1/contacts/" + id), "{\"kind\":\"COMPANY\"}").with(s.auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(patch("/api/v1/contacts/" + id), "{\"kind\":\"COMPANY\",\"company\":\"Ali Trading\"}")
                .with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ali Trading"));
        mvc.perform(json(patch("/api/v1/contacts/" + id), "{\"email\":null}").with(s.auth()))
                .andExpect(status().isBadRequest()); // would leave no way to reach the customer
    }

    @Test
    void searchIgnoresArabicLetterVariantsAndDiacritics() throws Exception {
        Session s = Api.register(mvc);
        create(s, "{\"type\":\"CLIENT\",\"name\":{\"first\":\"أحمد\",\"last\":\"مُصطفى\"},\"phone\":\"+20211112222\"}");
        create(s, "{\"type\":\"CLIENT\",\"kind\":\"COMPANY\",\"company\":\"مدرسة النور\",\"phone\":\"+20211113333\"}");
        create(s, "{\"type\":\"CLIENT\",\"name\":{\"first\":\"إيمان\"},\"phone\":\"+20211114444\"}");

        search(s, "احمد").andExpect(jsonPath("$.totalElements").value(1));   // أ → ا
        search(s, "مصطفي").andExpect(jsonPath("$.totalElements").value(1));  // tashkeel dropped, ى → ي
        search(s, "مدرسه").andExpect(jsonPath("$.totalElements").value(1));  // ة → ه
        search(s, "ايمان").andExpect(jsonPath("$.totalElements").value(1));  // إ → ا
        search(s, "أَحْمَد").andExpect(jsonPath("$.totalElements").value(1)); // diacritics in the query
        search(s, "AHMED").andExpect(jsonPath("$.totalElements").value(0));

        // the same normalisation applies to other lists, e.g. projects
        String location = location(s, JsonPath.read(body(search(s, "مدرسه")), "$.content[0].id"));
        project(s, location, "تشطيب مدرسة النور");
        mvc.perform(get("/api/v1/projects").param("q", "مدرسه").with(s.auth()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void customerHistoryCollectsProjectsJobsWarrantiesAndMoney() throws Exception {
        Session s = Api.register(mvc);
        String customer = id(create(s, """
                {"type":"CLIENT","kind":"COMPANY","company":"مجمع الشروق السكني","phone":"+20255556666"}
                """));
        String project = project(s, location(s, customer), "صيانة كاميرات المجمع");
        String job = id(mvc.perform(json(post("/api/v1/jobs"), """
                {"projectId":"%s","title":"تركيب 16 كاميرا","description":"البوابة الرئيسية",
                 "startDate":"2026-02-01","estimatedEndDate":"2026-02-05","budget":"40000"}
                """.formatted(project)).with(s.auth())).andExpect(status().isCreated()));
        mvc.perform(json(post("/api/v1/warranties"), """
                {"title":"ضمان الكاميرات","provider":"Hikvision","startDate":"2026-02-05","endDate":"2028-02-05","jobId":"%s"}
                """.formatted(job)).with(s.auth())).andExpect(status().isCreated());
        mvc.perform(json(post("/api/v1/transactions"), """
                {"jobId":"%s","type":"RECEIVED","amount":"15000","title":"دفعة مقدمة","payee":"مجمع الشروق",
                 "method":"CASH","commitmentDate":"2026-02-01","dueDate":"2026-02-01"}
                """.formatted(job)).with(s.auth())).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/contacts/" + customer + "/history").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.name").value("مجمع الشروق السكني"))
                .andExpect(jsonPath("$.projects", hasSize(1)))
                .andExpect(jsonPath("$.projects[0].name").value("صيانة كاميرات المجمع"))
                .andExpect(jsonPath("$.jobs[0].title").value("تركيب 16 كاميرا"))
                .andExpect(jsonPath("$.jobs[0].budget").value("40000.00"))
                .andExpect(jsonPath("$.warranties[0].provider").value("Hikvision"))
                .andExpect(jsonPath("$.accounting.received").value("15000.00"))
                .andExpect(jsonPath("$.recentTransactions[0].title").value("دفعة مقدمة"));

        // a viewer (no finance:read) sees the operational history without money
        Session viewer = Invites.invite(mvc, emails, s, "VIEWER");
        mvc.perform(get("/api/v1/contacts/" + customer + "/history").with(viewer.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs", hasSize(1)))
                .andExpect(jsonPath("$.accounting").doesNotExist())
                .andExpect(jsonPath("$.recentTransactions").doesNotExist());

        // other workspaces cannot read it
        Session other = Api.register(mvc);
        mvc.perform(get("/api/v1/contacts/" + customer + "/history").with(other.auth()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/contacts/" + UUID.randomUUID() + "/history").with(s.auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void javaAndSqlNormalisationAreIdentical() {
        for (String text : java.util.List.of("أحمد", "إيمان", "آمال", "ٱلله", "مستشفى", "مدرسة", "مُحَمَّد",
                "عـــلي", "Cairo HVAC", "MiXeD نص 123", "")) {
            String sql = jdbc.queryForObject("select app_search_norm(?)", String.class, text);
            org.assertj.core.api.Assertions.assertThat(sql).as("normalisation of «%s»", text)
                    .isEqualTo(com.crmplatform.shared.web.SearchText.normalize(text));
        }
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    // ------------------------------------------------------------------ helpers

    private ResultActions create(Session s, String body) throws Exception {
        return mvc.perform(json(post("/api/v1/contacts"), body).with(s.auth()));
    }

    private ResultActions search(Session s, String q) throws Exception {
        return mvc.perform(get("/api/v1/contacts").param("q", q).with(s.auth()));
    }

    private String location(Session s, String contact) throws Exception {
        return id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"COMMERCIAL","name":"الموقع الرئيسي","address":{"city":"القاهرة","country":"EG"}}
                """.formatted(contact)).with(s.auth())).andExpect(status().isCreated()));
    }

    private String project(Session s, String location, String name) throws Exception {
        return id(mvc.perform(json(post("/api/v1/projects"), """
                {"name":"%s","description":"وصف المشروع",
                 "address":{"city":"القاهرة","region":"القاهرة","postalCode":"11511","country":"EG"},
                 "totalArea":"100","budget":"100000","startDate":"2026-01-01","estimatedEndDate":"2026-12-31",
                 "locationId":"%s","projectManagerUserId":"%s"}
                """.formatted(name, location, s.userId())).with(s.auth())).andExpect(status().isCreated()));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.id");
    }
}
