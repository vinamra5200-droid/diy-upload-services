package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import java.util.List;
import java.util.Map;

/**
 * Parameters for {@link in.qualtechedge.qcp.templates.service.AuditEventService#record(PipelineAuditEventRequest)}
 * — the upload-pipeline audit event shape from Solution Design §12.4, as opposed to the simpler
 * admin-config-mutation overload. Every field but {@code eventCode}/{@code actorId}/{@code outcome}/
 * {@code summary} is optional: most pipeline stages don't have a submission or job yet when they
 * fire, and {@code traceId} is only meaningful once callers start threading one correlation id
 * through a whole maker action (nothing does yet — see {@link AuditEventCode}'s class doc for which
 * codes are actually emitted today).
 */
public record PipelineAuditEventRequest(
        AuditEventCode eventCode,
        String actorId,
        List<String> actorRoles,
        String processId,
        String templateCode,
        String templateVersion,
        String traceId,
        String uploadAttemptId,
        String submissionId,
        String jobId,
        AuditOutcome outcome,
        String summary,
        Map<String, Object> payload
) {
}
