package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.CheckerInboxItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Checker Inbox", description = "Pending Maker submissions across every governed entity (admin-api-contract.md §8)")
public interface InboxDocumentation {

    @Operation(summary = "List pending reviews")
    ResponseEntity<APIResponse<List<CheckerInboxItemResponse>>> list();

    @Operation(summary = "Accept an inbox item", description = "Routes to the matching entity's accept endpoint internally.")
    ResponseEntity<APIResponse<Object>> accept(String changeId);

    @Operation(summary = "Reject an inbox item", description = "Routes to the matching entity's reject endpoint internally.")
    ResponseEntity<APIResponse<Object>> reject(String changeId, RejectRequest request);
}
