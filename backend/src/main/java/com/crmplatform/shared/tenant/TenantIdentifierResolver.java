package com.crmplatform.shared.tenant;

import java.util.Map;
import java.util.UUID;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Supplies Hibernate's discriminator-based multi-tenancy with the active workspace. Every entity
 * with a {@code @TenantId} field is automatically filtered by, and stamped with, this value.
 *
 * <p>Fail-closed: without a context the tenant is {@link TenantContext#NONE}, which matches no
 * row. Only an explicit {@link TenantContext#SYSTEM} scope is treated as root (unfiltered). The
 * tenant is fixed when a Hibernate session opens, so the context must be set <em>before</em> a
 * transaction starts (see {@link TenantWork}).
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.workspaceId().orElse(TenantContext.NONE);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return TenantContext.SYSTEM.equals(tenantId);
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
