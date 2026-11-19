package com.crmplatform.identity.web;

import com.crmplatform.identity.application.AuthService;
import com.crmplatform.identity.application.AuthSession;
import com.crmplatform.identity.application.InvitationService;
import com.crmplatform.identity.web.IdentityDtos.AcceptInvitationRequest;
import com.crmplatform.identity.web.IdentityDtos.AuthResponse;
import com.crmplatform.identity.web.IdentityDtos.ChangePasswordRequest;
import com.crmplatform.identity.web.IdentityDtos.ForgotPasswordRequest;
import com.crmplatform.identity.web.IdentityDtos.InvitationPreviewResponse;
import com.crmplatform.identity.web.IdentityDtos.LoginRequest;
import com.crmplatform.identity.web.IdentityDtos.RegisterRequest;
import com.crmplatform.identity.web.IdentityDtos.ResetPasswordRequest;
import com.crmplatform.identity.web.IdentityDtos.SwitchWorkspaceRequest;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService auth;
    private final InvitationService invitations;
    private final RefreshCookies cookies;

    public AuthController(AuthService auth, InvitationService invitations, RefreshCookies cookies) {
        this.auth = auth;
        this.invitations = invitations;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Register a user and create their workspace")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        AuthSession session = auth.register(new AuthService.RegisterCommand(body.email(), body.password(),
                body.firstName(), body.lastName(), body.workspaceName()), client(request));
        return withCookie(HttpStatus.CREATED, session);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Sign in with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return withCookie(HttpStatus.OK, auth.login(body.email(), body.password(), body.workspaceId(), client(request)));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Exchange the refresh cookie for a new access token (rotates the cookie)")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = RefreshCookies.NAME, required = false) String token,
            HttpServletRequest request) {
        RefreshCookies.requireXhr(request);
        return withCookie(HttpStatus.OK, auth.refresh(token, client(request)));
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Sign out of this session")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookies.NAME, required = false) String token,
            HttpServletRequest request) {
        RefreshCookies.requireXhr(request);
        auth.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Sign out of every session")
    public ResponseEntity<Void> logoutAll() {
        AuthenticatedUser user = CurrentUser.require();
        auth.logoutAll(user.userId(), user.tokenId(), user.tokenExpiresAt());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @PostMapping("/switch-workspace")
    @Operation(summary = "Switch the active workspace")
    public ResponseEntity<AuthResponse> switchWorkspace(@Valid @RequestBody SwitchWorkspaceRequest body,
            HttpServletRequest request) {
        return withCookie(HttpStatus.OK, auth.switchWorkspace(CurrentUser.id(), body.workspaceId(), client(request)));
    }

    @PostMapping("/password/forgot")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Email a password reset link (always 202)")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest body, HttpServletRequest request) {
        auth.requestPasswordReset(body.email(), client(request));
    }

    @PostMapping("/password/reset")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a new password using a reset token")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        auth.resetPassword(body.token(), body.newPassword());
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change your password (other sessions are signed out)")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body,
            @CookieValue(name = RefreshCookies.NAME, required = false) String token) {
        auth.changePassword(CurrentUser.id(), body.currentPassword(), body.newPassword(), token);
    }

    @GetMapping("/invitations/{token}")
    @SecurityRequirements
    @Operation(summary = "Preview an invitation")
    public InvitationPreviewResponse previewInvitation(@PathVariable String token) {
        return InvitationPreviewResponse.of(invitations.preview(token));
    }

    @PostMapping("/invitations/accept")
    @SecurityRequirements
    @Operation(summary = "Accept an invitation (new users set a password; existing users must be signed in)")
    public ResponseEntity<AuthResponse> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest body,
            HttpServletRequest request) {
        UUID caller = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        AuthSession session = invitations.accept(new InvitationService.AcceptCommand(body.token(), body.firstName(),
                body.lastName(), body.password()), caller, client(request));
        return withCookie(HttpStatus.OK, session);
    }

    private ResponseEntity<AuthResponse> withCookie(HttpStatus status, AuthSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.set(session.refreshToken(), session.refreshExpiresAt()))
                .body(AuthResponse.of(session));
    }

    static AuthService.ClientInfo client(HttpServletRequest request) {
        return new AuthService.ClientInfo(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
