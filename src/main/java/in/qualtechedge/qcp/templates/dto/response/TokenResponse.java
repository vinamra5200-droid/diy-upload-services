package in.qualtechedge.qcp.templates.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.ZonedDateTime;

/**
 * Response DTO for client_credentials token operations.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(
        String type,
        String accessToken,
        ZonedDateTime accessTokenExpiresAt,
        String refreshToken,
        ZonedDateTime refreshTokenExpiresAt
) {
}
