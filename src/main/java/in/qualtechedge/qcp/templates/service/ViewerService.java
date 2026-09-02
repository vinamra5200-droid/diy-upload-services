package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.ProcessRecordsSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadAttemptResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import in.qualtechedge.qcp.templates.enums.SubmissionStatus;
import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;

/** Read-only, cross-maker listing for the viewer role — deliberately unscoped by makerUserId. */
public interface ViewerService {

    PageResponse<UploadAttemptResponse> listAttempts(List<UploadAttemptStatus> statuses, String processId,
            OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    PageResponse<UploadSubmissionResponse> listSubmissions(List<SubmissionStatus> statuses, String processId,
            OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    PageResponse<UploadJobResponse> listJobs(List<JobStatus> statuses, String processId,
            OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    /** Rows-processed totals per process, across every job — backs the dashboard's chart. */
    List<ProcessRecordsSummaryResponse> getProcessSummary();

    // --- Admin-only manual overrides (makerAdmin/checkerAdmin, gated at the controller — never
    // exposed to a plain viewer). Each resets/fails one stuck record; see ViewerServiceImpl for
    // the exact allowed current-status preconditions per action. ---

    UploadAttemptResponse retryAttempt(String attemptId, String actorId);

    UploadAttemptResponse rejectAttempt(String attemptId, String actorId);

    UploadSubmissionResponse retrySubmission(String submissionId, String actorId);

    UploadSubmissionResponse rejectSubmission(String submissionId, String actorId);

    UploadJobResponse retryJob(String jobId, String actorId);

    UploadJobResponse rejectJob(String jobId, String actorId);
}
