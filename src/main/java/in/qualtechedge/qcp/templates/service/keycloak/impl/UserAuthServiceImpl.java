package in.qualtechedge.qcp.templates.service.keycloak.impl;

import in.qualtechedge.qcp.templates.dto.request.KeycloakUserLoginRequest;
import in.qualtechedge.qcp.templates.dto.request.LoginRequest;
import in.qualtechedge.qcp.templates.dto.response.LoginResponse;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.multitenancy.resolution.HostUtils;
import in.qualtechedge.qcp.templates.properties.KeycloakProperties;
import in.qualtechedge.qcp.templates.properties.VaultProperties;
import in.qualtechedge.qcp.templates.service.keycloak.KeycloakAuthService;
import in.qualtechedge.qcp.templates.service.keycloak.UserAuthService;
import in.qualtechedge.qcp.templates.service.vault.VaultService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAuthServiceImpl implements UserAuthService {

    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakProperties keycloakProperties;
    private final VaultProperties vaultProperties;
    private final VaultService vaultService;

    @Override
    public LoginResponse keycloakUserLogin(LoginRequest loginRequest, HttpServletRequest request) {
        // Authentication endpoints are excluded from tenant resolution filter,
        // so we need to manually resolve tenant from Host header
        String hostHeader = request.getHeader("Host");
        String currentTenant = HostUtils.extractSubdomain(hostHeader);

        if (currentTenant == null || currentTenant.isBlank()) {
            currentTenant = keycloakProperties.getSuperAdminRealm();
        }

        log.info("Resolved tenant from Host header {}: {}", hostHeader, currentTenant);

        // Set tenant in context for KeycloakAuthService to use
        HostContext.setCurrentTenant(currentTenant);

        try {
            // Get client credentials from Vault or use default
            String clientSecret = getClientSecret(currentTenant);

            // Build KeycloakUserLoginRequest with resolved credentials
            KeycloakUserLoginRequest keycloakLoginRequest = KeycloakUserLoginRequest.builder()
                    .clientId(keycloakProperties.backendClientId(currentTenant))
                    .grantType("password")
                    .clientSecret(clientSecret)
                    .username(loginRequest.username())
                    .password(loginRequest.password())
                    .scope("openid")
                    .build();

            return keycloakAuthService.authenticateUser(keycloakLoginRequest, request);
        } finally {
            // Clean up context
            HostContext.clear();
        }
    }

    /**
     * The backend client secret for a tenant: Vault where it is enabled, otherwise the local
     * development map.
     *
     * <p>There is deliberately no built-in fallback value. This used to end in a hardcoded
     * secret returned for any unrecognised tenant, which meant the template shipped a working
     * credential that every project cloned from it inherited — and because it *worked*, nothing
     * ever prompted anyone to change it. Failing instead makes the missing configuration
     * obvious at the only moment it can still be fixed cheaply.
     */
    private String getClientSecret(String tenant) {
        if (vaultProperties.isEnabled()) {
            try {
                var secrets = vaultService.getTenantSecret(tenant);
                if (secrets != null && secrets.containsKey("keycloakBackendSecret")) {
                    return (String) secrets.get("keycloakBackendSecret");
                }
                log.warn("Vault holds no keycloakBackendSecret for tenant {}", tenant);
            } catch (Exception e) {
                log.warn("Failed to read the client secret from Vault for tenant {}", tenant, e);
            }
        }

        String configured = keycloakProperties.getClientSecrets().get(tenant);
        if (configured != null && !configured.isBlank()) {
            log.debug("Using the locally configured client secret for tenant {}", tenant);
            return configured;
        }

        throw new IllegalStateException(
                "No Keycloak client secret for tenant '" + tenant + "'. Store it in Vault as "
                + "keycloakBackendSecret, or for local development set "
                + "spring.keycloak.client-secrets." + tenant);
    }
}
