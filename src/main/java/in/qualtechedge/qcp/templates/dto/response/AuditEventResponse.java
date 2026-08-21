package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AuditEventResponse(
        String eventId,
        String eventCode,
        OffsetDateTime occurredAt,
        String actorId,
        List<String> actorRoles,
        String processId,
        String templateCode,
        String templateVersion,
        AuditOutcome outcome,
        String summary,
        String traceId,
        String uploadAttemptId,
        String submissionId,
        String jobId,
        Map<String, Object> payload,
        String prevEventId
) {
}
