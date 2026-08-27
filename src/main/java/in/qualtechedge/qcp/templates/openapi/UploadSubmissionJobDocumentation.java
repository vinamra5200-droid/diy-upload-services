package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "My Submissions & Jobs", description = "Maker-side tracking (upload-api-contract.md §3)")
public interface UploadSubmissionJobDocumentation {

    @Operation(summary = "My submissions", description = "Every submission the current actor created, across all statuses.")
    ResponseEntity<APIResponse<List<UploadSubmissionResponse>>> listSubmissions();

    @Operation(summary = "My jobs", description = "Every job the current actor's submissions/attempts produced.")
    ResponseEntity<APIResponse<List<UploadJobResponse>>> listJobs();

    @Operation(summary = "Download a submission's pending file")
    ResponseEntity<APIResponse<PresignedDownloadResponse>> downloadSubmission(String submissionId);

    @Operation(summary = "Download a job's completed file")
    ResponseEntity<APIResponse<PresignedDownloadResponse>> downloadJob(String jobId);

    @Operation(summary = "Dispatch a job to Kafka", description = "Starts streaming a QUEUED job's completed file "
            + "to its template's configured Kafka topic. Returns immediately with status=PROCESSING; the "
            + "template's post-load action must be kafka, and a job can only be dispatched once.")
    ResponseEntity<APIResponse<UploadJobResponse>> dispatchJob(String jobId);
}
