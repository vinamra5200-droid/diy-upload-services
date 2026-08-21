package in.qualtechedge.qcp.templates.multitenancy.routing;

import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which tenant the current session belongs to: the tenant resolved into
 * {@link HostContext} by the resolution filter, or {@code system} (the superadmin/system DB)
 * when no tenant context is set (admin endpoints, startup, actuator).
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = HostContext.getCurrentTenant();
        return tenant != null ? tenant : HostContext.SYSTEM_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
