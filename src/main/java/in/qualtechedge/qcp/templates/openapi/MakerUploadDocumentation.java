package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadCountsResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadFileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Maker Uploads", description = "Raw file upload to the interim object store, ahead of validation")
public interface MakerUploadDocumentation {

    @Operation(summary = "Upload a raw file",
            description = "Streams the file to a local temp file while computing its SHA-256 checksum, rejects a "
                    + "repeat upload of the same file for the same template (409), then hands the S3 PUT off to a "
                    + "background worker and returns immediately with status=pending. Follow progress via "
                    + "GET /{uploadId}/events (SSE), not by polling. Validation, maker-checker review and delivery "
                    + "are not performed here — see upload-api-contract.md (not yet implemented).")
    ResponseEntity<APIResponse<UploadFileResponse>> upload(String processId, String templateId, MultipartFile file);

    @Operation(summary = "Get an upload by id")
    ResponseEntity<APIResponse<UploadFileResponse>> getById(String uploadId);

    @Operation(summary = "Subscribe to an upload's status changes",
            description = "Server-Sent Events stream; sends the current status immediately, then pending -> "
                    + "inProgress -> completed/failed as they happen, closing the stream on a terminal status.")
    SseEmitter events(String uploadId);

    @Operation(summary = "Count a process's uploads by status",
            description = "Dashboard feed: completed/failed are all-time totals for the process (\"till date\" / "
                    + "\"in the past\"), pending/inProgress are current state. Optional templateId narrows to one "
                    + "template within the process.")
    ResponseEntity<APIResponse<UploadCountsResponse>> counts(String processId, String templateId);
}
