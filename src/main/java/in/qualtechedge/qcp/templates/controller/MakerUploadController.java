package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadCountsResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadFileResponse;
import in.qualtechedge.qcp.templates.openapi.MakerUploadDocumentation;
import in.qualtechedge.qcp.templates.service.S3UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Raw file upload only — lands the maker's file in S3 (with checksum-based duplicate rejection,
 * background processing and SSE status push) so later work (validation, maker-checker review,
 * post-load delivery) has something to act on. That workflow belongs to upload-api-contract.md,
 * which hasn't been provided yet, so this endpoint deliberately does not write an
 * {@code upload_attempts} row or run any validation.
 * <p>
 * Not role-gated beyond authentication: admin-api-contract.md defines {@code makerAdmin}/
 * {@code checkerAdmin} for the config API, not the upload-operator actor that calls this one —
 * tighten with {@code @PreAuthorize} once that role is defined.
 */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
@Slf4j
public class MakerUploadController implements MakerUploadDocumentation {

    private final S3UploadService s3UploadService;

    @Override
    @PostMapping(path = "/{processId}/{templateId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<APIResponse<UploadFileResponse>> upload(@PathVariable String processId,
            @PathVariable String templateId, @RequestParam("file") MultipartFile file) {
        log.info("Upload file request: processId={}, templateId={}, filename={}", processId, templateId, file.getOriginalFilename());
        UploadFileResponse response = s3UploadService.upload(processId, templateId, file);
        log.info("Upload accepted, processing in background: uploadId={}", response.uploadId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(APIResponse.success(HttpStatus.ACCEPTED.value(), "Upload accepted", response));
    }

    @Override
    @GetMapping("/{uploadId}")
    public ResponseEntity<APIResponse<UploadFileResponse>> getById(@PathVariable String uploadId) {
        log.info("Get upload request: id={}", uploadId);
        UploadFileResponse response = s3UploadService.getById(uploadId);
        log.info("Upload retrieved: id={}", uploadId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    // SseEmitter is returned directly, not wrapped in APIResponse — a stream of events over time
    // doesn't fit a single-JSON-body envelope, same reasoning as AuditController's CSV export.
    @Override
    @GetMapping(path = "/{uploadId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String uploadId) {
        log.info("Subscribe to upload events request: id={}", uploadId);
        return s3UploadService.subscribe(uploadId);
    }

    @Override
    @GetMapping("/processes/{processId}/counts")
    public ResponseEntity<APIResponse<UploadCountsResponse>> counts(@PathVariable String processId,
            @RequestParam(required = false) String templateId) {
        log.info("Upload counts request: processId={}, templateId={}", processId, templateId);
        UploadCountsResponse response = s3UploadService.counts(processId, templateId);
        log.info("Upload counts retrieved: pending={}, inProgress={}, completed={}, failed={}",
                response.pending(), response.inProgress(), response.completed(), response.failed());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }
}
