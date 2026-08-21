package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.ApiConfigMethod;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record ApiConfigResponse(
        String configId,
        String label,
        ApiConfigMethod method,
        String uri,
        List<KeyValueResponse> queryParams,
        List<KeyValueResponse> headers,
        String body,
        ApiAuthResponse auth,
        ConfigStatus status,
        String submittedBy,
        String rejectionReason,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
