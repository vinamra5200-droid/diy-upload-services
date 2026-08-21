package in.qualtechedge.qcp.templates.service.keycloak;

import in.qualtechedge.qcp.templates.dto.request.KeycloakTokenRequest;
import in.qualtechedge.qcp.templates.dto.response.KeycloakTokenResponse;
import org.springframework.util.MultiValueMap;

/**
 * Low-level Keycloak token operations — each method maps directly to a Keycloak endpoint.
 * The tenant/realm is always passed explicitly so this service is stateless.
 */
public interface KeycloakTokenService {

    /**
     * Obtain an access token using the password grant (user authentication).
     *
     * @param tenant  the Keycloak realm name
     * @param request request containing grant_type, client_id, client_secret, username, password
     * @return token response with access_token, refresh_token, etc.
     */
    KeycloakTokenResponse getToken(String tenant, KeycloakTokenRequest request);

    /**
     * Obtain an access token using the client_credentials grant (service authentication).
     *
     * @param tenant       the Keycloak realm name
     * @param clientId     OAuth2 client ID
     * @param clientSecret OAuth2 client secret
     * @return token response with access_token
     */
    KeycloakTokenResponse getClientCredentialsToken(String tenant, String clientId, String clientSecret);

    /**
     * Refresh an existing access token using a refresh token.
     *
     * @param tenant       the Keycloak realm name
     * @param clientId     OAuth2 client ID
     * @param clientSecret OAuth2 client secret (optional)
     * @param refreshToken current refresh token
     * @return new token response
     */
    KeycloakTokenResponse refreshToken(String tenant, String clientId, String clientSecret, String refreshToken);

    /**
     * Logout by invalidating the refresh token (back-channel logout).
     *
     * @param tenant       the Keycloak realm name
     * @param clientId     OAuth2 client ID
     * @param clientSecret OAuth2 client secret (optional)
     * @param refreshToken refresh token to invalidate
     */
    void logout(String tenant, String clientId, String clientSecret, String refreshToken);

    /**
     * Introspect a token to determine whether it is currently active.
     *
     * @param tenant       the Keycloak realm name
     * @param clientId     OAuth2 client ID
     * @param clientSecret OAuth2 client secret
     * @param token        the token to introspect
     * @return {@code true} if the token is active
     */
    boolean introspectToken(String tenant, String clientId, String clientSecret, String token);

    /**
     * Exchange an authorization code for tokens (Authorization Code Flow).
     *
     * @param tokenUrl token endpoint URL
     * @param formData form data containing grant_type, code, redirect_uri, client_id, client_secret
     * @return token response
     */
    KeycloakTokenResponse exchangeCodeForToken(String tokenUrl, MultiValueMap<String, String> formData);
}
