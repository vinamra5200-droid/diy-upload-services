package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for API client authentication using the client_credentials grant.
 */
public record ClientTokenRequest(

        @NotBlank(message = "Client ID is required")
        String clientId,

        @NotBlank(message = "Client secret is required")
        String clientSecret
) {
}
