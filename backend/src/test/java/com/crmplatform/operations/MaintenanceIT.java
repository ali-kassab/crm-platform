package com.crmplatform.operations;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.operations.maintenance.application.MaintenanceIntegration.MaintenanceDaily;
import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.support.Invites;
import com.crmplatform.support.RecordingEmailSender;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Equipment, warranty links/reminders, preventive maintenance and SLA (R6). */
@IntegrationTest
class MaintenanceIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingEmailSender emails;
    @Autowired
    MaintenanceDaily daily;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void equipmentRegisterServiceHistoryAndWarranties() throws Exception {
        Session s = Api.register(mvc);
        String customer = customer(s, "بنك القاهرة");
        String branch = site(s, customer, "فرع المعادي");
        String other = site(s, customer, "فرع الزمالك");
        String camera = id(mvc.perform(json(post("/api/v1/equipment"), """
                {"contactId":"%s","locationId":"%s","category":"CCTV_CAMERA","name":"كاميرا المدخل",
                 "manufacturer":"Hikvision","model":"DS-2CD2143","serialNumber":"HK-001","installedOn":"2026-01-10"}
                """.formatted(customer, branch)).with(s.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.serviceHistory", hasSize(0))));
        // serial numbers are unique per workspace (case-insensitive)
        mvc.perform(json(post("/api/v1/equipment"), """
                {"contactId":"%s","locationId":"%s","category":"RECORDER","name":"NVR","serialNumber":"hk-001"}
                """.formatted(customer, other)).with(s.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SERIAL"));
        String recorder = id(mvc.perform(json(post("/api/v1/equipment"), """
                {"contactId":"%s","locationId":"%s","category":"RECORDER","name":"NVR 16ch"}
                """.formatted(customer, other)).with(s.auth())).andExpect(status().isCreated()));
        mvc.perform(get("/api/v1/equipment?locationId=" + branch).with(s.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(camera)));
        mvc.perform(get("/api/v1/equipment?q=ds-2cd").with(s.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(camera)));

        String job = id(job(s, customer, branch, ",\"type\":\"CORRECTIVE_REPAIR\""));
        mvc.perform(json(put("/api/v1/jobs/" + job + "/equipment"), "{\"equipmentIds\":[\"%s\"]}".formatted(recorder))
                .with(s.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_NOT_AT_SITE"));
        mvc.perform(json(put("/api/v1/jobs/" + job + "/equipment"), "{\"equipmentIds\":[\"%s\"]}".formatted(camera))
                .with(s.auth())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/jobs/" + job + "/equipment").with(s.auth()))
                .andExpect(jsonPath("$[*].id").value(contains(camera)));

        // a device warranty inherits the device's customer and site
        String warranty = id(mvc.perform(json(post("/api/v1/warranties"), """
                {"title":"ضمان الكاميرا","provider":"Hikvision Egypt","startDate":"2026-01-10","endDate":"2028-01-10",
                 "equipmentId":"%s"}
                """.formatted(camera)).with(s.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactId").value(customer))
                .andExpect(jsonPath("$.locationId").value(branch))
                .andExpect(jsonPath("$.reminderDays").value(30)));
        mvc.perform(json(post("/api/v1/warranties"), """
                {"title":"x warranty","provider":"p","startDate":"2026-01-10","endDate":"2028-01-10",
                 "equipmentId":"%s","locationId":"%s"}
                """.formatted(camera, other)).with(s.auth()))
                .andExpect(jsonPath("$.code").value("EQUIPMENT_NOT_AT_SITE"));
        mvc.perform(get("/api/v1/equipment/" + camera).with(s.auth()))
                .andExpect(jsonPath("$.serviceHistory[0].jobId").value(job))
                .andExpect(jsonPath("$.warranties[0].id").value(warranty));
        mvc.perform(get("/api/v1/warranties?equipmentId=" + camera).with(s.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(warranty)));

        // isolation: another workspace sees nothing
        Session outsider = Api.register(mvc);
        mvc.perform(get("/api/v1/equipment/" + camera).with(outsider.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/equipment").with(outsider.auth())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(delete("/api/v1/locations/" + other).with(s.auth())).andExpect(status().isConflict());
    }

    @Test
    void planGenerationIsIdempotentAndRemindersAreSentOnce() throws Exception {
        Session owner = Api.register(mvc);
        Session tech = Invites.invite(mvc, emails, owner, "TECHNICIAN");
        String customer = customer(owner, "مستشفى النيل");
        String site = site(owner, customer, "المبنى الرئيسي");
        String unit = id(mvc.perform(json(post("/api/v1/equipment"), """
                {"contactId":"%s","locationId":"%s","category":"HVAC_UNIT","name":"تكييف مركزي 1"}
                """.formatted(customer, site)).with(owner.auth())).andExpect(status().isCreated()));
        LocalDate today = LocalDate.now();
        String plan = id(mvc.perform(json(post("/api/v1/maintenance-plans"), """
                {"contactId":"%s","locationId":"%s","title":"صيانة دورية للتكييف","frequency":"MONTHLY",
                 "startDate":"%s","endDate":"%s","leadDays":3,"responseHours":4,"visitBudget":"750",
                 "assigneeUserIds":["%s"],"equipmentIds":["%s"]}
                """.formatted(customer, site, today, today.plusDays(20), tech.userId(), unit)).with(owner.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nextDueDate").value(today.toString())));

        daily.runOnce();
        daily.runOnce(); // a second run (or a retry) creates nothing new
        mvc.perform(post("/api/v1/maintenance-plans/" + plan + "/generate").with(owner.auth()))
                .andExpect(jsonPath("$.generated").value(0));
        String body = body(mvc.perform(get("/api/v1/maintenance-plans/" + plan).with(owner.auth()))
                .andExpect(jsonPath("$.occurrences", hasSize(1)))
                .andExpect(jsonPath("$.generatedCount").value(1))
                .andExpect(jsonPath("$.nextDueDate").doesNotExist())); // next month is after the agreement ends
        String job = JsonPath.read(body, "$.occurrences[0].jobId");
        mvc.perform(get("/api/v1/jobs?contactId=" + customer).with(owner.auth()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/jobs/" + job).with(owner.auth()))
                .andExpect(jsonPath("$.type").value("PREVENTIVE_MAINTENANCE"))
                .andExpect(jsonPath("$.maintenancePlanId").value(plan))
                .andExpect(jsonPath("$.startDate").value(today.toString()))
                .andExpect(jsonPath("$.budget").value("750.00"))
                .andExpect(jsonPath("$.sla").doesNotExist()) // planned work has no response SLA
                .andExpect(jsonPath("$.assignees[0].userId").value(tech.userId().toString()));
        mvc.perform(get("/api/v1/jobs/" + job + "/equipment").with(tech.auth()))
                .andExpect(jsonPath("$[*].id").value(contains(unit)));
        mvc.perform(get("/api/v1/notifications").with(tech.auth()))
                .andExpect(jsonPath("$.content[*].type").value(hasItem("JOB_ASSIGNED")));
        // the agreement ends within 30 days → one renewal reminder for the owner
        mvc.perform(get("/api/v1/notifications").with(owner.auth()))
                .andExpect(jsonPath("$.content[?(@.type == 'MAINTENANCE_PLAN_ENDING')]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.type == 'MAINTENANCE_PLAN_ENDING')].entityId").value(hasItem(plan)));

        // time passes: the preventive job is now overdue
        jdbc.update("update maintenance_occurrences set due_date = ? where job_id = ?", today.minusDays(3),
                UUID.fromString(job));
        mvc.perform(get("/api/v1/maintenance-plans/overdue").with(owner.auth()))
                .andExpect(jsonPath("$[*].jobId").value(hasItem(job)))
                .andExpect(jsonPath("$[0].overdue").value(true));
        daily.runOnce();
        daily.runOnce();
        for (Session who : List.of(owner, tech)) {
            mvc.perform(get("/api/v1/notifications").with(who.auth()))
                    .andExpect(jsonPath("$.content[?(@.type == 'MAINTENANCE_OVERDUE')]", hasSize(1)));
        }
        // completing it clears the overdue list
        mvc.perform(json(post("/api/v1/jobs/" + job + "/status"), "{\"status\":\"COMPLETED\"}").with(tech.auth()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/maintenance-plans/overdue").with(owner.auth()))
                .andExpect(jsonPath("$[*].jobId").value(not(hasItem(job))));

        // warranty reminder: inside the 30-day window, sent once
        mvc.perform(json(post("/api/v1/warranties"), """
                {"title":"ضمان الكمبروسر","provider":"Carrier","startDate":"%s","endDate":"%s","equipmentId":"%s"}
                """.formatted(today.minusYears(1), today.plusDays(10), unit)).with(owner.auth()))
                .andExpect(status().isCreated());
        daily.runOnce();
        daily.runOnce();
        mvc.perform(get("/api/v1/notifications").with(owner.auth()))
                .andExpect(jsonPath("$.content[?(@.type == 'WARRANTY_EXPIRING')]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.type == 'WARRANTY_EXPIRING')].payload.provider")
                        .value(hasItem("Carrier")));
        mvc.perform(get("/api/v1/notifications").with(tech.auth()))
                .andExpect(jsonPath("$.content[?(@.type == 'WARRANTY_EXPIRING')]", hasSize(0)));
    }

    @Test
    void agreementResponseTimeAppliesToUnplannedJobsAndBreachesAreListed() throws Exception {
        Session s = Api.register(mvc);
        String customer = customer(s, "Cairo Mall");
        String covered = site(s, customer, "Gate 1");
        String uncovered = site(s, customer, "Gate 9");
        mvc.perform(json(post("/api/v1/maintenance-plans"), """
                {"contactId":"%s","locationId":"%s","title":"SLA agreement","frequency":"CUSTOM","intervalDays":90,
                 "startDate":"%s","responseHours":4}
                """.formatted(customer, covered, LocalDate.now().plusDays(60))).with(s.auth()))
                .andExpect(status().isCreated());
        mvc.perform(json(post("/api/v1/maintenance-plans"), """
                {"contactId":"%s","locationId":"%s","title":"bad","frequency":"CUSTOM","startDate":"%s"}
                """.formatted(customer, covered, LocalDate.now())).with(s.auth()))
                .andExpect(status().isBadRequest());

        String urgent = id(job(s, customer, covered, ",\"type\":\"CORRECTIVE_REPAIR\",\"priority\":\"URGENT\"")
                .andExpect(jsonPath("$.sla.responseDueAt").value(notNullValue()))
                .andExpect(jsonPath("$.sla.breached").value(false)));
        job(s, customer, uncovered, "").andExpect(jsonPath("$.sla").doesNotExist());
        String explicit = id(job(s, customer, uncovered, ",\"responseHours\":1")
                .andExpect(jsonPath("$.sla.responseDueAt").value(notNullValue())));

        // the deadline passes without anyone starting the work
        int moved = jdbc.update("update jobs set response_due_at = now() - interval '1 hour' where id = ?",
                UUID.fromString(explicit));
        assertThat(moved).isEqualTo(1);
        mvc.perform(get("/api/v1/jobs?slaBreached=true").with(s.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(explicit)));
        mvc.perform(json(post("/api/v1/jobs/" + explicit + "/status"), "{\"status\":\"IN_PROGRESS\"}").with(s.auth()))
                .andExpect(jsonPath("$.sla.breached").value(true)) // started late is still a breach
                .andExpect(jsonPath("$.sla.respondedAt").value(notNullValue()));
        mvc.perform(json(post("/api/v1/jobs/" + urgent + "/status"), "{\"status\":\"IN_PROGRESS\"}").with(s.auth()))
                .andExpect(jsonPath("$.sla.breached").value(false));
        mvc.perform(get("/api/v1/jobs?slaBreached=true").with(s.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(explicit)));
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

    private ResultActions job(Session s, String contact, String location, String extra) throws Exception {
        return mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"بلاغ عطل"%s}
                """.formatted(contact, location, extra)).with(s.auth())).andExpect(status().isCreated());
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.id");
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
