package com.crmplatform.identity.web;

import com.crmplatform.identity.application.AuthSession;
import com.crmplatform.identity.application.InvitationService;
import com.crmplatform.identity.application.MemberService;
import com.crmplatform.identity.application.WorkspaceService;
import com.crmplatform.identity.domain.Invitation;
import com.crmplatform.identity.domain.Role;
import com.crmplatform.identity.domain.User;
import com.crmplatform.identity.domain.Workspace;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Request and response bodies of the identity API. */
public final class IdentityDtos {

    private IdentityDtos() {
    }

    // ------------------------------------------------------------------ requests

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Size(min = 2, max = 80) String workspaceName,
            @Pattern(regexp = "^(ar|en)$") String language,
            @Pattern(regexp = "^(FIT_OUT|SECURITY_SYSTEMS|MAINTENANCE)$") String sector) {
    }

    public record LanguagePreferenceRequest(@Pattern(regexp = "^(ar|en)$") String language) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password, UUID workspaceId) {
    }

    public record SwitchWorkspaceRequest(@NotNull UUID workspaceId) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(max = 128) String newPassword) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(max = 128) String newPassword) {
    }

    public record AcceptInvitationRequest(@NotBlank String token, @Size(max = 60) String firstName,
            @Size(max = 60) String lastName, @Size(max = 128) String password) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @Size(max = 100) String jobTitle,
            @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be in E.164 format") String phone,
            UUID avatarFileId) {
    }

    /** Partial update: only fields present in the JSON change (see {@code Patches}). */
    public record UpdateWorkspaceRequest(
            @Size(min = 2, max = 80) String name,
            @Pattern(regexp = "^(ar|en)$") String defaultLanguage,
            @Size(max = 20) String locale,
            @Size(min = 3, max = 3) String currency,
            @Size(max = 60) String timeZone,
            @Pattern(regexp = "^(FIT_OUT|SECURITY_SYSTEMS|MAINTENANCE)$") String sector) {
    }

    public record DeleteWorkspaceRequest(@NotBlank String confirmName) {
    }

    public record UpdateMemberRequest(Role role, Boolean active) {
    }

    public record CreateInvitationRequest(@NotBlank @Email @Size(max = 254) String email, @NotNull Role role) {
    }

    // ------------------------------------------------------------------ responses

    public record UserSummary(UUID id, String email, String firstName, String lastName, String jobTitle, String phone,
            UUID avatarFileId, boolean superAdmin, String preferredLanguage) {
        static UserSummary of(User u) {
            return new UserSummary(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getJobTitle(),
                    u.getPhone(), u.getAvatarFileId(), u.isSuperAdmin(), u.getPreferredLanguage());
        }
    }

    public record WorkspaceSummary(UUID id, String name, String slug, String defaultLanguage, String locale,
            String currency, String timeZone, String sector) {
        static WorkspaceSummary of(Workspace w) {
            return new WorkspaceSummary(w.getId(), w.getName(), w.getSlug(), w.getDefaultLanguage(), w.getLocale(),
                    w.getCurrency(), w.getTimeZone(), w.getSector() == null ? null : w.getSector().name());
        }
    }

    /** {@code language} is the UI language for this session: the user's preference, else the workspace's. */
    public record AuthResponse(String accessToken, long expiresIn, UserSummary user, WorkspaceSummary workspace,
            String role, Set<String> permissions, String language) {
        static AuthResponse of(AuthSession s) {
            return new AuthResponse(s.accessToken(), s.expiresIn(), UserSummary.of(s.user()),
                    WorkspaceSummary.of(s.workspace()), s.role().name(), new TreeSet<>(s.permissions()),
                    s.user().effectiveLanguage(s.workspace()));
        }
    }

    public record MeResponse(UserSummary user, List<MembershipItem> memberships) {
        record MembershipItem(WorkspaceSummary workspace, String role) {
        }

        static MeResponse of(WorkspaceService.Profile p) {
            return new MeResponse(UserSummary.of(p.user()), p.workspaces().stream()
                    .map(m -> new MembershipItem(WorkspaceSummary.of(m.workspace()), m.role())).toList());
        }
    }

    public record MemberResponse(UUID userId, String email, String firstName, String lastName, String jobTitle,
            String role, boolean active, Instant joinedAt) {
        static MemberResponse of(MemberService.MemberView v) {
            User u = v.user();
            return new MemberResponse(v.membership().getUserId(), u == null ? null : u.getEmail(),
                    u == null ? null : u.getFirstName(), u == null ? null : u.getLastName(),
                    u == null ? null : u.getJobTitle(), v.membership().getRole().name(),
                    v.membership().isActive() && (u == null || u.isActive()), v.membership().getJoinedAt());
        }
    }

    public record InvitationResponse(UUID id, String email, String role, Instant expiresAt, UUID invitedBy,
            Instant createdAt) {
        static InvitationResponse of(Invitation i) {
            return new InvitationResponse(i.getId(), i.getEmail(), i.getRole().name(), i.getExpiresAt(),
                    i.getInvitedBy(), i.getCreatedAt());
        }
    }

    public record InvitationPreviewResponse(String workspaceName, String email, String role, boolean existingUser) {
        static InvitationPreviewResponse of(InvitationService.InvitationPreview p) {
            return new InvitationPreviewResponse(p.workspaceName(), p.email(), p.role().name(), p.existingUser());
        }
    }
}
