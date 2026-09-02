package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.ProcessRecordsSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadAttemptResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import in.qualtechedge.qcp.templates.enums.SubmissionStatus;
import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import in.qualtechedge.qcp.templates.openapi.ViewerDocumentation;
import in.qualtechedge.qcp.templates.service.ViewerService;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/viewer")
@RequiredArgsConstructor
@Slf4j
public class ViewerController implements ViewerDocumentation {

    private final ViewerService viewerService;

    @Override
    @GetMapping("/attempts")
    @PreAuthorize("hasAnyRole('viewer','makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<PageResponse<UploadAttemptResponse>>> listAttempts(
            @RequestParam(required = false) List<UploadAttemptStatus> statuses,
            @RequestParam(required = false) String processId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Pageable pageable) {
        log.info("Viewer attempts request: statuses={}, processId={}, from={}, to={}, page={}",
                statuses, processId, from, to, pageable.getPageNumber());
        PageResponse<UploadAttemptResponse> response =
                viewerService.listAttempts(statuses, processId, from, to, pageable);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/submissions")
    @PreAuthorize("hasAnyRole('viewer','makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<PageResponse<UploadSubmissionResponse>>> listSubmissions(
            @RequestParam(required = false) List<SubmissionStatus> statuses,
            @RequestParam(required = false) String processId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Pageable pageable) {
        log.info("Viewer submissions request: statuses={}, processId={}, from={}, to={}, page={}",
                statuses, processId, from, to, pageable.getPageNumber());
        PageResponse<UploadSubmissionResponse> response =
                viewerService.listSubmissions(statuses, processId, from, to, pageable);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/jobs")
    @PreAuthorize("hasAnyRole('viewer','makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<PageResponse<UploadJobResponse>>> listJobs(
            @RequestParam(required = false) List<JobStatus> statuses,
            @RequestParam(required = false) String processId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Pageable pageable) {
        log.info("Viewer jobs request: statuses={}, processId={}, from={}, to={}, page={}",
                statuses, processId, from, to, pageable.getPageNumber());
        PageResponse<UploadJobResponse> response = viewerService.listJobs(statuses, processId, from, to, pageable);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/stats/process-summary")
    @PreAuthorize("hasAnyRole('viewer','makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<List<ProcessRecordsSummaryResponse>>> getProcessSummary() {
        log.info("Viewer process-summary request");
        List<ProcessRecordsSummaryResponse> response = viewerService.getProcessSummary();
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    // --- Admin-only manual overrides — deliberately NOT open to 'viewer', unlike every read
    // endpoint above. A plain viewer stays read-only; only makerAdmin/checkerAdmin can retry or
    // reject-fail a stuck record from this dashboard. ---

    @Override
    @PostMapping("/attempts/{attemptId}/retry")
    @PreAuthorize("hasAnyRole('makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<UploadAttemptResponse>> retryAttempt(@PathVariable String attemptId) {
        log.info("Admin retry attempt request: attemptId={}", attemptId);
        UploadAttemptResponse response = viewerService.retryAttempt(attemptId, CurrentActor.id());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping("/attempts/{attemptId}/reject")
    @PreAuthorize("hasAnyRole('makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<UploadAttemptResponse>> rejectAttempt(@PathVariable String attemptId) {
        log.info("Admin reject attempt request: attemptId={}", attemptId);
        UploadAttemptResponse response = viewerService.rejectAttempt(attemptId, CurrentActor.id());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping("/submissions/{submissionId}/retry")
    @PreAuthorize("hasAnyRole('makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<UploadSubmissionResponse>> retrySubmission(@PathVariable String submissionId) {
        log.info("Admin retry submission request: submissionId={}", submissionId);
        UploadSubmissionResponse response = viewerService.retrySubmission(submissionId, CurrentActor.id());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping("/submissions/{submissionId}/reject")
    @PreAuthorize("hasAnyRole('makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<UploadSubmissionResponse>> rejectSubmission(@PathVariable String submissionId) {
        log.info("Admin reject (expire) submission request: submissionId={}", submissionId);
        UploadSubmissionResponse response = viewerService.rejectSubmission(submissionId, CurrentActor.id());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping("/jobs/{jobId}/retry")
    @PreAuthorize("hasAnyRole('makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<UploadJobResponse>> retryJob(@PathVariable String jobId) {
        log.info("Admin retry job request: jobId={}", jobId);
        UploadJobResponse response = viewerService.retryJob(jobId, CurrentActor.id());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping("/jobs/{jobId}/reject")
    @PreAuthorize("hasAnyRole('makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<UploadJobResponse>> rejectJob(@PathVariable String jobId) {
        log.info("Admin reject (fail) job request: jobId={}", jobId);
        UploadJobResponse response = viewerService.rejectJob(jobId, CurrentActor.id());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }
}
