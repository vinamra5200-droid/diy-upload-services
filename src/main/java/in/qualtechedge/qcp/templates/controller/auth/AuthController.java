package in.qualtechedge.qcp.templates.controller.auth;

import in.qualtechedge.qcp.templates.dto.request.ClientTokenRequest;
import in.qualtechedge.qcp.templates.dto.request.KeycloakUserLoginRequest;
import in.qualtechedge.qcp.templates.dto.request.TokenRefreshRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.AuthConfigResponse;
import in.qualtechedge.qcp.templates.dto.response.LoginResponse;
import in.qualtechedge.qcp.templates.dto.response.TokenResponse;
import in.qualtechedge.qcp.templates.multitenancy.config.MultiTenancyProperties;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.multitenancy.resolution.HostUtils;
import in.qualtechedge.qcp.templates.properties.KeycloakProperties;
import in.qualtechedge.qcp.templates.service.keycloak.KeycloakAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints for Keycloak-based login, token refresh and logout.
 * <p>
 * These endpoints are listed in {@link in.qualtechedge.qcp.templates.security.auth.SecurityConstants#PUBLIC_API_PATHS}
 * ({@code /api/v1/auth/**}) so no Bearer token is required to call them. They are also excluded
 * from the {@link in.qualtechedge.qcp.templates.multitenancy.resolution.TenantResolutionFilter}
 * ({@code qcp.multitenancy.excluded-paths}) because auth requests may arrive from the
 * {@code admin} subdomain which has no database tenant row.
 * <p>
 * Tenant resolution in this controller:
 * <ol>
 *   <li>Extract subdomain from the {@code Host} header via {@link HostUtils#extractSubdomain}.</li>
 *   <li>If no subdomain is resolvable (localhost, bare domain, or {@code admin-*} superadmin
 *       pattern) fall back to the configured super-admin realm ({@code admin}).</li>
 *   <li>Set {@link HostContext} so that {@link KeycloakAuthService} can read it.</li>
 *   <li>Always clear {@link HostContext} in the {@code finally} block to prevent leaks across
 *       pooled threads.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakProperties keycloakProperties;
    private final MultiTenancyProperties multiTenancy;

    /**
     * What a browser needs to start an OpenID Connect login here.
     * <p>
     * Both answers depend on the host that asked. A tenant signs into its own realm, so telling
     * every caller the console's would send tenant users to a login page where their account does
     * not exist — and the failure reads as a bad password rather than the wrong realm. The client
     * moves with it: the derived id is the one the token validator will require in the audience,
     * so answering with anything else guarantees that a token the console does obtain is refused
     * on arrival.
     * <p>
     * Public by design — see {@link AuthConfigResponse}. These are the values the browser is
     * about to put in a redirect URL.
     */
    @GetMapping("/config")
    public ResponseEntity<APIResponse<AuthConfigResponse>> config(HttpServletRequest httpRequest) {
        String tenant = resolveTenant(httpRequest);

        if (!keycloakProperties.isConfigured()) {
            log.warn("Keycloak is not configured — the console has nowhere to redirect to");
            return ResponseEntity.ok(APIResponse.success(200, "OK",
                    new AuthConfigResponse(null, null, null, null, null, false)));
        }

        String realm = keycloakProperties.realmForTenant(tenant);
        String issuer = keycloakProperties.issuerUrlFor(realm);

        // Re-read the subdomain rather than reuse `tenant` above: resolveTenant() substitutes the
        // super-admin realm name when a host carries no tenant, which is right for picking a realm
        // and wrong here. The audience would come out naming that realm as though it were a tenant
        // — oauth-client-admin-local-app-qc, against a client actually called
        // oauth-client-admin-app-qc — and the console would authenticate as a client that does not
        // exist. null is what "no tenant" has to look like to audienceForTenant.
        String tenantForAudience = HostUtils.extractSubdomain(HostUtils.fromRequest(httpRequest));
        String clientId = keycloakProperties.audienceForTenant(tenantForAudience,
                multiTenancy.host().product());
        if (clientId == null) {
            // No platform tenant: this service runs against a Keycloak of its own, where the
            // console client is named by configuration rather than derived from the host.
            clientId = keycloakProperties.backendClientId(tenant);
        }

        log.info("Auth config request: tenant={}, realm={}, clientId={}", tenant, realm, clientId);

        return ResponseEntity.ok(APIResponse.success(200, "OK", new AuthConfigResponse(
                issuer,
                issuer + "/protocol/openid-connect/auth",
                issuer + "/protocol/openid-connect/token",
                issuer + "/protocol/openid-connect/logout",
                clientId,
                true)));
    }

    /**
     * User login — Keycloak password grant.
     * <p>
     * The caller must supply the backend client ID and (optionally) client secret in the body.
     * The tenant/realm is inferred from the {@code Host} header subdomain.
     */
    @PostMapping("/login")
    public ResponseEntity<APIResponse<LoginResponse>> login(
            @Valid @RequestBody KeycloakUserLoginRequest request,
            HttpServletRequest httpRequest) {

        String tenant = resolveTenant(httpRequest);
        log.info("User login request: tenant={}, user={}", tenant, request.username());

        try {
            HostContext.setCurrentTenant(tenant);
            LoginResponse loginResponse = keycloakAuthService.authenticateUser(request, httpRequest);
            return ResponseEntity.ok(APIResponse.success(200, "Login successful", loginResponse));
        } finally {
            HostContext.clear();
        }
    }

    /**
     * API client authentication — Keycloak client_credentials grant.
     * <p>
     * Used by backend services or external API clients to obtain an access token.
     */
    @PostMapping("/client-token")
    public ResponseEntity<APIResponse<TokenResponse>> clientToken(
            @Valid @RequestBody ClientTokenRequest request,
            HttpServletRequest httpRequest) {

        String tenant = resolveTenant(httpRequest);
        log.info("Client token request: tenant={}, client={}", tenant, request.clientId());

        try {
            HostContext.setCurrentTenant(tenant);
            TokenResponse tokenResponse = keycloakAuthService.authenticateClient(
                    request.clientId(), request.clientSecret(), httpRequest);
            return ResponseEntity.ok(APIResponse.success(200, "Token issued", tokenResponse));
        } finally {
            HostContext.clear();
        }
    }

    /**
     * Refresh access token — Keycloak refresh_token grant.
     */
    @PostMapping("/refresh")
    public ResponseEntity<APIResponse<TokenResponse>> refresh(
            @Valid @RequestBody TokenRefreshRequest request,
            HttpServletRequest httpRequest) {

        String tenant = resolveTenant(httpRequest);
        log.info("Token refresh request: tenant={}", tenant);

        try {
            HostContext.setCurrentTenant(tenant);
            TokenResponse tokenResponse = keycloakAuthService.refreshToken(
                    request.clientId(), request.clientSecret(), request.refreshToken(), httpRequest);
            return ResponseEntity.ok(APIResponse.success(200, "Token refreshed", tokenResponse));
        } finally {
            HostContext.clear();
        }
    }

    /**
     * Logout — Keycloak back-channel refresh token invalidation.
     */
    @PostMapping("/logout")
    public ResponseEntity<APIResponse<Void>> logout(
            @Valid @RequestBody TokenRefreshRequest request,
            HttpServletRequest httpRequest) {

        String tenant = resolveTenant(httpRequest);
        log.info("Logout request: tenant={}", tenant);

        try {
            HostContext.setCurrentTenant(tenant);
            keycloakAuthService.logout(
                    request.clientId(), request.clientSecret(), request.refreshToken(), httpRequest);
            return ResponseEntity.ok(APIResponse.success(200, "Logged out successfully", null));
        } finally {
            HostContext.clear();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolve the tenant short code from the request's {@code Host} header.
     * Falls back to the super-admin realm when no subdomain is resolvable
     * (localhost, bare domain, or the {@code admin-*} superadmin console pattern).
     */
    private String resolveTenant(HttpServletRequest httpRequest) {
        String hostHeader = HostUtils.fromRequest(httpRequest);
        String tenant = HostUtils.extractSubdomain(hostHeader);
        if (tenant == null || tenant.isBlank()) {
            log.debug("No tenant subdomain resolved from host '{}', defaulting to super-admin realm", hostHeader);
            tenant = keycloakProperties.getSuperAdminRealm();
        }
        return tenant;
    }
}
