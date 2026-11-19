package com.crmplatform.operations;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.operations.visits.application.VisitService;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.support.Invites;
import com.crmplatform.support.RecordingEmailSender;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Work orders, visits and technician scoping (R3: directive §5C/§5D, D1, D2, D8). */
@IntegrationTest
class WorkOrdersIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingEmailSender emails;
    @Autowired
    VisitService visitService;

    @Test
    void independentTechnicianRunsAServiceJobEndToEnd() throws Exception {
        Session me = Api.register(mvc); // one-person workspace, no team
        String customer = customer(me, "شركة الأمل");
        String site = site(me, customer, "الفرع الرئيسي");
        String job = id(createJob(me, customer, site, ",\"type\":\"CORRECTIVE_REPAIR\",\"priority\":\"URGENT\","
                + "\"reportedIssue\":\"التكييف لا يبرد\",\"budget\":\"1500\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").doesNotExist())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.reportedIssue").value("التكييف لا يبرد"))
                .andExpect(jsonPath("$.customer.name").value("شركة الأمل"))
                .andExpect(jsonPath("$.site.name").value("الفرع الرئيسي"))
                .andExpect(jsonPath("$.boardId").value(notNullValue())));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        String visit = id(schedule(me, job, start, start.plus(2, ChronoUnit.HOURS), "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.assignees[0].userId").value(me.userId().toString()))); // defaults to me
        mvc.perform(get("/api/v1/jobs/" + job).with(me.auth())).andExpect(jsonPath("$.status").value("SCHEDULED"));
        mvc.perform(get("/api/v1/visits?assignee=me").with(me.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(visit)));

        mvc.perform(post("/api/v1/visits/" + visit + "/start").with(me.auth()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(get("/api/v1/jobs/" + job).with(me.auth()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.actualStartAt").value(notNullValue()));
        mvc.perform(json(post("/api/v1/visits/" + visit + "/complete"),
                "{\"outcome\":\"RESOLVED\",\"notes\":\"تم تغيير الكباس\",\"completeJob\":true}").with(me.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.outcome").value("RESOLVED"));
        mvc.perform(get("/api/v1/jobs/" + job).with(me.auth()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").value(notNullValue()));

        // money on a job without a project; the customer's contract includes the job budget
        mvc.perform(json(post("/api/v1/transactions"), """
                {"jobId":"%s","type":"RECEIVED","amount":"1500","title":"تحصيل","payee":"شركة الأمل",
                 "method":"CASH","commitmentDate":"2026-03-01","dueDate":"2026-03-01"}
                """.formatted(job)).with(me.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").doesNotExist())
                .andExpect(jsonPath("$.contactId").value(customer));
        mvc.perform(get("/api/v1/contacts/" + customer + "/accounting").with(me.auth()))
                .andExpect(jsonPath("$.contract").value("1500.00"))
                .andExpect(jsonPath("$.remaining").value("0.00"));
        mvc.perform(get("/api/v1/contacts/" + customer + "/history").with(me.auth()))
                .andExpect(jsonPath("$.jobs[0].id").value(job))
                .andExpect(jsonPath("$.jobs[0].status").value("COMPLETED"));
        // a customer with jobs can't be deleted
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/contacts/" + customer)
                .with(me.auth())).andExpect(status().isConflict());
    }

    @Test
    void techniciansOnlySeeAndChangeTheirOwnWork() throws Exception {
        Session owner = Api.register(mvc);
        Session ahmed = Invites.invite(mvc, emails, owner, "TECHNICIAN");
        Session sara = Invites.invite(mvc, emails, owner, "TECHNICIAN");
        String customer = customer(owner, "مستشفى النيل");
        String site = site(owner, customer, "مبنى الطوارئ");
        String jobA = id(createJob(owner, customer, site, ",\"assigneeUserIds\":[\"%s\"]".formatted(ahmed.userId())));
        String jobS = id(createJob(owner, customer, site, ",\"assigneeUserIds\":[\"%s\"]".formatted(sara.userId())));

        mvc.perform(get("/api/v1/jobs").with(ahmed.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(contains(jobA)));
        // even asking for someone else's work returns only their own
        mvc.perform(get("/api/v1/jobs?assigneeUserId=" + sara.userId()).with(ahmed.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(jobA)));
        mvc.perform(get("/api/v1/jobs/" + jobS).with(ahmed.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/jobs/" + jobA).with(ahmed.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.phone").value("+20223456789"))
                .andExpect(jsonPath("$.site.name").value("مبنى الطوارئ"));
        mvc.perform(json(post("/api/v1/jobs/" + jobA + "/status"), "{\"status\":\"IN_PROGRESS\"}").with(ahmed.auth()))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/jobs/" + jobS + "/status"), "{\"status\":\"IN_PROGRESS\"}").with(ahmed.auth()))
                .andExpect(status().isNotFound());

        // technicians are not dispatchers or CRM users
        createJob(ahmed, customer, site, "").andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/v1/jobs/" + jobA), "{\"budget\":\"1\"}").with(ahmed.auth()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/contacts").with(ahmed.auth())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/transactions").with(ahmed.auth())).andExpect(status().isForbidden());

        // visits follow the same rule
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        String visitS = id(schedule(owner, jobS, start, start.plus(1, ChronoUnit.HOURS), ""));
        String visitA = id(schedule(ahmed, jobA, start, start.plus(1, ChronoUnit.HOURS), "")); // own follow-up
        mvc.perform(get("/api/v1/visits").with(ahmed.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(visitA)));
        mvc.perform(get("/api/v1/visits/" + visitS).with(ahmed.auth())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/visits/" + visitS + "/start").with(ahmed.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/visits").with(owner.auth())).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void visitLifecycleKeepsTheHistory() throws Exception {
        Session s = Api.register(mvc);
        String customer = customer(s, "Cairo Mall");
        String job = id(createJob(s, customer, site(s, customer, "Gate 3"), ",\"type\":\"INSTALLATION\""));
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        schedule(s, job, start, start.minus(1, ChronoUnit.HOURS), "").andExpect(status().isBadRequest());
        String first = id(schedule(s, job, start, start.plus(3, ChronoUnit.HOURS), ",\"notes\":\"16 كاميرا\""));

        String second = id(mvc.perform(json(post("/api/v1/visits/" + first + "/reschedule"), """
                {"scheduledStart":"%s","scheduledEnd":"%s"}
                """.formatted(start.plus(1, ChronoUnit.DAYS), start.plus(1, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS)))
                .with(s.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rescheduledFromId").value(first))
                .andExpect(jsonPath("$.notes").value("16 كاميرا")));
        mvc.perform(get("/api/v1/jobs/" + job + "/visits").with(s.auth()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("RESCHEDULED"))
                .andExpect(jsonPath("$[1].status").value("SCHEDULED"));
        mvc.perform(post("/api/v1/visits/" + first + "/start").with(s.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_VISIT_TRANSITION"));
        mvc.perform(json(post("/api/v1/visits/" + second + "/complete"), "{\"outcome\":\"MAYBE\"}").with(s.auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/visits/" + second + "/complete"), "{\"outcome\":\"FOLLOW_UP_REQUIRED\"}")
                .with(s.auth()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/jobs/" + job).with(s.auth())).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // closed jobs take no new visits; invalid lifecycle moves are refused
        mvc.perform(json(post("/api/v1/jobs/" + job + "/status"), "{\"status\":\"COMPLETED\"}").with(s.auth()))
                .andExpect(status().isOk());
        schedule(s, job, start, start.plus(1, ChronoUnit.HOURS), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_CLOSED"));
        mvc.perform(json(post("/api/v1/jobs/" + job + "/status"), "{\"status\":\"CANCELLED\"}").with(s.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        mvc.perform(json(post("/api/v1/jobs/" + job + "/status"), "{\"status\":\"IN_PROGRESS\"}").with(s.auth()))
                .andExpect(status().isOk()) // reopen
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void missedVisitsAreMarkedOnceAndOverdueVisitsAreListed() throws Exception {
        Session s = Api.register(mvc);
        String customer = customer(s, "Delta Towers");
        String job = id(createJob(s, customer, site(s, customer, "Tower B"), ""));
        Instant now = Instant.now();
        String past = id(schedule(s, job, now.minus(1, ChronoUnit.DAYS), now.minus(23, ChronoUnit.HOURS), ""));
        String running = id(schedule(s, job, now.minus(30, ChronoUnit.MINUTES), now.plus(2, ChronoUnit.HOURS), ""));

        mvc.perform(get("/api/v1/visits?overdue=true").with(s.auth()))
                .andExpect(jsonPath("$.content[*].id").value(hasItem(running)));

        int first = TenantContext.runAs(TenantContext.SYSTEM, null, visitService::markMissed);
        assertThat(first).isGreaterThanOrEqualTo(1);
        mvc.perform(get("/api/v1/visits/" + past).with(s.auth())).andExpect(jsonPath("$.status").value("MISSED"));
        mvc.perform(get("/api/v1/visits/" + running).with(s.auth())).andExpect(jsonPath("$.status").value("SCHEDULED"));
        long versionAfterFirst = ((Number) JsonPath.read(mvc.perform(get("/api/v1/visits/" + past).with(s.auth()))
                .andReturn().getResponse().getContentAsString(), "$.version")).longValue();
        TenantContext.runAs(TenantContext.SYSTEM, null, visitService::markMissed);
        mvc.perform(get("/api/v1/visits/" + past).with(s.auth()))
                .andExpect(jsonPath("$.status").value("MISSED"))
                .andExpect(jsonPath("$.version").value(versionAfterFirst)); // untouched by the second run
        // a missed visit can still be rescheduled
        mvc.perform(json(post("/api/v1/visits/" + past + "/reschedule"), """
                {"scheduledStart":"%s","scheduledEnd":"%s"}
                """.formatted(now.plus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)))
                .with(s.auth())).andExpect(status().isCreated());
    }

    @Test
    void standaloneJobRulesAndFinanceRole() throws Exception {
        Session s = Api.register(mvc);
        String customer = customer(s, "Nour Clinic");
        String otherCustomer = customer(s, "Other Co");
        String otherSite = site(s, otherCustomer, "Elsewhere");
        mvc.perform(json(post("/api/v1/jobs"), "{\"title\":\"x\"}").with(s.auth()))
                .andExpect(jsonPath("$.code").value("JOB_CUSTOMER_REQUIRED"));
        mvc.perform(json(post("/api/v1/jobs"), "{\"title\":\"x\",\"contactId\":\"%s\"}".formatted(customer)).with(s.auth()))
                .andExpect(jsonPath("$.code").value("JOB_SITE_REQUIRED"));
        createJob(s, customer, otherSite, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOCATION_NOT_OF_CUSTOMER"));
        createJob(s, customer, site(s, customer, "Main"), ",\"teamManagerUserId\":\"%s\"".formatted(s.userId()))
                .andExpect(jsonPath("$.code").value("TEAM_NEEDS_PROJECT"));
        String job = id(createJob(s, customer, site(s, customer, "Annex"), ",\"type\":\"INSPECTION\""));
        mvc.perform(get("/api/v1/jobs?standalone=true&type=INSPECTION").with(s.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(job)));

        Session finance = Invites.invite(mvc, emails, s, "FINANCE");
        mvc.perform(get("/api/v1/jobs/" + job).with(finance.auth())).andExpect(status().isOk());
        createJob(finance, customer, site(s, customer, "Wing"), "").andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/transactions"), """
                {"jobId":"%s","type":"PAID","amount":"250","title":"قطع غيار","payee":"المورد",
                 "method":"CASH","commitmentDate":"2026-03-01","dueDate":"2026-03-01"}
                """.formatted(job)).with(finance.auth())).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ helpers

    private String customer(Session s, String company) throws Exception {
        return id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","kind":"COMPANY","company":"%s","phone":"+20223456789"}
                """.formatted(company)).with(s.auth())).andExpect(status().isCreated()));
    }

    private String site(Session s, String contact, String name) throws Exception {
        return id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"COMMERCIAL","name":"%s","address":{"line1":"شارع التسعين","city":"القاهرة","country":"EG"}}
                """.formatted(contact, name)).with(s.auth())).andExpect(status().isCreated()));
    }

    private ResultActions createJob(Session s, String contact, String location, String extra) throws Exception {
        return mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"صيانة","description":"زيارة فني"%s}
                """.formatted(contact, location, extra)).with(s.auth()));
    }

    private ResultActions schedule(Session s, String job, Instant start, Instant end, String extra) throws Exception {
        return mvc.perform(json(post("/api/v1/jobs/" + job + "/visits"), """
                {"scheduledStart":"%s","scheduledEnd":"%s"%s}
                """.formatted(start, end, extra)).with(s.auth()));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
