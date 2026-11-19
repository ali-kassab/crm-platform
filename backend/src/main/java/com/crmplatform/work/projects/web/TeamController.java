package com.crmplatform.work.projects.web;

import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.identity.WorkspaceMembers.MemberSummary;
import com.crmplatform.shared.web.Patches;
import com.crmplatform.work.projects.application.TeamService;
import com.crmplatform.work.projects.domain.TeamMember;
import com.crmplatform.work.projects.domain.TeamMember.Responsibility;
import com.crmplatform.work.projects.domain.TeamMemberNote;
import com.crmplatform.work.projects.web.ProjectController.TeamMemberResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Individual team members and their notes (FR-WRK-06, FR-WRK-07). */
@RestController
@RequestMapping("/api/v1/team-members")
@Tag(name = "Team members")
public class TeamController {

    private final TeamService team;
    private final WorkspaceMembers members;
    private final Patches patches;

    public TeamController(TeamService team, WorkspaceMembers members, Patches patches) {
        this.team = team;
        this.members = members;
        this.patches = patches;
    }

    @GetMapping("/responsibilities")
    @PreAuthorize("hasAuthority('work:read')")
    public List<String> responsibilities() {
        return Arrays.stream(Responsibility.values()).map(Enum::name).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('work:read')")
    public TeamMemberResponse get(@PathVariable UUID id) {
        return response(team.get(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('work:write')")
    public TeamMemberResponse update(@PathVariable UUID id, @RequestBody JsonNode body) {
        var patch = patches.read(body, UpdateTeamMemberRequest.class);
        UpdateTeamMemberRequest r = patch.value();
        return response(team.update(id, r.position(), r.responsibilities(), patch.has("position"),
                patch.has("responsibilities")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('work:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        team.remove(id);
    }

    @GetMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('work:read')")
    public List<NoteResponse> notes(@PathVariable UUID id) {
        List<TeamMemberNote> list = team.notes(id);
        Map<UUID, MemberSummary> authors = members.findAll(list.stream().map(TeamMemberNote::getCreatedBy)
                .filter(Objects::nonNull).toList());
        return list.stream().map(n -> NoteResponse.of(n, authors.get(n.getCreatedBy()))).toList();
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('work:write')")
    public ResponseEntity<NoteResponse> addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest body) {
        TeamMemberNote n = team.addNote(id, body.text());
        MemberSummary author = n.getCreatedBy() == null ? null
                : members.findAll(List.of(n.getCreatedBy())).get(n.getCreatedBy());
        return ResponseEntity.created(URI.create("/api/v1/team-members/" + id + "/notes/" + n.getId()))
                .body(NoteResponse.of(n, author));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    @PreAuthorize("hasAuthority('work:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNote(@PathVariable UUID id, @PathVariable UUID noteId) {
        team.deleteNote(id, noteId);
    }

    private TeamMemberResponse response(TeamMember tm) {
        return TeamMemberResponse.of(tm, members.findAll(List.of(tm.getUserId())).get(tm.getUserId()));
    }

    // ------------------------------------------------------------------ DTOs

    public record UpdateTeamMemberRequest(@Size(max = 100) String position, Set<Responsibility> responsibilities) {
    }

    public record NoteRequest(@NotBlank @Size(max = 2000) String text) {
    }

    public record NoteResponse(UUID id, UUID teamMemberId, String text, UUID authorUserId, String authorName,
            Instant createdAt) {

        static NoteResponse of(TeamMemberNote n, MemberSummary author) {
            return new NoteResponse(n.getId(), n.getTeamMemberId(), n.getText(), n.getCreatedBy(),
                    author == null ? null : author.fullName(), n.getCreatedAt());
        }
    }
}
