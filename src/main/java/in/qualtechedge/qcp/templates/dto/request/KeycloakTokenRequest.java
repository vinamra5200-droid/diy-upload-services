package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeycloakTokenRequest {

    @NotBlank(message = "Grant type is required")
    @Builder.Default
    private String grantType = "password";

    @NotBlank(message = "Client ID is required")
    private String clientId;

    private String clientSecret;

    private String username;

    private String password;

    private String scope;

    public boolean isPasswordGrant() {
        return "password".equalsIgnoreCase(grantType);
    }

    public boolean isClientCredentialsGrant() {
        return "client_credentials".equalsIgnoreCase(grantType);
    }
}
