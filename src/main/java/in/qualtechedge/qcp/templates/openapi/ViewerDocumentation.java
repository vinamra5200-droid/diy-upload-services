package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.ProcessRecordsSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadAttemptResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import in.qualtechedge.qcp.templates.enums.SubmissionStatus;
import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Viewer", description = "Read-only, cross-maker dashboard for the viewer role — every endpoint here "
        + "sees every maker's data, not just the calling actor's own (contrast with /upload/* and /checker/*).")
public interface ViewerDocumentation {

    @Operation(summary = "List attempts across every maker", description = "Newest first. statuses (optional, "
            + "repeatable/comma-separated) filters to a subset of UploadAttemptStatus; processId filters to one "
            + "process; from/to (ISO-8601 date-time) bound createdAt. Any combination is optional.")
    ResponseEntity<APIResponse<PageResponse<UploadAttemptResponse>>> listAttempts(List<UploadAttemptStatus> statuses,
            String processId, OffsetDateTime from, OffsetDateTime to, @Parameter(hidden = true) Pageable pageable);

    @Operation(summary = "List submissions across every maker", description = "Newest first. statuses (optional, "
            + "repeatable/comma-separated) filters to a subset of SubmissionStatus; processId filters to one "
            + "process; from/to (ISO-8601 date-time) bound createdAt. Any combination is optional.")
    ResponseEntity<APIResponse<PageResponse<UploadSubmissionResponse>>> listSubmissions(List<SubmissionStatus> statuses,
            String processId, OffsetDateTime from, OffsetDateTime to, @Parameter(hidden = true) Pageable pageable);

    @Operation(summary = "List jobs across every maker", description = "Newest first. statuses (optional, "
            + "repeatable/comma-separated) filters to a subset of JobStatus; processId filters to one process; "
            + "from/to (ISO-8601 date-time) bound createdAt. Any combination is optional.")
    ResponseEntity<APIResponse<PageResponse<UploadJobResponse>>> listJobs(List<JobStatus> statuses, String processId,
            OffsetDateTime from, OffsetDateTime to, @Parameter(hidden = true) Pageable pageable);

    @Operation(summary = "Rows-processed totals per process", description = "One row per process, summed across "
            + "every COMPLETED/FAILED job for that process (still-QUEUED/PROCESSING jobs don't count yet) — "
            + "backs the dashboard's chart. Not paginated (one row per process).")
    ResponseEntity<APIResponse<List<ProcessRecordsSummaryResponse>>> getProcessSummary();

    @Operation(summary = "Admin: retry a stuck attempt", description = "makerAdmin/checkerAdmin only. Resets the "
            + "attempt to ACCEPTED so validation can be kicked off again. 409 unless current status is TIMED_OUT "
            + "or ABORTED.")
    ResponseEntity<APIResponse<UploadAttemptResponse>> retryAttempt(String attemptId);

    @Operation(summary = "Admin: reject/abort an attempt", description = "makerAdmin/checkerAdmin only. Sets the "
            + "attempt to ABORTED. 409 unless current status is READY_FOR_DECISION or TIMED_OUT.")
    ResponseEntity<APIResponse<UploadAttemptResponse>> rejectAttempt(String attemptId);

    @Operation(summary = "Admin: retry an expired submission", description = "makerAdmin/checkerAdmin only. Resets "
            + "the submission to WAITING_FOR_CHECKER with a fresh SLA window. 409 unless current status is EXPIRED.")
    ResponseEntity<APIResponse<UploadSubmissionResponse>> retrySubmission(String submissionId);

    @Operation(summary = "Admin: reject (expire) a submission", description = "makerAdmin/checkerAdmin only. Sets "
            + "the submission to EXPIRED. 409 unless current status is WAITING_FOR_CHECKER.")
    ResponseEntity<APIResponse<UploadSubmissionResponse>> rejectSubmission(String submissionId);

    @Operation(summary = "Admin: retry a failed job", description = "makerAdmin/checkerAdmin only. Resets the job "
            + "to QUEUED so it can be dispatched again. 409 unless current status is FAILED.")
    ResponseEntity<APIResponse<UploadJobResponse>> retryJob(String jobId);

    @Operation(summary = "Admin: reject (fail) a job", description = "makerAdmin/checkerAdmin only. Sets the job to "
            + "FAILED. 409 unless current status is QUEUED or PROCESSING.")
    ResponseEntity<APIResponse<UploadJobResponse>> rejectJob(String jobId);
}
