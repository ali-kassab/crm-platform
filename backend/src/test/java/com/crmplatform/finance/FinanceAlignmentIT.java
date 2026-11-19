package com.crmplatform.finance;

import static com.crmplatform.support.Api.json;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.ResultActions;

/** Money on projects without jobs, materials in profitability, and outstanding balances (R8). */
@IntegrationTest
class FinanceAlignmentIT {

    @Autowired
    MockMvc mvc;

    @Test
    void rollupsMatchHandComputedFiguresAndOutstandingListsDebtorsOnly() throws Exception {
        Session s = Api.register(mvc);
        String a = customer(s, "مجموعة الفا");
        String aSite = site(s, a);
        String b = customer(s, "Beta Clinic");
        String c = customer(s, "Gamma Store");

        // A: fit-out project 10,000 with a 3,000 down payment recorded before any job exists
        String project = id(mvc.perform(json(post("/api/v1/projects"), """
                {"name":"تشطيب فيلا","description":"تشطيب كامل","type":"RESIDENTIAL",
                 "address":{"line1":"1 Main St","city":"Cairo","region":"Cairo","postalCode":"11511","country":"EG"},
                 "totalArea":"300","budget":"10000","startDate":"2026-01-01","estimatedEndDate":"2026-12-31",
                 "locationId":"%s","projectManagerUserId":"%s","teamMemberUserIds":[]}
                """.formatted(aSite, s.userId())).with(s.auth())).andExpect(status().isCreated()));
        String downPayment = id(money(s, "\"projectId\":\"%s\"".formatted(project), "RECEIVED", "3000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").doesNotExist())
                .andExpect(jsonPath("$.projectId").value(project))
                .andExpect(jsonPath("$.contactId").value(a)));
        // a job of that project: 1,200 paid out + materials 2 × 250
        String projectJob = id(mvc.perform(json(post("/api/v1/jobs"), """
                {"projectId":"%s","title":"أعمال الكهرباء"}
                """.formatted(project)).with(s.auth())).andExpect(status().isCreated()));
        money(s, "\"jobId\":\"%s\"".formatted(projectJob), "PAID", "1200").andExpect(status().isCreated());
        mvc.perform(json(post("/api/v1/jobs/" + projectJob + "/materials"),
                "{\"name\":\"كابلات\",\"quantity\":\"2\",\"unitCost\":\"250\"}").with(s.auth()))
                .andExpect(status().isCreated());
        // A also has a standalone service job of 1,500, fully paid
        String aService = job(s, a, aSite, "1500");
        money(s, "\"jobId\":\"%s\"".formatted(aService), "RECEIVED", "1500").andExpect(status().isCreated());

        // B: paid more than agreed (overpaid → not a debtor); C: nothing paid yet
        String bJob = job(s, b, site(s, b), "2000");
        money(s, "\"jobId\":\"%s\"".formatted(bJob), "RECEIVED", "2500").andExpect(status().isCreated());
        job(s, c, site(s, c), "800");

        mvc.perform(json(post("/api/v1/transactions"), """
                {"type":"RECEIVED","amount":"1","title":"x","payee":"x","method":"CASH",
                 "commitmentDate":"2026-03-01","dueDate":"2026-03-01"}
                """).with(s.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSACTION_PARENT_REQUIRED"));

        mvc.perform(get("/api/v1/projects/" + project + "/accounting").with(s.auth()))
                .andExpect(jsonPath("$.contract").value("10000.00"))
                .andExpect(jsonPath("$.received").value("3000.00"))
                .andExpect(jsonPath("$.actualCost").value("1200.00"))
                .andExpect(jsonPath("$.materialsCost").value("500.00"))
                .andExpect(jsonPath("$.remaining").value("7000.00"))
                .andExpect(jsonPath("$.profitLoss").value("8300.00"));
        mvc.perform(get("/api/v1/contacts/" + a + "/accounting").with(s.auth()))
                .andExpect(jsonPath("$.contract").value("11500.00"))
                .andExpect(jsonPath("$.received").value("4500.00"))
                .andExpect(jsonPath("$.remaining").value("7000.00"))
                .andExpect(jsonPath("$.profitLoss").value("9800.00"));
        mvc.perform(get("/api/v1/contacts/" + b + "/accounting").with(s.auth()))
                .andExpect(jsonPath("$.remaining").value("-500.00"))
                .andExpect(jsonPath("$.overpaid").value(true));

        mvc.perform(get("/api/v1/accounting/outstanding").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].contactId").value(contains(a, c)))
                .andExpect(jsonPath("$.content[0].customerName").value("مجموعة الفا"))
                .andExpect(jsonPath("$.content[0].remaining").value("7000.00"))
                .andExpect(jsonPath("$.content[0].lastPaymentDate").value("2026-03-01"))
                .andExpect(jsonPath("$.content[1].remaining").value("800.00"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.pageRemaining").value("7800.00"));
        mvc.perform(get("/api/v1/accounting/outstanding?size=1&page=1").with(s.auth()))
                .andExpect(jsonPath("$.content[*].contactId").value(contains(c)))
                .andExpect(jsonPath("$.totalElements").value(2));

        // voiding the down payment puts it back on the balance
        mvc.perform(json(post("/api/v1/transactions/" + downPayment + "/void"), "{\"reason\":\"شيك مرتد\"}")
                .with(s.auth())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/accounting/outstanding").with(s.auth()))
                .andExpect(jsonPath("$.content[0].remaining").value("10000.00"));

        // other workspaces see nothing; finance permission required
        Session other = Api.register(mvc);
        mvc.perform(get("/api/v1/accounting/outstanding").with(other.auth()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions money(Session s, String parent, String type, String amount) throws Exception {
        return mvc.perform(json(post("/api/v1/transactions"), """
                {%s,"type":"%s","amount":"%s","title":"دفعة","payee":"العميل","method":"CASH",
                 "commitmentDate":"2026-03-01","dueDate":"2026-03-01"}
                """.formatted(parent, type, amount)).with(s.auth()));
    }

    private String job(Session s, String contact, String site, String budget) throws Exception {
        return id(mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"صيانة","budget":"%s"}
                """.formatted(contact, site, budget)).with(s.auth())).andExpect(status().isCreated()));
    }

    private String customer(Session s, String company) throws Exception {
        return id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","kind":"COMPANY","company":"%s","phone":"+20223456789"}
                """.formatted(company)).with(s.auth())).andExpect(status().isCreated()));
    }

    private String site(Session s, String contact) throws Exception {
        return id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"COMMERCIAL","name":"الموقع","address":{"line1":"x","city":"القاهرة","country":"EG"}}
                """.formatted(contact)).with(s.auth())).andExpect(status().isCreated()));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
