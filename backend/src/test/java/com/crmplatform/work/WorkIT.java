package com.crmplatform.work;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.support.Invites;
import com.crmplatform.support.RecordingEmailSender;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Projects, team, boards, tasks, templates and flowcharts (TS-WRK-01..31). */
@IntegrationTest
class WorkIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    RecordingEmailSender emails;

    // ------------------------------------------------------------------ projects

    @Test
    void createProjectBuildsBoardFromDefaultTemplateAndManager() throws Exception { // TS-WRK-01
        Session s = Api.register(mvc);
        Fixture f = fixture(s);
        String project = id(createProject(s, f, "[]", null)
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.contactId").value(f.contactId))
                .andExpect(jsonPath("$.budget").value("1000.00"))
                .andExpect(jsonPath("$.remainingBudget").value("1000.00"))
                .andExpect(jsonPath("$.boardId").value(notNullValue()))
                .andExpect(jsonPath("$.team", hasSize(1)))
                .andExpect(jsonPath("$.team[0].manager").value(true))
                .andExpect(jsonPath("$.team[0].position").value("Project Manager"))
                .andExpect(jsonPath("$.team[0].userId").value(s.userId().toString())));
        mvc.perform(get("/api/v1/projects/" + project + "/board").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[*].title").value(contains("To Do", "In Progress", "Review", "Done")))
                .andExpect(jsonPath("$.columns[*].position").value(contains(1, 2, 3, 4)))
                .andExpect(jsonPath("$.version").value(notNullValue()));
        mvc.perform(get("/api/v1/projects?q=tower").with(s.auth())).andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void managerInTeamIsRejected() throws Exception { // TS-WRK-02
        Session s = Api.register(mvc);
        Fixture f = fixture(s);
        createProject(s, f, "[\"" + s.userId() + "\"]", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MANAGER_IN_TEAM"));
    }

    @Test
    void noTemplatesGivesFallbackColumns() throws Exception { // TS-WRK-03, EC-12
        Session s = Api.register(mvc);
        Fixture f = fixture(s);
        String template = JsonPath.read(body(mvc.perform(get("/api/v1/board-templates").with(s.auth()))),
                "$.content[0].id");
        mvc.perform(delete("/api/v1/board-templates/" + template).with(s.auth())).andExpect(status().isNoContent());
        String project = id(createProject(s, f, "[]", null));
        mvc.perform(get("/api/v1/projects/" + project + "/board").with(s.auth()))
                .andExpect(jsonPath("$.columns[*].title").value(contains("To Do", "In Progress", "Done")));
    }

    @Test
    void failedCreationPersistsNothing() throws Exception { // TS-WRK-04, TS-WRK-28
        Session a = Api.register(mvc);
        Session b = Api.register(mvc);
        Fixture f = fixture(a);
        String foreignTemplate = JsonPath.read(body(mvc.perform(get("/api/v1/board-templates").with(b.auth()))),
                "$.content[0].id");
        createProject(a, f, "[]", foreignTemplate)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFERENCE_NOT_FOUND"))
                .andExpect(jsonPath("$.field").value("boardTemplateId"));
        mvc.perform(get("/api/v1/projects").with(a.auth())).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void budgetMayBeZeroAndRemainingIsComputed() throws Exception { // TS-WRK-06, TS-WRK-08
        Session s = Api.register(mvc);
        Fixture f = fixture(s);
        String project = id(createProject(s, f, "[]", null));
        mvc.perform(json(patch("/api/v1/projects/" + project), "{\"budget\":0}").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget").value("0.00"))
                .andExpect(jsonPath("$.remainingBudget").value("0.00"))
                .andExpect(jsonPath("$.name").value("Tower A"));
        mvc.perform(json(patch("/api/v1/projects/" + project), "{\"budget\":-1}").with(s.auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects?overBudget=false").with(s.auth()))
                .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/v1/projects?overBudget=true").with(s.auth()))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void teamReplacementAndManagerPromotion() throws Exception { // TS-WRK-07, EC-14
        Session s = Api.register(mvc);
        Session member = invite(s, "MEMBER");
        Fixture f = fixture(s);
        String project = id(createProject(s, f, "[]", null));
        mvc.perform(json(patch("/api/v1/projects/" + project), "{\"teamMemberUserIds\":[\"%s\"]}"
                .formatted(member.userId())).with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team", hasSize(2)));
        mvc.perform(json(patch("/api/v1/projects/" + project), "{\"projectManagerUserId\":\"%s\",\"teamMemberUserIds\":[\"%s\"]}"
                .formatted(member.userId(), member.userId())).with(s.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MANAGER_IN_TEAM"));
        mvc.perform(json(patch("/api/v1/projects/" + project), "{\"projectManagerUserId\":\"%s\"}"
                .formatted(member.userId())).with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team", hasSize(1)))
                .andExpect(jsonPath("$.team[0].userId").value(member.userId().toString()))
                .andExpect(jsonPath("$.team[0].manager").value(true))
                .andExpect(jsonPath("$.team[0].position").value("Project Manager"));
    }

    @Test
    void deleteProjectRemovesBoardAndTasks() throws Exception { // TS-WRK-09
        Session s = Api.register(mvc);
        Fixture f = fixture(s);
        String project = id(createProject(s, f, "[]", null));
        String board = JsonPath.read(body(mvc.perform(get("/api/v1/projects/" + project).with(s.auth()))), "$.boardId");
        String task = id(createTask(s, project, null, "T1"));
        mvc.perform(delete("/api/v1/projects/" + project).with(s.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/projects/" + project).with(s.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/boards/" + board).with(s.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tasks/" + task).with(s.auth())).andExpect(status().isNotFound());
    }

    @Test
    void projectsAreIsolatedPerWorkspace() throws Exception {
        Session a = Api.register(mvc);
        Session b = Api.register(mvc);
        String project = id(createProject(a, fixture(a), "[]", null));
        mvc.perform(get("/api/v1/projects/" + project).with(b.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + project + "/board").with(b.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects").with(b.auth())).andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------------ team members

    @Test
    void teamMemberRules() throws Exception { // TS-WRK-10, TS-WRK-11
        Session s = Api.register(mvc);
        Session member = invite(s, "MEMBER");
        String project = id(createProject(s, fixture(s), "[]", null));
        String tm = id(mvc.perform(json(post("/api/v1/projects/" + project + "/team-members"),
                "{\"userId\":\"%s\",\"position\":\"Site lead\",\"responsibilities\":[\"REPORTING\"]}"
                        .formatted(member.userId())).with(s.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value("Site lead"))
                .andExpect(jsonPath("$.responsibilities[0]").value("REPORTING")));
        mvc.perform(json(post("/api/v1/projects/" + project + "/team-members"),
                "{\"userId\":\"%s\"}".formatted(member.userId())).with(s.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_PROJECT"));
        mvc.perform(json(patch("/api/v1/team-members/" + tm), "{\"responsibilities\":[]}").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibilities", hasSize(0)))
                .andExpect(jsonPath("$.position").value("Site lead"));
        mvc.perform(get("/api/v1/team-members/responsibilities").with(s.auth()))
                .andExpect(jsonPath("$", hasSize(4)));

        String managerTm = JsonPath.read(body(mvc.perform(get("/api/v1/projects/" + project + "/team-members")
                .with(s.auth()))), "$[0].id");
        mvc.perform(delete("/api/v1/team-members/" + managerTm).with(s.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IS_MANAGER"));
        mvc.perform(delete("/api/v1/team-members/" + tm).with(s.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/projects/" + project + "/team-members").with(s.auth()))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void teamNoteDeletionNeedsAuthorOrAdmin() throws Exception { // TS-WRK-12, AZ-01
        Session owner = Api.register(mvc);
        Session member = invite(owner, "MEMBER");
        String project = id(createProject(owner, fixture(owner), "[]", null));
        String tm = JsonPath.read(body(mvc.perform(get("/api/v1/projects/" + project + "/team-members")
                .with(owner.auth()))), "$[0].id");
        String note = id(mvc.perform(json(post("/api/v1/team-members/" + tm + "/notes"), "{\"text\":\"Great work\"}")
                .with(owner.auth())).andExpect(status().isCreated()));
        mvc.perform(delete("/api/v1/team-members/" + tm + "/notes/" + note).with(member.auth()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/team-members/" + tm + "/notes/" + note).with(owner.auth()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/team-members/" + tm + "/notes").with(owner.auth()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ------------------------------------------------------------------ boards & columns

    @Test
    void columnsCanBeAddedRenamedAndReordered() throws Exception { // TS-WRK-13, TS-WRK-14
        Session s = Api.register(mvc);
        String project = id(createProject(s, fixture(s), "[]", null));
        String board = boardId(s, project);
        String review = JsonPath.read(body(mvc.perform(get("/api/v1/boards/" + board).with(s.auth()))),
                "$.columns[2].id");
        mvc.perform(json(post("/api/v1/boards/" + board + "/columns"), "{\"title\":\"Blocked\",\"isDefault\":true}")
                .with(s.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.columns", hasSize(5)))
                .andExpect(jsonPath("$.columns[4].isDefault").value(true))
                .andExpect(jsonPath("$.columns[0].isDefault").value(false));
        mvc.perform(json(post("/api/v1/boards/" + board + "/columns/" + review + "/move"), "{\"position\":1}")
                .with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[*].title").value(contains("Review", "To Do", "In Progress", "Done",
                        "Blocked")))
                .andExpect(jsonPath("$.columns[*].position").value(contains(1, 2, 3, 4, 5)));
        mvc.perform(json(patch("/api/v1/boards/" + board + "/columns/" + review), "{\"title\":\"QA\"}")
                .with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].title").value("QA"));
        mvc.perform(json(post("/api/v1/boards/" + board + "/columns"), "{\"title\":\"%s\"}".formatted("x".repeat(31)))
                .with(s.auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingNonEmptyColumnNeedsTarget() throws Exception { // TS-WRK-15
        Session s = Api.register(mvc);
        String project = id(createProject(s, fixture(s), "[]", null));
        String board = boardId(s, project);
        String boardJson = body(mvc.perform(get("/api/v1/boards/" + board).with(s.auth())));
        String todo = JsonPath.read(boardJson, "$.columns[0].id");
        String done = JsonPath.read(boardJson, "$.columns[3].id");
        createTask(s, project, todo, "A");
        createTask(s, project, todo, "B");
        createTask(s, project, done, "C");
        mvc.perform(delete("/api/v1/boards/" + board + "/columns/" + todo).with(s.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLUMN_NOT_EMPTY"));
        mvc.perform(delete("/api/v1/boards/" + board + "/columns/" + todo).param("moveTasksTo", todo).with(s.auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/boards/" + board + "/columns/" + todo).param("moveTasksTo", done).with(s.auth()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/boards/" + board).with(s.auth()))
                .andExpect(jsonPath("$.columns", hasSize(3)))
                .andExpect(jsonPath("$.columns[2].tasks[*].title").value(contains("C", "A", "B")))
                .andExpect(jsonPath("$.columns[2].tasks[*].position").value(contains(1, 2, 3)))
                .andExpect(jsonPath("$.columns[*].position").value(contains(1, 2, 3)));
    }

    // ------------------------------------------------------------------ tasks

    @Test
    void createTaskPlacementAndParentRules() throws Exception { // TS-WRK-16
        Session s = Api.register(mvc);
        Session other = Api.register(mvc);
        String project = id(createProject(s, fixture(s), "[]", null));
        String otherProject = id(createProject(other, fixture(other), "[]", null));
        String otherColumn = JsonPath.read(body(mvc.perform(get("/api/v1/projects/" + otherProject + "/board")
                .with(other.auth()))), "$.columns[0].id");
        createTask(s, project, null, "First").andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        createTask(s, project, null, "Second").andExpect(jsonPath("$.position").value(2));
        mvc.perform(json(post("/api/v1/tasks"), taskBody("\"projectId\":\"%s\",\"jobId\":\"%s\"".formatted(project,
                UUID.randomUUID()), "Both")).with(s.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TASK_PARENT_AMBIGUOUS"));
        mvc.perform(json(post("/api/v1/tasks"), taskBody("\"x\":1", "None")).with(s.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TASK_PARENT_REQUIRED"));
        createTask(s, project, otherColumn, "Wrong column")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COLUMN_NOT_ON_BOARD"));
        mvc.perform(json(post("/api/v1/tasks"), taskBody("\"projectId\":\"%s\",\"priority\":\"URGENT\""
                .formatted(project), "Bad priority")).with(s.auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completingATaskSetsCompletedAt() throws Exception { // TS-WRK-17
        Session s = Api.register(mvc);
        String project = id(createProject(s, fixture(s), "[]", null));
        String task = id(createTask(s, project, null, "Pour concrete"));
        mvc.perform(json(patch("/api/v1/tasks/" + task), "{\"status\":\"COMPLETED\"}").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").value(notNullValue()));
        mvc.perform(json(patch("/api/v1/tasks/" + task), "{\"status\":\"IN_PROGRESS\"}").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.title").value("Pour concrete"));
    }

    @Test
    void assigneesMustBeOnTheTeam() throws Exception { // EC-22
        Session s = Api.register(mvc);
        String project = id(createProject(s, fixture(s), "[]", null));
        String manager = JsonPath.read(body(mvc.perform(get("/api/v1/projects/" + project + "/team-members")
                .with(s.auth()))), "$[0].id");
        String task = id(createTask(s, project, null, "Assign me"));
        mvc.perform(json(patch("/api/v1/tasks/" + task), "{\"assigneeTeamMemberIds\":[\"%s\"]}".formatted(manager))
                .with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignees[0].teamMemberId").value(manager));
        mvc.perform(json(patch("/api/v1/tasks/" + task), "{\"assigneeTeamMemberIds\":[\"%s\"]}"
                .formatted(UUID.randomUUID())).with(s.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_IN_TEAM"));
    }

    @Test
    void randomMovesKeepPositionsContiguous() throws Exception { // TS-WRK-18, BR-076
        Session s = Api.register(mvc);
        String project = id(createProject(s, fixture(s), "[]", null));
        String board = boardId(s, project);
        String boardJson = body(mvc.perform(get("/api/v1/boards/" + board).with(s.auth())));
        List<String> columns = JsonPath.read(boardJson, "$.columns[*].id");
        List<String> taskIds = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            taskIds.add(id(createTask(s, project, columns.get(i % 3), "T" + i)));
        }
        boardJson = body(mvc.perform(get("/api/v1/boards/" + board).with(s.auth())));
        assertContiguous(boardJson, taskIds.size());
        Random random = new Random(42);
        for (int i = 0; i < 30; i++) {
            long version = ((Number) JsonPath.read(boardJson, "$.version")).longValue();
            String task = taskIds.get(random.nextInt(taskIds.size()));
            String column = columns.get(random.nextInt(columns.size()));
            int position = 1 + random.nextInt(6);
            boardJson = body(mvc.perform(json(post("/api/v1/tasks/" + task + "/move"),
                    "{\"columnId\":\"%s\",\"position\":%d,\"boardVersion\":%d}".formatted(column, position, version))
                    .with(s.auth())).andExpect(status().isOk()));
            assertContiguous(boardJson, taskIds.size());
            List<String> inColumn = JsonPath.read(boardJson, "$.columns[?(@.id=='" + column + "')].tasks[*].id");
            assertThat(inColumn).contains(task);
        }
    }

    @Test
    void staleBoardVersionConflicts() throws Exception { // TS-WRK-19, BR-077, EC-16
        Session s = Api.register(mvc);
        String project = id(createProject(s, fixture(s), "[]", null));
        String board = boardId(s, project);
        String t1 = id(createTask(s, project, null, "One"));
        String t2 = id(createTask(s, project, null, "Two"));
        String boardJson = body(mvc.perform(get("/api/v1/boards/" + board).with(s.auth())));
        long version = ((Number) JsonPath.read(boardJson, "$.version")).longValue();
        String done = JsonPath.read(boardJson, "$.columns[3].id");
        mvc.perform(json(post("/api/v1/tasks/" + t1 + "/move"),
                "{\"columnId\":\"%s\",\"position\":1,\"boardVersion\":%d}".formatted(done, version)).with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[3].tasks[0].id").value(t1))
                .andExpect(jsonPath("$.columns[0].tasks[0].position").value(1));
        mvc.perform(json(post("/api/v1/tasks/" + t2 + "/move"),
                "{\"columnId\":\"%s\",\"position\":1,\"boardVersion\":%d}".formatted(done, version)).with(s.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOARD_CONFLICT"))
                .andExpect(jsonPath("$.board.columns[3].tasks[0].id").value(t1));
        mvc.perform(json(post("/api/v1/tasks/" + t2 + "/move"), "{\"columnId\":\"%s\",\"position\":1}".formatted(done))
                .with(s.auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingATaskCompactsPositions() throws Exception { // TS-WRK-24
        Session s = Api.register(mvc);
        String project = id(createProject(s, fixture(s), "[]", null));
        createTask(s, project, null, "A");
        String b = id(createTask(s, project, null, "B"));
        createTask(s, project, null, "C");
        mvc.perform(delete("/api/v1/tasks/" + b).with(s.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/projects/" + project + "/board").with(s.auth()))
                .andExpect(jsonPath("$.columns[0].tasks[*].title").value(contains("A", "C")))
                .andExpect(jsonPath("$.columns[0].tasks[*].position").value(contains(1, 2)));
    }

    @Test
    void taskComments() throws Exception { // TS-WRK-25
        Session owner = Api.register(mvc);
        Session member = invite(owner, "MEMBER");
        String project = id(createProject(owner, fixture(owner), "[]", null));
        String task = id(createTask(owner, project, null, "Discuss"));
        String comment = id(mvc.perform(json(post("/api/v1/tasks/" + task + "/comments"), "{\"text\":\"Looks good\"}")
                .with(owner.auth())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorUserId").value(owner.userId().toString())));
        mvc.perform(get("/api/v1/projects/" + project + "/board").with(owner.auth()))
                .andExpect(jsonPath("$.columns[0].tasks[0].commentCount").value(1));
        mvc.perform(json(patch("/api/v1/tasks/" + task + "/comments/" + comment), "{\"text\":\"Hijack\"}")
                .with(member.auth()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/tasks/" + task + "/comments/" + comment).with(member.auth()))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/v1/tasks/" + task + "/comments/" + comment), "{\"text\":\"Edited\"}")
                .with(owner.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Edited"));
        mvc.perform(delete("/api/v1/tasks/" + task + "/comments/" + comment).with(owner.auth()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/tasks/" + task + "/comments").with(owner.auth()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void overdueFilter() throws Exception { // TS-WRK-26
        Session s = Api.register(mvc);
        String project = id(createProject(s, fixture(s), "[]", null));
        LocalDate past = LocalDate.now().minusDays(5);
        String late = """
                {"projectId":"%s","title":"Late","description":"","startDate":"%s","endDate":"%s"}
                """.formatted(project, past.minusDays(1), past);
        mvc.perform(json(post("/api/v1/tasks"), late).with(s.auth())).andExpect(status().isCreated());
        String finished = id(mvc.perform(json(post("/api/v1/tasks"), late.replace("Late", "Finished")).with(s.auth())));
        mvc.perform(json(patch("/api/v1/tasks/" + finished), "{\"status\":\"COMPLETED\"}").with(s.auth()))
                .andExpect(status().isOk());
        createTask(s, project, null, "Future");
        mvc.perform(get("/api/v1/tasks").param("projectId", project).param("overdue", "true").with(s.auth()))
                .andExpect(jsonPath("$.content[*].title").value(contains("Late")));
        mvc.perform(get("/api/v1/tasks").param("projectId", project).with(s.auth()))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/tasks").param("status", "NOPE").with(s.auth()))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ templates

    @Test
    void onlyOneDefaultTemplate() throws Exception { // TS-WRK-27
        Session s = Api.register(mvc);
        String created = id(mvc.perform(json(post("/api/v1/board-templates"),
                "{\"title\":\"Lean\",\"columns\":[\"Backlog\",\"Doing\",\"Shipped\"],\"defaultColumnIndex\":1,\"isDefault\":true}")
                .with(s.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.columns[1].isDefault").value(true)));
        mvc.perform(get("/api/v1/board-templates").with(s.auth()))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[?(@.isDefault==true)].id").value(contains(created)));
        mvc.perform(json(patch("/api/v1/board-templates/" + created), "{\"title\":\"Lean 2\"}").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns", hasSize(3)))
                .andExpect(jsonPath("$.columns[1].isDefault").value(true))
                .andExpect(jsonPath("$.isDefault").value(true));
        String project = id(createProject(s, fixture(s), "[]", null));
        mvc.perform(get("/api/v1/projects/" + project + "/board").with(s.auth()))
                .andExpect(jsonPath("$.columns[*].title").value(contains("Backlog", "Doing", "Shipped")))
                .andExpect(jsonPath("$.columns[1].isDefault").value(true));
        mvc.perform(json(post("/api/v1/board-templates"), "{\"title\":\"Empty\",\"columns\":[]}").with(s.auth()))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ flowcharts

    @Test
    void flowchartOwnerRules() throws Exception { // TS-WRK-29, TS-WRK-30, AZ-02
        Session owner = Api.register(mvc);
        Session member = invite(owner, "MEMBER");
        String chart = id(mvc.perform(json(post("/api/v1/flowcharts"), "{\"title\":\"Permit flow\"}")
                .with(owner.auth())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").value(owner.userId().toString())));
        mvc.perform(json(patch("/api/v1/flowcharts/" + chart), "{\"favorite\":true}").with(member.auth()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/flowcharts/" + chart).with(member.auth())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/flowcharts/" + chart).with(member.auth())).andExpect(status().isOk());
        mvc.perform(json(patch("/api/v1/flowcharts/" + chart), "{\"favorite\":true}").with(owner.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true))
                .andExpect(jsonPath("$.title").value("Permit flow"));
        mvc.perform(get("/api/v1/flowcharts?favorite=true&mine=true").with(owner.auth()))
                .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/v1/flowcharts?mine=true").with(member.auth()))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(json(post("/api/v1/flowcharts"), "{\"title\":\"ab\"}").with(owner.auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void diagramSaveUsesIfMatch() throws Exception { // TS-WRK-31, BR-086
        Session s = Api.register(mvc);
        String chart = id(mvc.perform(json(post("/api/v1/flowcharts"), "{\"title\":\"Approval\"}").with(s.auth())));
        String etag = mvc.perform(get("/api/v1/flowcharts/" + chart + "/diagram").with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagram.nodes", hasSize(0)))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();
        String diagram = "{\"nodes\":[{\"id\":\"n1\",\"label\":\"Start\"}],\"edges\":[]}";
        mvc.perform(json(put("/api/v1/flowcharts/" + chart + "/diagram"), diagram).with(s.auth()))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(json(put("/api/v1/flowcharts/" + chart + "/diagram"), diagram)
                .header(HttpHeaders.IF_MATCH, "\"999\"").with(s.auth()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        String next = mvc.perform(json(put("/api/v1/flowcharts/" + chart + "/diagram"), diagram)
                .header(HttpHeaders.IF_MATCH, etag).with(s.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagram.nodes[0].label").value("Start"))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(next).isNotEqualTo(etag);
        mvc.perform(json(put("/api/v1/flowcharts/" + chart + "/diagram"), diagram)
                .header(HttpHeaders.IF_MATCH, etag).with(s.auth()))
                .andExpect(status().isPreconditionFailed());
        String huge = "{\"nodes\":[{\"id\":\"n1\",\"label\":\"%s\"}],\"edges\":[]}".formatted("x".repeat(1_100_000));
        mvc.perform(json(put("/api/v1/flowcharts/" + chart + "/diagram"), huge)
                .header(HttpHeaders.IF_MATCH, next).with(s.auth()))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(json(put("/api/v1/flowcharts/" + chart + "/diagram"), "{\"nodes\":[]}")
                .header(HttpHeaders.IF_MATCH, next).with(s.auth()))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ helpers

    private record Fixture(String contactId, String locationId) {
    }

    private Fixture fixture(Session s) throws Exception {
        String contact = id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","name":{"first":"Sam","last":"Rivera"},"email":"%s"}
                """.formatted(Api.uniqueEmail())).with(s.auth())).andExpect(status().isCreated()));
        String location = id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"OFFICE","name":"HQ","address":{"city":"Austin","country":"US"}}
                """.formatted(contact)).with(s.auth())).andExpect(status().isCreated()));
        return new Fixture(contact, location);
    }

    private ResultActions createProject(Session s, Fixture f, String teamJson, String templateId) throws Exception {
        String template = templateId == null ? "" : ",\"boardTemplateId\":\"" + templateId + "\"";
        return mvc.perform(json(post("/api/v1/projects"), """
                {"name":"Tower A","description":"Office tower","type":"COMMERCIAL",
                 "address":{"line1":"1 Main St","city":"Austin","region":"TX","postalCode":"78701","country":"US"},
                 "totalArea":"1200.50","budget":"1000","startDate":"2026-01-01","estimatedEndDate":"2026-12-31",
                 "locationId":"%s","projectManagerUserId":"%s","teamMemberUserIds":%s%s}
                """.formatted(f.locationId(), s.userId(), teamJson, template)).with(s.auth()));
    }

    private ResultActions createTask(Session s, String projectId, String columnId, String title) throws Exception {
        String parent = "\"projectId\":\"%s\"".formatted(projectId)
                + (columnId == null ? "" : ",\"columnId\":\"%s\"".formatted(columnId));
        return mvc.perform(json(post("/api/v1/tasks"), taskBody(parent, title)).with(s.auth()));
    }

    private static String taskBody(String extra, String title) {
        LocalDate today = LocalDate.now();
        return "{%s,\"title\":\"%s\",\"description\":\"\",\"startDate\":\"%s\",\"endDate\":\"%s\"}"
                .formatted(extra, title, today, today.plusDays(7));
    }

    private String boardId(Session s, String project) throws Exception {
        return JsonPath.read(body(mvc.perform(get("/api/v1/projects/" + project).with(s.auth()))), "$.boardId");
    }

    private Session invite(Session owner, String role) throws Exception {
        return Invites.invite(mvc, emails, owner, role);
    }

    private static void assertContiguous(String boardJson, int expectedTotal) {
        List<Map<String, Object>> columns = JsonPath.read(boardJson, "$.columns");
        int total = 0;
        Map<String, Integer> seen = new HashMap<>();
        for (Map<String, Object> column : columns) {
            List<Integer> positions = JsonPath.read(boardJson,
                    "$.columns[?(@.id=='" + column.get("id") + "')].tasks[*].position");
            for (int i = 0; i < positions.size(); i++) {
                assertThat(positions.get(i)).as("column %s", column.get("title")).isEqualTo(i + 1);
            }
            total += positions.size();
            seen.put((String) column.get("id"), positions.size());
        }
        assertThat(total).as("tasks per column %s", seen).isEqualTo(expectedTotal);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.id");
    }
}
