package com.crmplatform.billing.web;

import com.crmplatform.billing.application.SubscriptionService;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Read-only mode for workspaces whose subscription ended (D5) or was suspended: reading and
 * exporting keep working, every write is refused with 402 {@code SUBSCRIPTION_INACTIVE} — except
 * what is needed to renew (subscription pages, receipt upload), sign in/out and personal settings.
 * Nothing is ever deleted. Platform-admin requests are not workspace requests and are not affected.
 */
@Configuration(proxyBeanMethods = false)
public class SubscriptionEnforcement implements WebMvcConfigurer {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final List<String> ALWAYS_ALLOWED = List.of("/api/v1/subscription", "/api/v1/auth/",
            "/api/v1/me", "/api/v1/notifications", "/api/v1/admin/");

    private final SubscriptionService subscriptions;

    public SubscriptionEnforcement(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                check(request);
                return true;
            }
        }).addPathPatterns("/api/**");
    }

    void check(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return;
        }
        AuthenticatedUser user = CurrentUser.get().orElse(null);
        if (user == null || user.workspaceId() == null || TenantContext.NONE.equals(user.workspaceId())) {
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (ALWAYS_ALLOWED.stream().anyMatch(path::startsWith) || isReceiptUpload(request, path)) {
            return;
        }
        if (!subscriptions.canWrite(user.workspaceId())) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "SUBSCRIPTION_INACTIVE",
                    "Your subscription is not active; renew it to make changes. Your data is safe and still readable");
        }
    }

    private static boolean isReceiptUpload(HttpServletRequest request, String path) {
        return "POST".equals(request.getMethod()) && path.equals("/api/v1/files")
                && "RECEIPT".equals(request.getParameter("purpose"));
    }
}
