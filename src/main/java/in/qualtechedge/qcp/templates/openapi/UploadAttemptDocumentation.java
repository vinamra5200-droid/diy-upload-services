package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.ProceedResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadAttemptResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Upload Attempts", description = "Maker upload -> validate -> decide flow (upload-api-contract.md §2)")
public interface UploadAttemptDocumentation {

    @Operation(summary = "Upload a file", description = "201 with rawObjectKey set. 409 CONCURRENT_UPLOAD_NOT_ALLOWED "
            + "if the process already has a non-terminal attempt; 422 if the file is missing or its extension isn't "
            + "enabled on the active template.")
    ResponseEntity<APIResponse<UploadAttemptResponse>> create(String processId, String templateId, MultipartFile file);

    @Operation(summary = "Start validation", description = "Kicks off (or resumes watching) the validation run. "
            + "202 while the Kafka round-trip to validation-service is in flight — poll GET /{attemptId} for "
            + "READY_FOR_DECISION. Skips straight to READY_FOR_DECISION if the template has validationsEnabled=false.")
    ResponseEntity<APIResponse<UploadAttemptResponse>> validate(String attemptId);

    @Operation(summary = "Get an attempt's status/results")
    ResponseEntity<APIResponse<UploadAttemptResponse>> get(String attemptId);

    @Operation(summary = "Decide: proceed", description = "Requires READY_FOR_DECISION. Creates an UploadSubmission "
            + "if the template has maker-checker enabled, else an UploadJob directly.")
    ResponseEntity<APIResponse<ProceedResponse>> proceed(String attemptId);

    @Operation(summary = "Decide: reupload", description = "Requires READY_FOR_DECISION. Frees the process for a new upload.")
    ResponseEntity<APIResponse<UploadAttemptResponse>> reupload(String attemptId);

    @Operation(summary = "My attempt history", description = "Newest first, for the current actor.")
    ResponseEntity<APIResponse<List<UploadAttemptResponse>>> listMine();

    @Operation(summary = "Download an attempt's file", description = "Presigned URL for the raw or validated stage. "
            + "404 if that stage's key is still null.")
    ResponseEntity<APIResponse<PresignedDownloadResponse>> download(String attemptId, String stage);
}
