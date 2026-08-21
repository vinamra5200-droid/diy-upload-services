package in.qualtechedge.qcp.templates.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for user login operations.
 * Contains Keycloak JWT tokens and basic user information.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String type,
        String accessToken,
        ZonedDateTime accessTokenExpiresAt,
        String refreshToken,
        ZonedDateTime refreshTokenExpiresAt,
        UUID id,
        String username,
        String email,
        Set<String> roles
) {
}
