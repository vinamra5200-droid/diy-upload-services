package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.AcceptSubmissionResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Checker", description = "Checker (upload-operator) review of maker submissions (upload-api-contract.md §4)")
public interface CheckerDocumentation {

    @Operation(summary = "Checker inbox", description = "Every WAITING_FOR_CHECKER submission, excluding the current actor's own (four-eyes).")
    ResponseEntity<APIResponse<List<UploadSubmissionResponse>>> inbox();

    @Operation(summary = "Get a submission's detail")
    ResponseEntity<APIResponse<UploadSubmissionResponse>> get(String submissionId);

    @Operation(summary = "Download a submission's file", description = "A checker may not download their own submission.")
    ResponseEntity<APIResponse<PresignedDownloadResponse>> download(String submissionId);

    @Operation(summary = "Accept a submission", description = "Creates an UploadJob and promotes the file to pending_processing.")
    ResponseEntity<APIResponse<AcceptSubmissionResponse>> accept(String submissionId);

    @Operation(summary = "Reject a submission")
    ResponseEntity<APIResponse<UploadSubmissionResponse>> reject(String submissionId, RejectRequest request);
}
