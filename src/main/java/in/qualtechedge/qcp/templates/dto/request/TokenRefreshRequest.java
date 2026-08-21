package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for access token refresh (refresh_token grant).
 */
public record TokenRefreshRequest(

        @NotBlank(message = "Client ID is required")
        String clientId,

        String clientSecret,

        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}
