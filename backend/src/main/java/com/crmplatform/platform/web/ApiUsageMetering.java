package com.crmplatform.platform.web;

import com.crmplatform.platform.application.ApiUsageMeter;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Counts each authenticated workspace API request (count only) for the platform usage view.
 * Platform-admin calls carry no workspace and are not counted.
 */
@Configuration(proxyBeanMethods = false)
public class ApiUsageMetering implements WebMvcConfigurer {

    private final ApiUsageMeter meter;

    public ApiUsageMetering(ApiUsageMeter meter) {
        this.meter = meter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                AuthenticatedUser user = CurrentUser.get().orElse(null);
                if (user != null && user.workspaceId() != null && !TenantContext.NONE.equals(user.workspaceId())
                        && !TenantContext.SYSTEM.equals(user.workspaceId()) && user.isMember()) {
                    meter.record(user.workspaceId());
                }
                return true;
            }
        }).addPathPatterns("/api/**").excludePathPatterns("/api/v1/admin/**");
    }
}
