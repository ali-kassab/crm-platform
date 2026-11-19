package com.crmplatform.identity.web;

import com.crmplatform.identity.application.InvitationService;
import com.crmplatform.identity.application.MemberService;
import com.crmplatform.identity.application.WorkspaceService;
import com.crmplatform.identity.domain.Role;
import com.crmplatform.identity.web.IdentityDtos.CreateInvitationRequest;
import com.crmplatform.identity.web.IdentityDtos.DeleteWorkspaceRequest;
import com.crmplatform.identity.web.IdentityDtos.InvitationResponse;
import com.crmplatform.identity.web.IdentityDtos.MeResponse;
import com.crmplatform.identity.web.IdentityDtos.MemberResponse;
import com.crmplatform.identity.web.IdentityDtos.UpdateMemberRequest;
import com.crmplatform.identity.web.IdentityDtos.UpdateProfileRequest;
import com.crmplatform.identity.web.IdentityDtos.UpdateWorkspaceRequest;
import com.crmplatform.identity.web.IdentityDtos.UserSummary;
import com.crmplatform.identity.web.IdentityDtos.WorkspaceSummary;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Workspace")
public class WorkspaceController {

    private static final Map<String, String> MEMBER_SORT = Map.of("joinedAt", "joinedAt", "role", "role");

    private final WorkspaceService workspaces;
    private final MemberService members;
    private final InvitationService invitations;

    public WorkspaceController(WorkspaceService workspaces, MemberService members, InvitationService invitations) {
        this.workspaces = workspaces;
        this.members = members;
        this.invitations = invitations;
    }

    // -------------------------------------------------------------- profile

    @GetMapping("/me")
    public MeResponse me() {
        return MeResponse.of(workspaces.profile(CurrentUser.id()));
    }

    @PatchMapping("/me")
    public UserSummary updateMe(@Valid @RequestBody UpdateProfileRequest body) {
        return UserSummary.of(workspaces.updateProfile(CurrentUser.id(), body.firstName(), body.lastName(),
                body.jobTitle(), body.phone(), body.avatarFileId()));
    }

    // -------------------------------------------------------------- workspace

    @GetMapping("/workspace")
    public WorkspaceSummary workspace() {
        return WorkspaceSummary.of(workspaces.get(CurrentUser.require().workspaceId()));
    }

    @PatchMapping("/workspace")
    @PreAuthorize("hasAuthority('workspace:manage')")
    public WorkspaceSummary rename(@Valid @RequestBody UpdateWorkspaceRequest body) {
        return WorkspaceSummary.of(workspaces.rename(CurrentUser.require().workspaceId(), body.name()));
    }

    @DeleteMapping("/workspace")
    @PreAuthorize("hasAuthority('workspace:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Valid @RequestBody DeleteWorkspaceRequest body) {
        workspaces.delete(CurrentUser.require().workspaceId(), body.confirmName());
    }

    // -------------------------------------------------------------- members

    @GetMapping("/workspace/members")
    public PageResponse<MemberResponse> members(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, MEMBER_SORT, Sort.by("joinedAt"));
        return PageResponse.of(members.list(CurrentUser.require().workspaceId(), pageable), MemberResponse::of);
    }

    @PatchMapping("/workspace/members/{userId}")
    @PreAuthorize("hasAuthority('members:manage')")
    public MemberResponse updateMember(@PathVariable UUID userId, @Valid @RequestBody UpdateMemberRequest body) {
        AuthenticatedUser actor = CurrentUser.require();
        return MemberResponse.of(members.update(actor.workspaceId(), actor.userId(), Role.valueOf(actor.role()), userId,
                body.role(), body.active()));
    }

    @DeleteMapping("/workspace/members/{userId}")
    @PreAuthorize("hasAuthority('members:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID userId) {
        AuthenticatedUser actor = CurrentUser.require();
        members.remove(actor.workspaceId(), actor.userId(), Role.valueOf(actor.role()), userId);
    }

    // -------------------------------------------------------------- invitations

    @GetMapping("/workspace/invitations")
    @PreAuthorize("hasAuthority('members:manage')")
    public List<InvitationResponse> invitations() {
        return invitations.pending(CurrentUser.require().workspaceId()).stream().map(InvitationResponse::of).toList();
    }

    @PostMapping("/workspace/invitations")
    @PreAuthorize("hasAuthority('members:manage')")
    public ResponseEntity<InvitationResponse> invite(@Valid @RequestBody CreateInvitationRequest body) {
        AuthenticatedUser actor = CurrentUser.require();
        var invitation = invitations.invite(actor.workspaceId(), actor.userId(), Role.valueOf(actor.role()),
                body.email(), body.role());
        return ResponseEntity.created(URI.create("/api/v1/workspace/invitations/" + invitation.getId()))
                .body(InvitationResponse.of(invitation));
    }

    @DeleteMapping("/workspace/invitations/{id}")
    @PreAuthorize("hasAuthority('members:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(@PathVariable UUID id) {
        invitations.revoke(CurrentUser.require().workspaceId(), id);
    }

    // -------------------------------------------------------------- platform

    @GetMapping("/admin/stats")
    @PreAuthorize("hasAuthority('platform:admin')")
    public WorkspaceService.PlatformStats platformStats() {
        return workspaces.platformStats();
    }
}
