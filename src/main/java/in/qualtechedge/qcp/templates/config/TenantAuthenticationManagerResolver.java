package in.qualtechedge.qcp.templates.config;

import in.qualtechedge.qcp.templates.multitenancy.config.MultiTenancyProperties;
import in.qualtechedge.qcp.templates.multitenancy.resolution.HostScope;
import in.qualtechedge.qcp.templates.multitenancy.resolution.HostUtils;
import in.qualtechedge.qcp.templates.properties.KeycloakProperties;
import in.qualtechedge.qcp.templates.security.CustomJwtDecoder;
import in.qualtechedge.qcp.templates.security.tenant.TenantContext;
import in.qualtechedge.qcp.templates.utils.DeploymentEnvironment;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the correct {@link AuthenticationManager} for a request, from the host it addresses.
 * <p>
 * Each (realm, audience) pair gets its own {@link JwtAuthenticationProvider} backed by a
 * {@link in.qualtechedge.qcp.templates.security.CustomJwtDecoder} pointing at that realm's
 * JWKS endpoint. Providers are cached in a {@link ConcurrentHashMap} so the JWKS is only
 * fetched once per pair per service lifetime.
 * <p>
 * This class also populates {@link TenantContext} with the resolved realm and tenant so
 * downstream code can reference them without re-parsing the token.
 */
@Component
@Slf4j
public class TenantAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {

    private final Map<String, AuthenticationManager> managers = new ConcurrentHashMap<>();
    private final KeycloakProperties keycloakProperties;
    private final CustomJwtDecoder customJwtDecoder;
    private final MultiTenancyProperties multiTenancy;
    private final DeploymentEnvironment deploymentEnvironment;

    @Value("${http-client.ssl-verify:false}")
    private boolean sslVerify;

    public TenantAuthenticationManagerResolver(CustomJwtDecoder customJwtDecoder,
                                               KeycloakProperties keycloakProperties,
                                               MultiTenancyProperties multiTenancy,
                                               DeploymentEnvironment deploymentEnvironment) {
        this.customJwtDecoder = customJwtDecoder;
        this.keycloakProperties = keycloakProperties;
        this.multiTenancy = multiTenancy;
        this.deploymentEnvironment = deploymentEnvironment;
    }

    /**
     * The realm comes from the <em>host</em>, and the audience with it.
     * <p>
     * It used to come from the token's own {@code iss} claim, which lets the credential choose
     * the authority that checks it: present a token from any realm this service can reach and
     * that realm's keys were fetched to verify it. Signature and issuer then agree, because they
     * were never in dispute — the question is whether this token was minted for <em>this</em>
     * host, and the token was answering it itself.
     * <p>
     * That is survivable while every tenant has its own realm. It is not once
     * identity-portal-service puts every application tenant of a platform tenant in one realm:
     * at that point one tenant's token verifies perfectly on another tenant's host, and the
     * audience is the only thing left that separates them.
     */
    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        try {
            HostScope scope = HostUtils.resolve(HostUtils.fromRequest(request),
                    multiTenancy.host().product(),
                    deploymentEnvironment.current(),
                    multiTenancy.host().baseDomain(),
                    multiTenancy.host().systemHosts());

            if (scope.isInvalid()) {
                log.warn("SECURITY: refusing to authenticate a request on an unrecognised host: {}", scope.reason());
                return null;
            }

            String tenantCode = scope.isTenant() ? scope.tenantCode() : null;
            String realm = keycloakProperties.realmForTenant(tenantCode);
            String audience = keycloakProperties.audienceForTenant(tenantCode,
                    multiTenancy.host().product());

            TenantContext.setRealm(realm);
            TenantContext.setTenant(tenantCode == null ? realm : tenantCode);

            // Keyed on realm AND audience, not realm alone. Many hosts share one realm, so a
            // cache keyed only by realm hands back whichever manager was built first — and the
            // audience check silently becomes a check against another tenant's client.
            String key = realm + "|" + audience;
            return managers.computeIfAbsent(key, k -> buildManager(realm, audience));
        } catch (Exception e) {
            log.error("Error resolving authentication manager", e);
            return null;
        }
    }

    private AuthenticationManager buildManager(String realm, String audience) {
        String issuer = keycloakProperties.issuerUrlFor(realm);
        log.info("SECURITY: building token validation for realm '{}' (issuer {}){}", realm, issuer,
                audience == null ? "" : ", audience '" + audience + "'");

        JwtDecoder decoder = customJwtDecoder.createDecoderForIssuer(issuer, sslVerify);
        if (audience != null && decoder instanceof NimbusJwtDecoder nimbus) {
            requireAudience(nimbus, issuer, audience);
        }
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(createJwtAuthenticationConverter());

        return provider::authenticate;
    }

    /**
     * Refuse a token that does not name this host's client in {@code aud}.
     * <p>
     * Every realm also holds {@code account}, {@code admin-cli} and
     * {@code security-admin-console}, and a token from any of them carries the same signature and
     * issuer as a real one — so signature and issuer alone prove only the realm.
     * <p>
     * {@code setJwtValidator} <em>replaces</em> the default chain rather than adding to it, and
     * that chain is where expiry lives: Spring installs a no-op claims verifier into the Nimbus
     * processor because it expects the validator chain to do that work. Timestamp and issuer are
     * therefore repeated here on purpose — omitting them switches expiry checking off entirely.
     */
    private void requireAudience(NimbusJwtDecoder decoder, String issuer, String audience) {
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer),
                token -> token.getAudience() != null && token.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                OAuth2ErrorCodes.INVALID_TOKEN,
                                "Token is not intended for '" + audience + "'", null))));
    }

    private JwtAuthenticationConverter createJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            log.debug("Extracting authorities from JWT for user: {}", jwt.getSubject());

            Set<GrantedAuthority> authorities = new HashSet<>();

            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof List<?> roles) {
                    for (Object role : roles) {
                        authorities.add(new SimpleGrantedAuthority(role.toString()));
                        log.debug("Added realm role: {}", role);
                    }
                }
            }

            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            if (resourceAccess != null) {
                for (Map.Entry<String, Object> entry : resourceAccess.entrySet()) {
                    Object clientRoles = entry.getValue();
                    if (clientRoles instanceof Map<?, ?> clientMap) {
                        Object roles = clientMap.get("roles");
                        if (roles instanceof List<?> roleList) {
                            for (Object role : roleList) {
                                authorities.add(new SimpleGrantedAuthority(role.toString()));
                                log.debug("Added client role from {}: {}", entry.getKey(), role);
                            }
                        }
                    }
                }
            }

            log.debug("Total authorities extracted for {}: {}", jwt.getSubject(), authorities.size());
            return authorities;
        });

        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

}
