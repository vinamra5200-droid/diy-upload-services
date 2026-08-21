package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.ApiConfigAuthType;

public record ApiAuthResponse(
        ApiConfigAuthType type,
        String username,
        String password,
        String token,
        String apiKeyName,
        String apiKeyValue,
        String apiKeyLocation
) {
}
