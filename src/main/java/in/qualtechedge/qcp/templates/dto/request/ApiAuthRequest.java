package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.ApiConfigAuthType;
import jakarta.validation.constraints.NotNull;

/**
 * {@code apiKeyLocation} stays a plain string ({@code header}|{@code query}) rather than a Java
 * enum — it lives only inside the {@code auth} JSON blob, with no backing DB column to enforce it.
 */
public record ApiAuthRequest(
        @NotNull(message = "auth.type must not be null")
        ApiConfigAuthType type,
        String username,
        String password,
        String token,
        String apiKeyName,
        String apiKeyValue,
        String apiKeyLocation
) {
}
