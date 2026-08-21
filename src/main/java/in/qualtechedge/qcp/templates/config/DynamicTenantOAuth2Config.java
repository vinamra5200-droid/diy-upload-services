package in.qualtechedge.qcp.templates.config;

import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.properties.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * Dynamic OAuth2 client configuration for Keycloak with multi-tenant support.
 * <p>
 * Creates a default {@link ClientRegistrationRepository} at startup (required by Spring Security
 * auto-configuration). At runtime, callers can obtain a tenant-specific
 * {@link ClientRegistration} via {@link #getClientRegistrationForCurrentTenant()} which
 * reads the tenant from {@link HostContext}.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class DynamicTenantOAuth2Config {

    private final KeycloakProperties keycloakProperties;

    /**
     * Default client registration repository (uses "default" realm as placeholder).
     * The actual realm is resolved per-request at runtime.
     */
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration defaultRegistration = createClientRegistration("default");
        return new InMemoryClientRegistrationRepository(defaultRegistration);
    }

    /**
     * Build a {@link ClientRegistration} for a specific tenant/realm.
     *
     * @param tenant the tenant short code (= Keycloak realm name)
     * @return a fully configured {@link ClientRegistration}
     */
    private ClientRegistration createClientRegistration(String tenant) {
        log.debug("Creating OAuth2 client registration for tenant: {}", tenant);

        String issuerUri = String.format("%s/realms/%s", keycloakProperties.getServerUrl(), tenant);
        String tokenUri = String.format("%s/realms/%s/protocol/openid-connect/token",
                keycloakProperties.getServerUrl(), tenant);
        String authorizationUri = String.format("%s/realms/%s/protocol/openid-connect/auth",
                keycloakProperties.getServerUrl(), tenant);
        String userInfoUri = String.format("%s/realms/%s/protocol/openid-connect/userinfo",
                keycloakProperties.getServerUrl(), tenant);
        String jwkSetUri = String.format("%s/realms/%s/protocol/openid-connect/certs",
                keycloakProperties.getServerUrl(), tenant);

        // The secret was previously built from the client-id format as well, so every
        // registration authenticated with its own client id as the password and Keycloak
        // rejected it. Local secrets come from configuration; on a server they come from Vault
        // and this registration is a placeholder the runtime path replaces per request.
        String clientSecret = keycloakProperties.getClientSecrets().getOrDefault(tenant, "");

        return ClientRegistration.withRegistrationId("keycloak")
                .clientId(keycloakProperties.backendClientId(tenant))
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(authorizationUri)
                .tokenUri(tokenUri)
                .userInfoUri(userInfoUri)
                .jwkSetUri(jwkSetUri)
                .issuerUri(issuerUri)
                .userNameAttributeName("preferred_username")
                .clientName("Keycloak")
                .build();
    }

    /**
     * Return the {@link ClientRegistration} for the tenant currently in {@link HostContext}.
     * Falls back to {@code "default"} if no tenant is set.
     */
    public ClientRegistration getClientRegistrationForCurrentTenant() {
        String tenant = HostContext.getCurrentTenant();
        if (tenant == null || tenant.isEmpty()) {
            log.warn("No tenant found in context, using default tenant");
            tenant = "default";
        }
        log.debug("Getting OAuth2 client registration for tenant: {}", tenant);
        return createClientRegistration(tenant);
    }

    /**
     * Convenience method that returns client registrations for a set of known tenants.
     * Useful for testing or administrative tooling.
     *
     * @return map of tenant short code → {@link ClientRegistration}
     */
    public Map<String, ClientRegistration> getAllTenantRegistrations() {
        Map<String, ClientRegistration> registrations = new HashMap<>();
        String[] commonTenants = {"default", "qc", "admin", "dev", "test"};
        for (String tenant : commonTenants) {
            registrations.put(tenant, createClientRegistration(tenant));
        }
        return registrations;
    }
}
