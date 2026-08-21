package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.dto.response.AuditEventResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import java.time.OffsetDateTime;

/**
 * Records audit events (called from every other resource's mutating methods) and serves the
 * Audit Trail read/export API (admin-api-contract.md §9).
 */
public interface AuditEventService {

    /** Admin config-mutation events — every {@code event_code} must exist in {@code audit_event_catalogue}. */
    void record(String eventCode, String actorId, String processId, String templateCode,
                AuditOutcome outcome, String summary);

    /** Upload-pipeline events (Solution Design §12.4) — see {@link PipelineAuditEventRequest}. */
    void record(PipelineAuditEventRequest event);

    PageResponse<AuditEventResponse> list(String processId, String actorId, String eventCode, AuditOutcome outcome,
                                          OffsetDateTime from, OffsetDateTime to, int page, int limit);

    String exportCsv(String processId, String actorId, String eventCode, AuditOutcome outcome,
                      OffsetDateTime from, OffsetDateTime to);
}
