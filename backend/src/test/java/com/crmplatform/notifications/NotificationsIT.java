package com.crmplatform.notifications;

import static com.crmplatform.support.Api.json;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** In-app notifications (R5): produced from committed events, strictly per recipient. */
@IntegrationTest
class NotificationsIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingEmailSender emails;

    @Test
    void assignmentAndVisitsNotifyTheTechnicianButNotTheDispatcher() throws Exception {
        Session owner = Api.register(mvc);
        Session tech = Invites.invite(mvc, emails, owner, "TECHNICIAN");
        String customer = customer(owner, "شركة الأمل");
        String site = site(owner, customer);
        String job = id(mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"صيانة تكييف","assigneeUserIds":["%s"]}
                """.formatted(customer, site, tech.userId())).with(owner.auth())).andExpect(status().isCreated()));

        mvc.perform(get("/api/v1/notifications").with(tech.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("JOB_ASSIGNED"))
                .andExpect(jsonPath("$.content[0].entityType").value("JOB"))
                .andExpect(jsonPath("$.content[0].entityId").value(job))
                .andExpect(jsonPath("$.content[0].actorUserId").value(owner.userId().toString()))
                .andExpect(jsonPath("$.content[0].payload.jobTitle").value("صيانة تكييف"))
                .andExpect(jsonPath("$.content[0].read").value(false));
        // the person who assigned is not notified about their own action
        mvc.perform(get("/api/v1/notifications").with(owner.auth())).andExpect(jsonPath("$.totalElements").value(0));
        // re-saving the same assignees does not notify again
        mvc.perform(json(patch("/api/v1/jobs/" + job), "{\"assigneeUserIds\":[\"%s\"]}".formatted(tech.userId()))
                .with(owner.auth())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications/unread-count").with(tech.auth())).andExpect(jsonPath("$.unread").value(1));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        String visit = id(mvc.perform(json(post("/api/v1/jobs/" + job + "/visits"), """
                {"scheduledStart":"%s","scheduledEnd":"%s","assigneeUserIds":["%s"]}
                """.formatted(start, start.plus(2, ChronoUnit.HOURS), tech.userId())).with(owner.auth()))
                .andExpect(status().isCreated()));
        String next = id(mvc.perform(json(post("/api/v1/visits/" + visit + "/reschedule"), """
                {"scheduledStart":"%s","scheduledEnd":"%s"}
                """.formatted(start.plus(1, ChronoUnit.DAYS), start.plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS)))
                .with(owner.auth())).andExpect(status().isCreated()));

        mvc.perform(get("/api/v1/notifications").with(tech.auth()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].type").value("VISIT_RESCHEDULED")) // newest first
                .andExpect(jsonPath("$.content[0].entityId").value(next))
                .andExpect(jsonPath("$.content[0].payload.rescheduledFromId").value(visit))
                .andExpect(jsonPath("$.content[0].payload.jobId").value(job))
                .andExpect(jsonPath("$.content[1].type").value("VISIT_SCHEDULED"))
                .andExpect(jsonPath("$.content[1].payload.scheduledStart").value(start.toString()));
        mvc.perform(get("/api/v1/notifications").with(owner.auth())).andExpect(jsonPath("$.totalElements").value(0));

        // a technician scheduling their own follow-up is not notified about it
        mvc.perform(json(post("/api/v1/jobs/" + job + "/visits"), """
                {"scheduledStart":"%s","scheduledEnd":"%s"}
                """.formatted(start.plus(3, ChronoUnit.DAYS), start.plus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)))
                .with(tech.auth())).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/notifications/unread-count").with(tech.auth())).andExpect(jsonPath("$.unread").value(3));
    }

    @Test
    void usersOnlyEverSeeAndChangeTheirOwnNotifications() throws Exception {
        Session owner = Api.register(mvc);
        Session ahmed = Invites.invite(mvc, emails, owner, "TECHNICIAN");
        Session sara = Invites.invite(mvc, emails, owner, "TECHNICIAN");
        Session outsider = Api.register(mvc);
        String customer = customer(owner, "Cairo Mall");
        String site = site(owner, customer);
        for (Session t : new Session[] { ahmed, ahmed, sara }) {
            mvc.perform(json(post("/api/v1/jobs"), """
                    {"contactId":"%s","locationId":"%s","title":"CCTV","assigneeUserIds":["%s"]}
                    """.formatted(customer, site, t.userId())).with(owner.auth())).andExpect(status().isCreated());
        }
        String ahmedFirst = JsonPath.read(body(mvc.perform(get("/api/v1/notifications").with(ahmed.auth()))),
                "$.content[0].id");
        String saraOnly = JsonPath.read(body(mvc.perform(get("/api/v1/notifications").with(sara.auth()))),
                "$.content[0].id");

        // nobody else — same workspace (owner, sara) or another workspace — can read or mark it
        for (Session other : new Session[] { owner, sara, outsider }) {
            mvc.perform(post("/api/v1/notifications/" + ahmedFirst + "/read").with(other.auth()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/notifications").with(outsider.auth())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/v1/notifications/read-all").with(sara.auth())).andExpect(jsonPath("$.updated").value(1));
        mvc.perform(get("/api/v1/notifications/unread-count").with(ahmed.auth())).andExpect(jsonPath("$.unread").value(2));

        mvc.perform(post("/api/v1/notifications/" + ahmedFirst + "/read").with(ahmed.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/api/v1/notifications?unread=true").with(ahmed.auth()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(post("/api/v1/notifications/read-all").with(ahmed.auth())).andExpect(jsonPath("$.updated").value(1));
        mvc.perform(post("/api/v1/notifications/read-all").with(ahmed.auth())).andExpect(jsonPath("$.updated").value(0));
        mvc.perform(get("/api/v1/notifications/unread-count").with(ahmed.auth())).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/v1/notifications").with(ahmed.auth())).andExpect(jsonPath("$.totalElements").value(2));
        // sara's stays hers
        mvc.perform(get("/api/v1/notifications").with(sara.auth()))
                .andExpect(jsonPath("$.content[*].id").value(contains(saraOnly)));

        // localized 404 for a foreign id
        mvc.perform(post("/api/v1/notifications/" + saraOnly + "/read").with(ahmed.auth())
                .header("Accept-Language", "ar")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void projectTeamChangesAndDeletionNotifyMembers() throws Exception {
        Session owner = Api.register(mvc);
        Session member = Invites.invite(mvc, emails, owner, "MEMBER");
        String customer = customer(owner, "Delta Towers");
        String site = site(owner, customer);
        String project = id(mvc.perform(json(post("/api/v1/projects"), """
                {"name":"برج دلتا","description":"تشطيب","type":"COMMERCIAL",
                 "address":{"line1":"1 Main St","city":"Cairo","region":"Cairo","postalCode":"11511","country":"EG"},
                 "totalArea":"500","budget":"1000","startDate":"2026-01-01","estimatedEndDate":"2026-12-31",
                 "locationId":"%s","projectManagerUserId":"%s","teamMemberUserIds":["%s"]}
                """.formatted(site, owner.userId(), member.userId())).with(owner.auth()))
                .andExpect(status().isCreated()));

        mvc.perform(get("/api/v1/notifications").with(member.auth()))
                .andExpect(jsonPath("$.content[*].type").value(hasItem("PROJECT_MEMBER_ADDED")))
                .andExpect(jsonPath("$.content[0].payload.projectName").value("برج دلتا"))
                .andExpect(jsonPath("$.content[0].entityId").value(project));
        mvc.perform(get("/api/v1/notifications").with(owner.auth())).andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(delete("/api/v1/projects/" + project).with(owner.auth())).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/v1/notifications").with(member.auth()))
                .andExpect(jsonPath("$.content[0].type").value("PROJECT_DELETED"))
                .andExpect(jsonPath("$.content[0].payload.projectName").value("برج دلتا"));
    }

    // ------------------------------------------------------------------ helpers

    private String customer(Session s, String company) throws Exception {
        return id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","kind":"COMPANY","company":"%s","phone":"+20223456789"}
                """.formatted(company)).with(s.auth())).andExpect(status().isCreated()));
    }

    private String site(Session s, String contact) throws Exception {
        return id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"COMMERCIAL","name":"الفرع","address":{"line1":"شارع التسعين","city":"القاهرة","country":"EG"}}
                """.formatted(contact)).with(s.auth())).andExpect(status().isCreated()));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.id");
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
