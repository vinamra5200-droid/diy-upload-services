package in.qualtechedge.qcp.templates.service.keycloak;

import in.qualtechedge.qcp.templates.dto.request.KeycloakTokenRequest;
import in.qualtechedge.qcp.templates.dto.request.KeycloakUserLoginRequest;
import in.qualtechedge.qcp.templates.dto.response.KeycloakTokenResponse;
import in.qualtechedge.qcp.templates.dto.response.LoginResponse;
import in.qualtechedge.qcp.templates.dto.response.TokenResponse;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.properties.KeycloakProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;

/**
 * Orchestration service for Keycloak-based authentication.
 * <p>
 * Delegates low-level HTTP calls to {@link KeycloakTokenService} and converts
 * the Keycloak response into the application's response types ({@link LoginResponse},
 * {@link TokenResponse}). The tenant/realm is resolved from {@link HostContext}, which
 * is populated by the {@link in.qualtechedge.qcp.templates.multitenancy.resolution.TenantResolutionFilter}
 * or directly from the {@code Host} header for auth endpoints.
 * <p>
 * Tenant fallback: if no tenant is in context, defaults to {@code admin} realm
 * (matches the super-admin Keycloak realm defined in {@link KeycloakProperties#getSuperAdminRealm()}).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakAuthService {

    private final KeycloakTokenService keycloakTokenService;
    private final KeycloakProperties keycloakProperties;

    /**
     * Authenticate a user via Keycloak password grant.
     *
     * @param loginRequest contains grant_type, client_id, client_secret, username, password
     * @param request      HTTP servlet request (unused; kept for parity with KYC pattern)
     * @return {@link LoginResponse} with Keycloak JWT tokens
     */
    public LoginResponse authenticateUser(KeycloakUserLoginRequest loginRequest,
                                          HttpServletRequest request) {
        String tenant = resolveTenant();
        log.info("Authenticating user via Keycloak: tenant={}, user={}", tenant, loginRequest.username());

        KeycloakTokenRequest tokenRequest = KeycloakTokenRequest.builder()
                .grantType(loginRequest.grantType())
                .clientId(loginRequest.clientId())
                .clientSecret(loginRequest.clientSecret())
                .username(loginRequest.username())
                .password(loginRequest.password())
                .scope(loginRequest.scope())
                .build();

        KeycloakTokenResponse keycloakResponse = keycloakTokenService.getToken(tenant, tokenRequest);
        return toLoginResponse(keycloakResponse, loginRequest.username());
    }

    /**
     * Authenticate an API client via Keycloak client_credentials grant.
     *
     * @param clientId     OAuth2 client ID
     * @param clientSecret OAuth2 client secret
     * @param request      HTTP servlet request (unused; kept for parity with KYC pattern)
     * @return {@link TokenResponse} with Keycloak JWT tokens
     */
    public TokenResponse authenticateClient(String clientId, String clientSecret,
                                            HttpServletRequest request) {
        String tenant = resolveTenant();
        log.info("Authenticating API client via Keycloak: tenant={}, client={}", tenant, clientId);

        KeycloakTokenResponse keycloakResponse =
                keycloakTokenService.getClientCredentialsToken(tenant, clientId, clientSecret);
        return toTokenResponse(keycloakResponse);
    }

    /**
     * Refresh an access token via Keycloak.
     *
     * @param clientId     OAuth2 client ID
     * @param clientSecret OAuth2 client secret (optional)
     * @param refreshToken current refresh token
     * @param request      HTTP servlet request (unused; kept for parity with KYC pattern)
     * @return {@link TokenResponse} with new Keycloak JWT tokens
     */
    public TokenResponse refreshToken(String clientId, String clientSecret,
                                      String refreshToken, HttpServletRequest request) {
        String tenant = resolveTenant();
        log.info("Refreshing token via Keycloak: tenant={}", tenant);

        KeycloakTokenResponse keycloakResponse =
                keycloakTokenService.refreshToken(tenant, clientId, clientSecret, refreshToken);
        return toTokenResponse(keycloakResponse);
    }

    /**
     * Logout via Keycloak back-channel (invalidates the refresh token).
     *
     * @param clientId     OAuth2 client ID
     * @param clientSecret OAuth2 client secret (optional)
     * @param refreshToken refresh token to invalidate
     * @param request      HTTP servlet request (unused; kept for parity with KYC pattern)
     */
    public void logout(String clientId, String clientSecret,
                       String refreshToken, HttpServletRequest request) {
        String tenant = resolveTenant();
        log.info("Logging out via Keycloak: tenant={}", tenant);
        keycloakTokenService.logout(tenant, clientId, clientSecret, refreshToken);
    }

    /**
     * Returns the configured Keycloak server URL.
     */
    public String getKeycloakServerUrl() {
        return keycloakProperties.getServerUrl();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolve the active tenant from {@link HostContext}.
     * Falls back to the super-admin realm ({@code admin}) when no tenant is in context —
     * this covers direct admin logins that arrive without a tenant subdomain.
     */
    private String resolveTenant() {
        String tenant = HostContext.getCurrentTenant();

        if (tenant == null || tenant.isEmpty()) {
            log.info("No tenant in context — defaulting to super-admin realm");
            tenant = keycloakProperties.getSuperAdminRealm();
            HostContext.setCurrentTenant(tenant);
        }

        log.debug("Resolved tenant from context: {}", tenant);
        return tenant;
    }

    private LoginResponse toLoginResponse(KeycloakTokenResponse keycloakResponse, String username) {
        ZonedDateTime accessTokenExpiresAt = calculateExpiryTime(keycloakResponse.getExpiresIn());
        ZonedDateTime refreshTokenExpiresAt = calculateExpiryTime(keycloakResponse.getRefreshExpiresIn());

        return LoginResponse.builder()
                .type(keycloakResponse.getTokenType())
                .accessToken(keycloakResponse.getAccessToken())
                .accessTokenExpiresAt(accessTokenExpiresAt)
                .refreshToken(keycloakResponse.getRefreshToken())
                .refreshTokenExpiresAt(refreshTokenExpiresAt)
                .username(username)
                .roles(new HashSet<>())
                .build();
    }

    private TokenResponse toTokenResponse(KeycloakTokenResponse keycloakResponse) {
        ZonedDateTime accessTokenExpiresAt = calculateExpiryTime(keycloakResponse.getExpiresIn());
        ZonedDateTime refreshTokenExpiresAt = calculateExpiryTime(keycloakResponse.getRefreshExpiresIn());

        return TokenResponse.builder()
                .type(keycloakResponse.getTokenType())
                .accessToken(keycloakResponse.getAccessToken())
                .accessTokenExpiresAt(accessTokenExpiresAt)
                .refreshToken(keycloakResponse.getRefreshToken())
                .refreshTokenExpiresAt(refreshTokenExpiresAt)
                .build();
    }

    private ZonedDateTime calculateExpiryTime(Long expiresInSeconds) {
        if (expiresInSeconds == null) {
            return null;
        }
        return ZonedDateTime.ofInstant(
                Instant.now().plusSeconds(expiresInSeconds), ZoneId.systemDefault());
    }
}
