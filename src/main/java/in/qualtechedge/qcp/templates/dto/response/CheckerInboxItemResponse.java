package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.ChangeEntityType;
import java.time.OffsetDateTime;

public record CheckerInboxItemResponse(
        String changeId,
        ChangeEntityType entityType,
        String entityId,
        String entityLabel,
        String summary,
        String submittedBy,
        OffsetDateTime submittedAt,
        boolean actorNeSubmitter,
        String processId
) {
}
