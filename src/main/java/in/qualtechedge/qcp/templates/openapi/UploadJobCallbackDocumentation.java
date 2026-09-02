package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.CallbackCompletedRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Upload Job Callbacks", description = "Inbound completion callback from consumer-callback-service")
public interface UploadJobCallbackDocumentation {

    @Operation(summary = "Callback-delivery-completed callback", description = "Called by "
            + "consumer-callback-service once it has attempted the outbound API call for every batch "
            + "(Kafka chunk) of a job. System-scope (no tenant subdomain on this call) — tenantCode in "
            + "the body is trusted the same way diy-validation-service's validation-completed callback "
            + "is. Records the JOB_CALLBACK_COMPLETED audit event and moves the job out of PROCESSING "
            + "into COMPLETED or FAILED.")
    ResponseEntity<APIResponse<Void>> callbackCompleted(String jobId, CallbackCompletedRequest request);
}
