package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.ApiConfigMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ApiConfigRequest(
        @NotBlank(message = "label must not be blank")
        String label,

        @NotNull(message = "method must not be null")
        ApiConfigMethod method,

        @NotBlank(message = "uri must not be blank")
        String uri,

        List<KeyValueRequest> queryParams,
        List<KeyValueRequest> headers,
        String body,

        @NotNull(message = "auth must not be null")
        ApiAuthRequest auth
) {
}
