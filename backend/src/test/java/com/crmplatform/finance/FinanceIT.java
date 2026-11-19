package com.crmplatform.finance;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Transactions and accounting rollups (TS-FIN-01..10). */
@IntegrationTest
class FinanceIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RecordingEmailSender emails;

    @Test
    void methodSpecificFieldsAreRequired() throws Exception { // TS-FIN-01, BR-092
        Session s = Api.register(mvc);
        Setup x = setup(s);
        record(s, x.jobA, "RECEIVED", "100", "CHECK", ",\"bankName\":\"First Bank\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("checkNumber"));
        record(s, x.jobA, "PAID", "100", "BANK_TRANSFER", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bankName"));
        record(s, x.jobA, "RECEIVED", "100", "CHECK", ",\"bankName\":\"First Bank\",\"checkNumber\":\"1042\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkNumber").value("1042"));
    }

    @Test
    void datesAmountAndParentsAreValidated() throws Exception { // TS-FIN-02, TS-FIN-03, TS-FIN-04, TS-FIN-05
        Session s = Api.register(mvc);
        Setup x = setup(s);
        mvc.perform(json(post("/api/v1/transactions"), body(x.jobA, "PAID", "10", "CASH", "")
                .replace("\"dueDate\":\"2026-03-15\"", "\"dueDate\":\"2026-02-01\"")).with(s.auth()))
                .andExpect(status().isBadRequest());
        record(s, x.jobA, "PAID", "10", "CASH", ",\"projectId\":\"%s\"".formatted(x.project2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JOB_NOT_IN_PROJECT"));
        record(s, x.jobA, "PAID", "0", "CASH", "").andExpect(status().isBadRequest());
        record(s, x.jobA, "PAID", "10.555", "CASH", "").andExpect(status().isBadRequest());
        record(s, x.jobA, "PAID", "10", "WIRE", "").andExpect(status().isBadRequest());
        record(s, UUID.randomUUID().toString(), "PAID", "10", "CASH", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("jobId"));
        record(s, x.jobA, "PAID", "10.5", "CASH", "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("10.50"))
                .andExpect(jsonPath("$.contactId").value(x.contact))
                .andExpect(jsonPath("$.projectId").value(x.project1));
    }

    @Test
    void listFiltersByDateRangeAndType() throws Exception { // TS-FIN-06
        Session s = Api.register(mvc);
        Setup x = setup(s);
        record(s, x.jobA, "RECEIVED", "10", "CASH", "");
        mvc.perform(json(post("/api/v1/transactions"), body(x.jobA, "PAID", "20", "CASH", "")
                .replace("2026-03-01", "2026-06-01").replace("2026-03-15", "2026-06-15")).with(s.auth()))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/transactions?type=PAID").with(s.auth()))
                .andExpect(jsonPath("$.content[*].amount").value(contains("20.00")));
        mvc.perform(get("/api/v1/transactions?from=2026-05-01&to=2026-07-01").with(s.auth()))
                .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/v1/transactions?projectId=" + x.project1).with(s.auth()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/transactions?type=BOTH").with(s.auth())).andExpect(status().isBadRequest());
        Session other = Api.register(mvc);
        mvc.perform(get("/api/v1/transactions").with(other.auth())).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void voidExcludesFromRollupsAndOnlyOnce() throws Exception { // TS-FIN-07, BR-093, EC-25
        Session s = Api.register(mvc);
        Setup x = setup(s);
        String t = id(record(s, x.jobA, "RECEIVED", "120", "CASH", ""));
        mvc.perform(get("/api/v1/jobs/" + x.jobA + "/accounting").with(s.auth()))
                .andExpect(jsonPath("$.received").value("120.00"));
        mvc.perform(json(post("/api/v1/transactions/" + t + "/void"), "{\"reason\":\"no\"}").with(s.auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/transactions/" + t + "/void"), "{\"reason\":\"Entered twice\"}").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(true))
                .andExpect(jsonPath("$.voidReason").value("Entered twice"));
        mvc.perform(json(post("/api/v1/transactions/" + t + "/void"), "{\"reason\":\"Entered twice\"}").with(s.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_VOIDED"));
        mvc.perform(get("/api/v1/jobs/" + x.jobA + "/accounting").with(s.auth()))
                .andExpect(jsonPath("$.received").value("0.00"))
                .andExpect(jsonPath("$.transactionCount").value(0));
        mvc.perform(get("/api/v1/transactions").with(s.auth())).andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/v1/transactions?includeVoided=true").with(s.auth()))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void rollupsMatchHandComputedValues() throws Exception { // TS-FIN-08..10, BR-094, EC-23
        Session s = Api.register(mvc);
        Setup x = setup(s); // project1 budget 1000 (jobA 300, jobB 200), project2 budget 500, same contact
        record(s, x.jobA, "RECEIVED", "250", "CASH", "");
        record(s, x.jobA, "PAID", "100", "BANK_TRANSFER", ",\"bankName\":\"First Bank\"");
        record(s, x.jobB, "RECEIVED", "400", "CHECK", ",\"bankName\":\"First Bank\",\"checkNumber\":\"7\"");
        String voided = id(record(s, x.jobB, "RECEIVED", "999", "CASH", ""));
        mvc.perform(json(post("/api/v1/transactions/" + voided + "/void"), "{\"reason\":\"Wrong job\"}")
                .with(s.auth())).andExpect(status().isOk());

        accounting(s, "jobs/" + x.jobA, "300.00", "250.00", "100.00", "50.00", "200.00", 2, false);
        accounting(s, "jobs/" + x.jobB, "200.00", "400.00", "0.00", "-200.00", "200.00", 1, true);
        accounting(s, "projects/" + x.project1, "1000.00", "650.00", "100.00", "350.00", "900.00", 3, false);
        accounting(s, "contacts/" + x.contact, "1500.00", "650.00", "100.00", "850.00", "1400.00", 3, false);

        mvc.perform(delete("/api/v1/jobs/" + x.jobB).with(s.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/jobs/" + x.jobB + "/accounting").with(s.auth())).andExpect(status().isNotFound());
        accounting(s, "projects/" + x.project1, "1000.00", "650.00", "100.00", "350.00", "900.00", 3, false);
        record(s, x.jobB, "PAID", "10", "CASH", "").andExpect(status().isBadRequest());
    }

    @Test
    void databaseRejectsChangesToRecordedTransactions() throws Exception { // BR-093
        Session s = Api.register(mvc);
        Setup x = setup(s);
        String t = id(record(s, x.jobA, "PAID", "75", "CASH", ""));
        assertThatThrownBy(() -> jdbc.update("update transactions set amount = 1 where id = ?::uuid", t))
                .hasMessageContaining("immutable");
        jdbc.update("update transactions set voided_at = now(), void_reason = 'manual void' where id = ?::uuid", t);
        assertThatThrownBy(() -> jdbc.update("update transactions set void_reason = 'changed' where id = ?::uuid", t))
                .hasMessageContaining("voided");
    }

    @Test
    void viewerCannotRecord() throws Exception {
        Session owner = Api.register(mvc);
        Setup x = setup(owner);
        Session viewer = Invites.invite(mvc, emails, owner, "VIEWER");
        record(viewer, x.jobA, "PAID", "5", "CASH", "").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/transactions").with(viewer.auth())).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private record Setup(String contact, String project1, String project2, String jobA, String jobB) {
    }

    private Setup setup(Session s) throws Exception {
        String contact = id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","name":{"first":"Sam","last":"Rivera"},"email":"%s"}
                """.formatted(Api.uniqueEmail())).with(s.auth())).andExpect(status().isCreated()));
        String location = id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"OFFICE","name":"HQ","address":{"city":"Austin","country":"US"}}
                """.formatted(contact)).with(s.auth())).andExpect(status().isCreated()));
        String p1 = project(s, location, "1000");
        String p2 = project(s, location, "500");
        return new Setup(contact, p1, p2, job(s, p1, "300"), job(s, p1, "200"));
    }

    private String project(Session s, String location, String budget) throws Exception {
        return id(mvc.perform(json(post("/api/v1/projects"), """
                {"name":"Tower","description":"Office tower",
                 "address":{"city":"Austin","region":"TX","postalCode":"78701","country":"US"},
                 "totalArea":"100","budget":"%s","startDate":"2026-01-01","estimatedEndDate":"2026-12-31",
                 "locationId":"%s","projectManagerUserId":"%s"}
                """.formatted(budget, location, s.userId())).with(s.auth())).andExpect(status().isCreated()));
    }

    private String job(Session s, String project, String budget) throws Exception {
        return id(mvc.perform(json(post("/api/v1/jobs"), """
                {"projectId":"%s","title":"Job","description":"Work","startDate":"2026-02-01",
                 "estimatedEndDate":"2026-03-01","budget":"%s"}
                """.formatted(project, budget)).with(s.auth())).andExpect(status().isCreated()));
    }

    private static String body(String job, String type, String amount, String method, String extra) {
        return """
                {"jobId":"%s","type":"%s","amount":"%s","title":"Invoice","payee":"Rivera Builders",
                 "method":"%s","commitmentDate":"2026-03-01","dueDate":"2026-03-15"%s}
                """.formatted(job, type, amount, method, extra);
    }

    private ResultActions record(Session s, String job, String type, String amount, String method, String extra)
            throws Exception {
        return mvc.perform(json(post("/api/v1/transactions"), body(job, type, amount, method, extra)).with(s.auth()));
    }

    private void accounting(Session s, String path, String contract, String received, String cost, String remaining,
            String profitLoss, int count, boolean overpaid) throws Exception {
        mvc.perform(get("/api/v1/" + path + "/accounting").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value(contract))
                .andExpect(jsonPath("$.received").value(received))
                .andExpect(jsonPath("$.actualCost").value(cost))
                .andExpect(jsonPath("$.remaining").value(remaining))
                .andExpect(jsonPath("$.profitLoss").value(profitLoss))
                .andExpect(jsonPath("$.transactionCount").value(count))
                .andExpect(jsonPath("$.overpaid").value(overpaid));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
