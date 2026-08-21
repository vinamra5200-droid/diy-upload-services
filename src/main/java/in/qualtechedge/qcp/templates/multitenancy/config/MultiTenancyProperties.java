package in.qualtechedge.qcp.templates.multitenancy.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Multi-tenancy configuration ({@code qcp.multitenancy.*}).
 * <p>
 * v1 keeps tenant DB credentials in configuration ({@code tenants} map) via
 * {@code ConfigTenantCredentialProvider}. When Vault is introduced, a Vault-backed
 * {@code TenantCredentialProvider} replaces the map — nothing else changes.
 *
 * @param tenants       per-tenant DB credentials keyed by tenant short code (lowercase)
 * @param dbNameFormat  database name pattern for tenant DBs; %s = tenant short code
 * @param excludedPaths URI prefixes that bypass tenant resolution (system scope)
 */
@ConfigurationProperties(prefix = "qcp.multitenancy")
public record MultiTenancyProperties(
        Map<String, TenantCredentials> tenants,
        String dbNameFormat,
        List<String> excludedPaths,
        HostConvention host
) {
    public MultiTenancyProperties {
        tenants = tenants == null ? Map.of() : tenants;
        host = host == null ? new HostConvention(null, null, null, null) : host;
        // No default. The old one repeated the template's own name, so a project that renamed
        // the value in application.yaml and later dropped the key would silently start
        // provisioning databases named after the template — and the first sign of it is a
        // tenant whose data is in a database nobody is looking at. Failing here instead names
        // the missing key while there is still nothing to clean up.
        if (dbNameFormat == null || dbNameFormat.isBlank()) {
            throw new IllegalStateException(
                    "qcp.multitenancy.db-name-format is required — set it to this product's "
                    + "tenant database pattern, e.g. myapp-%s-db");
        }
        if (!dbNameFormat.contains("%s")) {
            // Without the placeholder every tenant resolves to the same database name, which
            // means every tenant shares one database. That is the failure this whole service
            // exists to prevent, so it is worth refusing at startup.
            throw new IllegalStateException(
                    "qcp.multitenancy.db-name-format must contain %s for the tenant short code, "
                    + "otherwise every tenant resolves to the same database: " + dbNameFormat);
        }
        excludedPaths = excludedPaths == null
                ? List.of("/actuator", "/swagger-ui", "/v3/api-docs", "/api/v1/admin")
                : excludedPaths;
    }

    /**
     * The hostname convention this deployment answers to:
     * {@code {tenant}-{product}-{env}.{baseDomain}}.
     *
     * <p>All three middle parts are checked, not just the tenant code. Without that,
     * {@code qc-other-dev} (a different product) and {@code qc-app-prod} (a different
     * environment) both resolve to tenant {@code qc} on this instance.
     *
     * @param product     the product segment this deployment serves, e.g. {@code app}
     * @param environment the environment segment; blank means "use the active profile"
     * @param baseDomain  domain the subdomains sit under; blank disables the check
     * @param systemHosts hosts that mean "console scope" without a subdomain — an allowlist, so
     *                    an unrecognised host is refused rather than assumed to be the console
     */
    public record HostConvention(String product, String environment, String baseDomain,
            List<String> systemHosts) {
        public HostConvention {
            // No product default. A template that supplies one puts the template's own name in
            // the host grammar of every project cloned from it, and the mismatch only shows up
            // as tenants failing to resolve on correctly-named hosts.
            baseDomain = baseDomain == null ? "qualtechedge.in" : baseDomain;
            systemHosts = systemHosts == null
                    ? List.of("localhost", "127.0.0.1", "[::1]")
                    // An empty override (QCP_HOST_SYSTEM_HOSTS=) binds as [""] — blank entries
                    // would match nothing but still read as "a list is configured".
                    : systemHosts.stream().filter(h -> h != null && !h.isBlank()).toList();
        }
    }

    /** DB credentials for one tenant (config-based v1 of the Vault contract). */
    public record TenantCredentials(String dbUsername, String dbPassword) {
    }
}
