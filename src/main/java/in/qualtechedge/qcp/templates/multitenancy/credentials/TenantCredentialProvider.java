package in.qualtechedge.qcp.templates.multitenancy.credentials;

import in.qualtechedge.qcp.templates.multitenancy.config.MultiTenancyProperties.TenantCredentials;
import java.util.Optional;

/**
 * Source of per-tenant database credentials (QCC Multi-Tenancy §4).
 * <p>
 * The platform standard is Vault (AppRole) — this interface is the seam: v1 ships the
 * config-based implementation; the Vault implementation slots in later without touching
 * the datasource manager, provisioning or routing code.
 */
public interface TenantCredentialProvider {

    /** Returns the DB credentials for the tenant, or empty when none are configured. */
    Optional<TenantCredentials> getCredentials(String tenantCode);
}
