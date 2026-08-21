package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import java.time.OffsetDateTime;

public record TemplateSummaryResponse(
        String templateId,
        String templateCode,
        String templateName,
        String version,
        String processId,
        ConfigStatus status,
        int fieldsCount,
        int rulesCount,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
