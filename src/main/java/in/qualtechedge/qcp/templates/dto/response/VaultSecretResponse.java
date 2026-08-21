package in.qualtechedge.qcp.templates.dto.response;

import lombok.Builder;

import java.util.Map;

@Builder
public record VaultSecretResponse(
        String tenantCode,
        Map<String, Object> secrets
) {
}
