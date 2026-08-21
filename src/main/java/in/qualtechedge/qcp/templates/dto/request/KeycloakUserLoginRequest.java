package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Request body for user login via Keycloak password grant.
 * <p>
 * Example:
 * <pre>
 * {
 *   "grantType":    "password",
 *   "clientId":     "template-qc-backend",
 *   "clientSecret": "secret",
 *   "username":     "alice",
 *   "password":     "secret123"
 * }
 * </pre>
 */
@Builder
public record KeycloakUserLoginRequest(

        @NotBlank(message = "Grant type is required")
        String grantType,

        @NotBlank(message = "Client ID is required")
        String clientId,

        String clientSecret,

        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password,

        String scope

) {
    public KeycloakUserLoginRequest {
        if (grantType == null) {
            grantType = "password";
        }
        if (scope == null) {
            scope = "openid";
        }
    }
}
