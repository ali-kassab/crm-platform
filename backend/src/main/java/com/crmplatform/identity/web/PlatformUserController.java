package com.crmplatform.identity.web;

import com.crmplatform.identity.application.PlatformUserService;
import com.crmplatform.identity.application.PlatformUserService.UserFilter;
import com.crmplatform.identity.application.PlatformUserService.UserRow;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform admin: registered users across companies (account metadata only) and sign-in access. */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Platform admin — users")
@PreAuthorize("hasAuthority('platform:admin')")
public class PlatformUserController {

    private final PlatformUserService users;

    public PlatformUserController(PlatformUserService users) {
        this.users = users;
    }

    @GetMapping
    public PageResponse<UserRow> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(required = false) String kind,
            @RequestParam(required = false) UUID workspaceId, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
        return users.list(new UserFilter(q, status, kind, workspaceId), page, size, sort);
    }

    @PostMapping("/{id}/deactivate")
    public UserRow deactivate(@PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        return users.setActive(CurrentUser.id(), id, false, body.reason());
    }

    @PostMapping("/{id}/activate")
    public UserRow activate(@PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest body) {
        return users.setActive(CurrentUser.id(), id, true, body == null ? null : body.reason());
    }

    public record ReasonRequest(@Size(max = 500) String reason) {
    }
}
