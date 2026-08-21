package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import java.time.OffsetDateTime;

public record AuditEventResponse(
        String eventId,
        String eventCode,
        OffsetDateTime occurredAt,
        String actorId,
        String processId,
        String templateCode,
        AuditOutcome outcome,
        String summary
) {
}
