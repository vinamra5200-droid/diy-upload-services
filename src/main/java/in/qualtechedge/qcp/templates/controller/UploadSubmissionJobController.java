package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import in.qualtechedge.qcp.templates.openapi.UploadSubmissionJobDocumentation;
import in.qualtechedge.qcp.templates.service.UploadJobService;
import in.qualtechedge.qcp.templates.service.UploadSubmissionService;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<PresignedDownloadResponse>> downloadJob(@PathVariable String jobId) {
        log.info("Download job file request: jobId={}", jobId);
        PresignedDownloadResponse response = uploadJobService.download(jobId, CurrentActor.id());
        log.info("Job download URL minted: jobId={}", jobId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }
}
