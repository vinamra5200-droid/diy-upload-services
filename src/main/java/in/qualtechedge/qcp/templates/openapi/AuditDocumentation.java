package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.AuditEventResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;

/**
 * OpenAPI documentation contract (QCP rule: Swagger annotations live here; the controller
 * implements this interface).
 */
@Tag(name = "Audit Trail", description = "Append-only admin activity log (admin-api-contract.md §9)")
public interface AuditDocumentation {

    @Operation(summary = "List audit events", description = "Filtered, paginated admin activity log.")
    ResponseEntity<APIResponse<PageResponse<AuditEventResponse>>> list(String processId, String actorId, String eventCode,
            AuditOutcome outcome, OffsetDateTime from, OffsetDateTime to, int page, int limit);

    @Operation(summary = "Export audit events as CSV", description = "Same filters as list; returns text/csv.")
    ResponseEntity<String> export(String processId, String actorId, String eventCode, AuditOutcome outcome,
            OffsetDateTime from, OffsetDateTime to);
}
