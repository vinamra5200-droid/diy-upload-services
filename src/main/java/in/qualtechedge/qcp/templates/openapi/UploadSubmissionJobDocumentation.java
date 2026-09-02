package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.CallbackBatchResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobCallbackSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationRowResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "My Submissions & Jobs", description = "Maker-side tracking (upload-api-contract.md §3)")
public interface UploadSubmissionJobDocumentation {

    @Operation(summary = "My submissions", description = "Every submission the current actor created, across all statuses.")
    ResponseEntity<APIResponse<List<UploadSubmissionResponse>>> listSubmissions();

    @Operation(summary = "My jobs", description = "Every job the current actor's submissions/attempts produced.")
    ResponseEntity<APIResponse<List<UploadJobResponse>>> listJobs();

    @Operation(summary = "Get a job by id", description = "Single job lookup — backs the maker UI's completion "
            + "poll (every ~1.5s from job creation until it leaves QUEUED/PROCESSING).")
    ResponseEntity<APIResponse<UploadJobResponse>> getJob(String jobId);

    @Operation(summary = "Download a submission's pending file")
    ResponseEntity<APIResponse<PresignedDownloadResponse>> downloadSubmission(String submissionId);

    @Operation(summary = "Download a job's completed file")
    ResponseEntity<APIResponse<PresignedDownloadResponse>> downloadJob(String jobId);

    @Operation(summary = "Dispatch a job to Kafka", description = "Starts streaming a QUEUED job's completed file "
            + "to its template's configured Kafka topic. Returns immediately with status=PROCESSING; the "
            + "template's post-load action must be kafka, and a job can only be dispatched once.")
    ResponseEntity<APIResponse<UploadJobResponse>> dispatchJob(String jobId);

    @Operation(summary = "Job callback delivery summary", description = "Aggregate outcome of "
            + "consumer-callback-service's outbound API delivery for this job's batches (total/success/failed "
            + "counts) — 404 until its completion callback has been received.")
    ResponseEntity<APIResponse<UploadJobCallbackSummaryResponse>> getJobCallbackSummary(String jobId);

    @Operation(summary = "Job callback per-batch detail", description = "The drill-down behind the summary "
            + "above — one row per batch (Kafka chunk), pulled live from consumer-callback-service on every "
            + "call (never cached here). outcome filters to SUCCESS/FAILED; omit for every batch.")
    ResponseEntity<APIResponse<PageResponse<CallbackBatchResponse>>> getJobCallbackBatches(String jobId, String outcome, Pageable pageable);

    @Operation(summary = "Browse this job's processed rows", description = "On-demand: synthesizes one row per "
            + "original source record from consumer-callback-service's per-batch delivery outcome — a Kafka "
            + "chunk is posted to the third-party as one atomic HTTP call, so every row within it shares that "
            + "chunk's outcome and API response. rowStatus (PASSED/FAILED) and search (free text against the "
            + "row's API response) are both optional.")
    ResponseEntity<APIResponse<PageResponse<ValidationRowResponse>>> getJobRows(String jobId, String rowStatus,
            String search, Pageable pageable);
}
