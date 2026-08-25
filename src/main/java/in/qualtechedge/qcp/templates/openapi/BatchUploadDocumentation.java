package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.BatchValidationCompletedRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Batch Uploads", description = "Inbound callbacks from diy-validation-service (upload-api-contract.md §2)")
public interface BatchUploadDocumentation {

    @Operation(summary = "Validation-completed callback", description = "Called by diy-validation-service once a "
            + "batch's last chunk is validated. System-scope (no tenant subdomain on this call) — tenantCode in "
            + "the body is trusted the same way it was on the Kafka message this endpoint replaces. Records the "
            + "VALIDATION_COMPLETED audit event, pulls row-wise results, and releases the process's config lock.")
    ResponseEntity<APIResponse<Void>> validationCompleted(UUID batchId, BatchValidationCompletedRequest request);
}
