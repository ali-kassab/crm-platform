package com.crmplatform.identity.security;

import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the authenticated user's active workspace to {@link TenantContext} (ADR-0004). Callers who
 * are no longer members get 403 NO_WORKSPACE except on endpoints needed to recover (EC-03).
 */
class TenantContextFilter extends OncePerRequestFilter {

    private static final List<String> MEMBERSHIP_OPTIONAL = List.of(
            "/api/v1/me", "/api/v1/auth/", "/api/v1/admin/");

    private final ProblemWriter problemWriter;

    TenantContextFilter(ProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<AuthenticatedUser> user = CurrentUser.get();
        if (user.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        AuthenticatedUser u = user.get();
        String path = request.getRequestURI();
        if (!u.isMember() && MEMBERSHIP_OPTIONAL.stream().noneMatch(path::startsWith)) {
            problemWriter.write(request, response, HttpStatus.FORBIDDEN, "NO_WORKSPACE",
                    "You are no longer a member of this workspace");
            return;
        }
        TenantContext.set(u.workspaceId(), u.userId());
        MDC.put("workspaceId", u.workspaceId().toString());
        MDC.put("userId", u.userId().toString());
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("workspaceId");
            MDC.remove("userId");
        }
    }
}
