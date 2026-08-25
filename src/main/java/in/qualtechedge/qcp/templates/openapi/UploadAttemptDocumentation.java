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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Upload Attempts", description = "Maker upload -> validate -> decide flow (upload-api-contract.md §2)")
public interface UploadAttemptDocumentation {

    @Operation(summary = "Upload a file", description = "201 with rawObjectKey set. 409 CONCURRENT_UPLOAD_NOT_ALLOWED "
            + "if the process already has a non-terminal attempt; 422 if the file is missing or its extension isn't "
            + "enabled on the active template.")
    ResponseEntity<APIResponse<UploadAttemptResponse>> create(String processId, String templateId, MultipartFile file);

    @Operation(summary = "Start validation", description = "Fire-and-forget: kicks off (or resumes watching) the "
            + "validation run and returns immediately — 202 with the attempt as ACCEPTED or VALIDATING, or 200 "
            + "READY_FOR_DECISION straight away if the template has validationsEnabled=false. Does not block for "
            + "the Kafka round-trip to validation-service to finish; watch GET /{attemptId}/events (SSE) for the "
            + "result rather than polling.")
    ResponseEntity<APIResponse<UploadAttemptResponse>> validate(String attemptId);

    @Operation(summary = "Get an attempt's status/results", description = "§2.3: a fallback/one-off lookup, not the "
            + "primary way to watch an in-flight validation — prefer GET /{attemptId}/events (SSE) for that.")
    ResponseEntity<APIResponse<UploadAttemptResponse>> get(String attemptId);

    @Operation(summary = "Watch an attempt's status changes", description = "§2.2/§2.2a: Server-Sent Events stream, "
            + "Content-Type text/event-stream. Sends an \"attempt\" event with the current UploadAttempt "
            + "immediately on connect, another \"attempt\" event on every state change while status is still "
            + "ACCEPTED/VALIDATING, then one \"done\" event (server closes the connection right after) once "
            + "status leaves ACCEPTED/VALIDATING — including if it already had by the time this connected. A "
            + "comment heartbeat goes out at least every 15s while otherwise idle. 404 immediately, no stream "
            + "opened, if attemptId doesn't exist. Same auth as every other /upload/* route.")
    SseEmitter events(String attemptId);

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
