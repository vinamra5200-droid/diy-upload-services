package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.CallbackBatchResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobCallbackSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationRowResponse;
import in.qualtechedge.qcp.templates.openapi.UploadSubmissionJobDocumentation;
import in.qualtechedge.qcp.templates.service.UploadJobService;
import in.qualtechedge.qcp.templates.service.UploadSubmissionService;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
@Slf4j
public class UploadSubmissionJobController implements UploadSubmissionJobDocumentation {

    private final UploadSubmissionService uploadSubmissionService;
    private final UploadJobService uploadJobService;

    @Override
    @GetMapping("/submissions")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<List<UploadSubmissionResponse>>> listSubmissions() {
        log.info("My submissions request");
        List<UploadSubmissionResponse> response = uploadSubmissionService.listByMaker(CurrentActor.id());
        log.info("Submissions retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/jobs")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<List<UploadJobResponse>>> listJobs() {
        log.info("My jobs request");
        List<UploadJobResponse> response = uploadJobService.listByMaker(CurrentActor.id());
        log.info("Jobs retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('makerBatchUpload','viewer','makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<UploadJobResponse>> getJob(@PathVariable String jobId) {
        log.info("Get job request: jobId={}", jobId);
        UploadJobResponse response = uploadJobService.getById(jobId, CurrentActor.id());
        log.info("Job retrieved: jobId={}, status={}", jobId, response.status());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/submissions/{submissionId}/download")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<PresignedDownloadResponse>> downloadSubmission(@PathVariable String submissionId) {
        log.info("Download submission file request: submissionId={}", submissionId);
        PresignedDownloadResponse response = uploadSubmissionService.download(submissionId, CurrentActor.id());
        log.info("Submission download URL minted: submissionId={}", submissionId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/jobs/{jobId}/download")
    @PreAuthorize("hasAnyRole('makerBatchUpload','viewer','makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<PresignedDownloadResponse>> downloadJob(@PathVariable String jobId) {
        log.info("Download job file request: jobId={}", jobId);
        PresignedDownloadResponse response = uploadJobService.download(jobId, CurrentActor.id());
        log.info("Job download URL minted: jobId={}", jobId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping("/jobs/{jobId}/dispatch")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<UploadJobResponse>> dispatchJob(@PathVariable String jobId) {
        log.info("Dispatch job request: jobId={}", jobId);
        UploadJobResponse response = uploadJobService.dispatch(jobId, CurrentActor.id());
        log.info("Job dispatch started: jobId={}", jobId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/jobs/{jobId}/callback-summary")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<UploadJobCallbackSummaryResponse>> getJobCallbackSummary(@PathVariable String jobId) {
        log.info("Job callback summary request: jobId={}", jobId);
        UploadJobCallbackSummaryResponse response = uploadJobService.getCallbackSummary(jobId, CurrentActor.id());
        log.info("Job callback summary retrieved: jobId={}", jobId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/jobs/{jobId}/callback-batches")
    @PreAuthorize("hasAnyRole('makerBatchUpload','viewer','makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<PageResponse<CallbackBatchResponse>>> getJobCallbackBatches(
            @PathVariable String jobId, @RequestParam(required = false) String outcome, Pageable pageable) {
        log.info("Job callback batches request: jobId={}, outcome={}, page={}", jobId, outcome, pageable.getPageNumber());
        PageResponse<CallbackBatchResponse> response =
                uploadJobService.getCallbackBatches(jobId, CurrentActor.id(), outcome, pageable);
        log.info("Job callback batches retrieved: jobId={}, count={}", jobId, response.content().size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/jobs/{jobId}/rows")
    @PreAuthorize("hasAnyRole('makerBatchUpload','viewer','makerAdmin','checkerAdmin')")
    public ResponseEntity<APIResponse<PageResponse<ValidationRowResponse>>> getJobRows(@PathVariable String jobId,
            @RequestParam(required = false) String rowStatus,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        log.info("Get job rows request: jobId={}, page={}, rowStatus={}, search={}",
                jobId, pageable.getPageNumber(), rowStatus, search);
        PageResponse<ValidationRowResponse> response =
                uploadJobService.getJobRows(jobId, CurrentActor.id(), rowStatus, search, pageable);
        log.info("Job rows retrieved: jobId={}", jobId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }
}
