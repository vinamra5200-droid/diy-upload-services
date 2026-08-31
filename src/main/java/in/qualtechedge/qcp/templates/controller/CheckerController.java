package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.AcceptSubmissionResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationRowResponse;
import in.qualtechedge.qcp.templates.openapi.CheckerDocumentation;
import in.qualtechedge.qcp.templates.service.CheckerService;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checker")
@RequiredArgsConstructor
@Slf4j
public class CheckerController implements CheckerDocumentation {

    private final CheckerService checkerService;

    @Override
    @GetMapping("/inbox")
    @PreAuthorize("hasRole('checkerBatchUpload')")
    public ResponseEntity<APIResponse<List<UploadSubmissionResponse>>> inbox() {
        log.info("Checker inbox request");
        List<UploadSubmissionResponse> response = checkerService.inbox(CurrentActor.id());
        log.info("Checker inbox retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/submissions/{submissionId}")
    @PreAuthorize("hasRole('checkerBatchUpload')")
    public ResponseEntity<APIResponse<UploadSubmissionResponse>> get(@PathVariable String submissionId) {
        log.info("Get submission detail request: submissionId={}", submissionId);
        UploadSubmissionResponse response = checkerService.get(submissionId);
        log.info("Submission detail retrieved: submissionId={}", submissionId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/submissions/{submissionId}/rows")
    @PreAuthorize("hasRole('checkerBatchUpload')")
    public ResponseEntity<APIResponse<PageResponse<ValidationRowResponse>>> getRows(
            @PathVariable String submissionId,
            @RequestParam(required = false) String rowStatus,
            @RequestParam(required = false) List<String> ruleTypes,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        log.info("Get submission rows request: submissionId={}, page={}, rowStatus={}, ruleTypes={}, search={}",
                submissionId, pageable.getPageNumber(), rowStatus, ruleTypes, search);
        PageResponse<ValidationRowResponse> response =
                checkerService.getRows(submissionId, rowStatus, ruleTypes, search, pageable);
        log.info("Submission rows retrieved: submissionId={}", submissionId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/submissions/{submissionId}/download")
    @PreAuthorize("hasRole('checkerBatchUpload')")
    public ResponseEntity<APIResponse<PresignedDownloadResponse>> download(@PathVariable String submissionId) {
        log.info("Download submission file request (checker): submissionId={}", submissionId);
        PresignedDownloadResponse response = checkerService.download(submissionId, CurrentActor.id());
        log.info("Submission download URL minted (checker): submissionId={}", submissionId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping("/submissions/{submissionId}/accept")
    @PreAuthorize("hasRole('checkerBatchUpload')")
    public ResponseEntity<APIResponse<AcceptSubmissionResponse>> accept(@PathVariable String submissionId) {
        log.info("Accept submission request: submissionId={}", submissionId);
        AcceptSubmissionResponse response = checkerService.accept(submissionId, CurrentActor.id());
        log.info("Submission accepted: submissionId={}", submissionId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Accepted", response));
    }

    @Override
    @PostMapping("/submissions/{submissionId}/reject")
    @PreAuthorize("hasRole('checkerBatchUpload')")
    public ResponseEntity<APIResponse<UploadSubmissionResponse>> reject(@PathVariable String submissionId,
            @Valid @RequestBody RejectRequest request) {
        log.info("Reject submission request: submissionId={}", submissionId);
        UploadSubmissionResponse response = checkerService.reject(submissionId, CurrentActor.id(), request);
        log.info("Submission rejected: submissionId={}", submissionId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Rejected", response));
    }
}
