package com.crmplatform.identity.web;

import com.crmplatform.identity.application.PlatformAdminService;
import com.crmplatform.identity.domain.User;
import com.crmplatform.identity.web.IdentityDtos.UserSummary;
import com.crmplatform.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-admin sign-in and own-account management. There is intentionally no endpoint that
 * creates or promotes a platform admin; the first one comes from the environment at startup.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Platform admin")
public class PlatformAdminController {

    private final PlatformAdminService admins;

    public PlatformAdminController(PlatformAdminService admins) {
        this.admins = admins;
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Platform-admin sign-in (short-lived token, no workspace, no refresh)")
    public AdminSessionResponse login(@Valid @RequestBody AdminLoginRequest body, HttpServletRequest request) {
        var session = admins.login(body.email(), body.password(), request.getRemoteAddr());
        return new AdminSessionResponse(session.accessToken(), session.expiresIn(), UserSummary.of(session.user()));
    }

    @GetMapping("/account")
    @PreAuthorize("hasAuthority('platform:admin')")
    public UserSummary account() {
        return UserSummary.of(admins.account(CurrentUser.id()));
    }

    @PostMapping("/account/email")
    @PreAuthorize("hasAuthority('platform:admin')")
    public UserSummary changeEmail(@Valid @RequestBody ChangeEmailRequest body) {
        User admin = admins.changeEmail(CurrentUser.id(), body.currentPassword(), body.newEmail());
        return UserSummary.of(admin);
    }

    @PostMapping("/account/password")
    @PreAuthorize("hasAuthority('platform:admin')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        admins.changePassword(CurrentUser.id(), body.currentPassword(), body.newPassword());
    }

    @PatchMapping("/account")
    @PreAuthorize("hasAuthority('platform:admin')")
    public UserSummary updateAccount(@Valid @RequestBody UpdateAccountRequest body) {
        return UserSummary.of(admins.updateName(CurrentUser.id(), body.firstName(), body.lastName()));
    }

    public record UpdateAccountRequest(@NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName) {
    }

    public record AdminLoginRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
    }

    public record ChangeEmailRequest(@NotBlank String currentPassword, @NotBlank @Email @Size(max = 254) String newEmail) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(max = 128) String newPassword) {
    }

    public record AdminSessionResponse(String accessToken, long expiresIn, UserSummary user) {
    }
}
