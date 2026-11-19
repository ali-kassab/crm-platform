package com.crmplatform.identity.application;

import com.crmplatform.identity.domain.Invitation;
import com.crmplatform.identity.domain.InvitationRepository;
import com.crmplatform.identity.domain.Membership;
import com.crmplatform.identity.domain.MembershipRepository;
import com.crmplatform.identity.domain.Role;
import com.crmplatform.identity.domain.User;
import com.crmplatform.identity.domain.UserRepository;
import com.crmplatform.identity.domain.Workspace;
import com.crmplatform.identity.domain.WorkspaceRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.mail.EmailSender;
import com.crmplatform.shared.mail.EmailTemplates;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Invite teammates and accept invitations (AUTH_FLOWS F8). */
@Service
public class InvitationService {

    private final InvitationRepository invitations;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final WorkspaceRepository workspaces;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final AuthService authService;
    private final IdentityProperties properties;
    private final Clock clock;
    private final String clientBaseUrl;

    public InvitationService(InvitationRepository invitations, MembershipRepository memberships, UserRepository users,
            WorkspaceRepository workspaces, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
            EmailSender emailSender, EmailTemplates templates, AuthService authService, IdentityProperties properties,
            Clock clock, @Value("${app.client-base-url}") String clientBaseUrl) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.users = users;
        this.workspaces = workspaces;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.emailSender = emailSender;
        this.templates = templates;
        this.authService = authService;
        this.properties = properties;
        this.clock = clock;
        this.clientBaseUrl = clientBaseUrl;
    }

    @Transactional
    public Invitation invite(UUID workspaceId, UUID inviterId, Role inviterRole, String rawEmail, Role role) {
        if (!inviterRole.canGrant(role)) {
            throw ApiException.forbidden("You cannot invite members with this role");
        }
        String email = User.normalizeEmail(rawEmail);
        users.findByEmail(email).ifPresent(existing -> {
            if (memberships.existsByWorkspaceIdAndUserIdAndActiveTrue(workspaceId, existing.getId())) {
                throw ApiException.conflict("ALREADY_MEMBER", "This person is already a member of the workspace");
            }
        });
        Instant now = Instant.now(clock);
        invitations.findPending(workspaceId, email).ifPresent(old -> {
            old.revoke(now);
            invitations.saveAndFlush(old);
        });
        String raw = SecureTokens.newToken();
        Invitation invitation = invitations.save(new Invitation(workspaceId, email, role, SecureTokens.hash(raw),
                now.plus(properties.invitationTtl()), inviterId));

        Workspace workspace = workspaces.findById(workspaceId).orElseThrow(() -> ApiException.notFound("Workspace"));
        // an existing account's language preference wins; otherwise the inviting workspace's default
        String language = users.findByEmail(email).map(u -> u.effectiveLanguage(workspace))
                .orElse(workspace.getDefaultLanguage());
        String inviterName = users.findById(inviterId).map(User::getFullName).orElse("");
        String html = templates.render("workspace-invitation", language, Map.of(
                "inviterName", inviterName,
                "workspaceName", workspace.getName(),
                "role", templates.text("role." + role.name(), language),
                "acceptUrl", clientBaseUrl + "/accept-invite?token=" + raw,
                "expiresInDays", properties.invitationTtl().toDays()));
        String subject = templates.subject("invitation", language, workspace.getName());
        afterCommit(() -> emailSender.send(new EmailSender.EmailMessage(List.of(email), subject, html, null)));
        return invitation;
    }

    @Transactional(readOnly = true)
    public List<Invitation> pending(UUID workspaceId) {
        Instant now = Instant.now(clock);
        return invitations.findAllPending(workspaceId).stream().filter(i -> i.isPending(now)).toList();
    }

    @Transactional
    public void revoke(UUID workspaceId, UUID invitationId) {
        Invitation invitation = invitations.findByIdAndWorkspaceId(invitationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Invitation"));
        invitation.revoke(Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public InvitationPreview preview(String rawToken) {
        Invitation invitation = findPending(rawToken);
        Workspace workspace = workspaces.findById(invitation.getWorkspaceId())
                .orElseThrow(() -> ApiException.notFound("Invitation"));
        boolean existingUser = users.existsByEmail(invitation.getEmail());
        return new InvitationPreview(workspace.getName(), invitation.getEmail(), invitation.getRole(), existingUser);
    }

    /**
     * Accepts an invitation. New emails create an account; an existing account must be the
     * authenticated caller ({@code authenticatedUserId}).
     */
    @Transactional
    public AuthSession accept(AcceptCommand cmd, UUID authenticatedUserId, AuthService.ClientInfo client) {
        Invitation invitation = findPending(cmd.token());
        Instant now = Instant.now(clock);
        User user = users.findByEmail(invitation.getEmail()).orElse(null);
        if (user == null) {
            if (isBlank(cmd.firstName()) || isBlank(cmd.lastName()) || cmd.password() == null) {
                throw ApiException.badRequest("VALIDATION_ERROR", "firstName, lastName and password are required");
            }
            passwordPolicy.validate(cmd.password(), invitation.getEmail());
            user = users.save(new User(invitation.getEmail(), passwordEncoder.encode(cmd.password()), cmd.firstName(),
                    cmd.lastName()));
        } else if (!user.getId().equals(authenticatedUserId)) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Sign in as " + invitation.getEmail() + " to accept");
        }
        UUID userId = user.getId();
        Membership membership = memberships.findByWorkspaceIdAndUserId(invitation.getWorkspaceId(), userId)
                .orElseGet(() -> new Membership(invitation.getWorkspaceId(), userId, invitation.getRole(), now));
        membership.changeRole(invitation.getRole());
        membership.setActive(true);
        memberships.save(membership);
        invitation.accept(now);
        user.recordLogin(now, invitation.getWorkspaceId());
        Workspace workspace = workspaces.findById(invitation.getWorkspaceId())
                .orElseThrow(() -> ApiException.notFound("Invitation"));
        return authService.openSession(user, workspace, invitation.getRole(), client);
    }

    private Invitation findPending(String rawToken) {
        Instant now = Instant.now(clock);
        return invitations.findByTokenHash(SecureTokens.hash(rawToken == null ? "" : rawToken))
                .filter(i -> i.isPending(now))
                .orElseThrow(() -> ApiException.notFound("Invitation"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    public record InvitationPreview(String workspaceName, String email, Role role, boolean existingUser) {
    }

    public record AcceptCommand(String token, String firstName, String lastName, String password) {
    }
}
