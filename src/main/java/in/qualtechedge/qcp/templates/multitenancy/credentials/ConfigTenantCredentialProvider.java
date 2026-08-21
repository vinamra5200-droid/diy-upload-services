package in.qualtechedge.qcp.templates.multitenancy.credentials;

import in.qualtechedge.qcp.templates.multitenancy.config.MultiTenancyProperties;
import in.qualtechedge.qcp.templates.multitenancy.config.MultiTenancyProperties.TenantCredentials;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Config-based credential provider: reads per-tenant DB credentials from
 * {@code qcp.multitenancy.tenants.<code>.db-username/db-password}.
 * <p>
 * v1 only — production deployments replace this with the Vault-backed provider
 * (credentials must never live in config files on server environments).
 */
@Component
@RequiredArgsConstructor
public class ConfigTenantCredentialProvider implements TenantCredentialProvider {

    private final MultiTenancyProperties properties;

    @Override
    public Optional<TenantCredentials> getCredentials(String tenantCode) {
        if (tenantCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(properties.tenants().get(tenantCode.toLowerCase(Locale.ROOT)));
    }
}
