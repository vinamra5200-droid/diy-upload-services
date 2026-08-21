package in.qualtechedge.qcp.templates.properties;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "spring.keycloak")
@Validated
@Data
@Slf4j
public class KeycloakProperties {

    @NotBlank(message = "Keycloak server URL must not be blank")
    private String serverUrl;

    @NotBlank(message = "Keycloak admin username must not be blank")
    private String adminUsername;

    @NotBlank(message = "Keycloak admin password must not be blank")
    private String adminPassword;

    @NotBlank(message = "Keycloak realm must not be blank")
    private String realm;

    private String realmSslRequired = "none";

    private String superAdminRealm = "admin";

    /**
     * Client id of the service's own confidential client in each tenant realm; {@code %s} is the
     * tenant short code. Configuration rather than a constant, because the id carries the product
     * name and a template that hardcodes it puts the template's name in every project's realm.
     */
    @NotBlank(message = "Keycloak backend client id format must not be blank")
    private String backendClientIdFormat = "%s-backend";

    /**
     * Per-tenant client secrets for local development only, keyed by tenant short code.
     *
     * <p>Empty by default and meant to stay empty anywhere real: on a server the secret comes
     * from Vault. This exists so a developer without Vault can still sign in, and it replaced a
     * {@code switch} on hardcoded tenant codes returning a hardcoded string — which silently
     * gave every project cloned from the template the same client secret.
     */
    private Map<String, String> clientSecrets = new HashMap<>();

    /**
     * The platform tenant this deployment belongs to, when identity-portal-service issues the
     * tokens: the {@code qc} in {@code oauth-client-at1-app-qc}.
     *
     * <p>Blank means this service runs against a Keycloak of its own, one realm per tenant, and
     * the host-derived audience below is not used.
     *
     * <p>Set it and the audience becomes mandatory. That matters because the portal puts every
     * application tenant of a platform tenant in <em>one</em> realm, so signature and issuer no
     * longer tell two tenants apart — every token in that realm verifies on every host in it. The
     * client named in the audience is then the only thing that does.
     */
    private String platformTenant;

    /** Realm serving a tenant, or the configured realm when running against a shared one. */
    public String realmForTenant(String tenantCode) {
        if (platformTenant != null && !platformTenant.isBlank()) {
            // One realm per platform tenant: every application tenant shares it.
            return realm;
        }
        return tenantCode == null || tenantCode.isBlank() ? realm : tenantCode;
    }

    /**
     * The client a token must name in its audience, for the tenant this host belongs to.
     *
     * <p>Mirrors identity-portal-service: {@code oauth-client-<scope>}, where scope is the host
     * prefix with the application tenant first. Returns {@code null} when no platform tenant is
     * configured, which is how a service running against its own Keycloak opts out.
     */
    public String audienceForTenant(String tenantCode, String product) {
        if (platformTenant == null || platformTenant.isBlank()) {
            return null;
        }
        String scope = String.join("-",
                tenantCode == null || tenantCode.isBlank() ? "admin" : tenantCode,
                product,
                platformTenant);
        return "oauth-client-" + scope;
    }

    public String issuerUrlFor(String realmName) {
        String base = serverUrl == null ? "" : serverUrl.replaceAll("/+$", "");
        return base + "/realms/" + realmName;
    }

    public String jwkSetUriFor(String realmName) {
        return issuerUrlFor(realmName) + "/protocol/openid-connect/certs";
    }

    /** False when Keycloak is not wired up, so callers can degrade instead of throwing. */
    public boolean isConfigured() {
        return serverUrl != null && !serverUrl.isBlank() && realm != null && !realm.isBlank();
    }

    @PostConstruct
    public void logProperties() {
        log.info("Keycloak properties loaded:");
        log.info("  server-url        : {}", serverUrl);
        log.info("  realm             : {}", realm);
        log.info("  admin-username    : {}", adminUsername);
        log.info("  admin-password    : {}", adminPassword != null ? "[PROTECTED]" : null);
        log.info("  realm-ssl-required: {}", realmSslRequired);
        log.info("  super-admin-realm : {}", superAdminRealm);
        log.info("  backend-client-id : {}", backendClientIdFormat);
        log.info("  client-secrets    : {} configured locally", clientSecrets.size());
        if (!backendClientIdFormat.contains("%s")) {
            throw new IllegalStateException(
                    "spring.keycloak.backend-client-id-format must contain %s for the tenant "
                    + "short code, otherwise every tenant uses one client: " + backendClientIdFormat);
        }
    }

    /** The backend client id for a tenant. */
    public String backendClientId(String tenant) {
        return String.format(backendClientIdFormat, tenant);
    }
}
