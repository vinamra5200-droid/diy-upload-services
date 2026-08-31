package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.ProceedResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadAttemptResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationRowResponse;
import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import in.qualtechedge.qcp.templates.openapi.UploadAttemptDocumentation;
import in.qualtechedge.qcp.templates.service.UploadAttemptService;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/upload/attempts")
@RequiredArgsConstructor
@Slf4j
public class UploadAttemptController implements UploadAttemptDocumentation {

    private final UploadAttemptService uploadAttemptService;

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<UploadAttemptResponse>> create(@RequestParam String processId,
            @RequestParam String templateId, @RequestParam("file") MultipartFile file) {
        log.info("Create upload attempt request: processId={}, templateId={}", processId, templateId);
        UploadAttemptResponse response = uploadAttemptService.create(processId, templateId, CurrentActor.id(), file);
        log.info("Upload attempt created: id={}", response.uploadAttemptId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.success(HttpStatus.CREATED.value(), "Upload attempt accepted", response));
    }

    @Override
    @PostMapping("/{attemptId}/validate")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<UploadAttemptResponse>> validate(@PathVariable String attemptId) {
        log.info("Start validation request: attemptId={}", attemptId);
        UploadAttemptResponse response = uploadAttemptService.startValidation(attemptId, CurrentActor.id());
        log.info("Validation status: attemptId={}, status={}", attemptId, response.status());
        HttpStatus status = response.status() == UploadAttemptStatus.READY_FOR_DECISION ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(APIResponse.success(status.value(), "OK", response));
    }

    @Override
    @GetMapping("/{attemptId}")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<UploadAttemptResponse>> get(@PathVariable String attemptId) {
        log.info("Get upload attempt request: attemptId={}", attemptId);
        UploadAttemptResponse response = uploadAttemptService.get(attemptId);
        log.info("Upload attempt retrieved: attemptId={}", attemptId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    // SseEmitter is returned directly, not wrapped in APIResponse — a stream of events over time
    // doesn't fit a single-JSON-body envelope, same reasoning as MakerUploadController#events.
    @Override
    @GetMapping(path = "/{attemptId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('makerBatchUpload')")
    public SseEmitter events(@PathVariable String attemptId) {
        log.info("Subscribe to upload attempt events request: attemptId={}", attemptId);
        return uploadAttemptService.subscribe(attemptId);
    }

    @Override
    @PostMapping("/{attemptId}/proceed")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<ProceedResponse>> proceed(@PathVariable String attemptId) {
        log.info("Proceed decision request: attemptId={}", attemptId);
        ProceedResponse response = uploadAttemptService.proceed(attemptId, CurrentActor.id());
        log.info("Proceed decision recorded: attemptId={}", attemptId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping("/{attemptId}/reupload")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<UploadAttemptResponse>> reupload(@PathVariable String attemptId) {
        log.info("Reupload decision request: attemptId={}", attemptId);
        UploadAttemptResponse response = uploadAttemptService.reupload(attemptId, CurrentActor.id());
        log.info("Reupload decision recorded: attemptId={}", attemptId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<List<UploadAttemptResponse>>> listMine() {
        log.info("My attempt history request");
        List<UploadAttemptResponse> response = uploadAttemptService.listByMaker(CurrentActor.id());
        log.info("Attempt history retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/{attemptId}/rows")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<PageResponse<ValidationRowResponse>>> getRows(@PathVariable String attemptId,
            @RequestParam(required = false) String rowStatus,
            @RequestParam(required = false) List<String> ruleTypes,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        log.info("Get upload attempt rows request: attemptId={}, page={}, rowStatus={}, ruleTypes={}, search={}",
                attemptId, pageable.getPageNumber(), rowStatus, ruleTypes, search);
        PageResponse<ValidationRowResponse> response =
                uploadAttemptService.getRows(attemptId, rowStatus, ruleTypes, search, pageable);
        log.info("Upload attempt rows retrieved: attemptId={}", attemptId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/{attemptId}/download")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<PresignedDownloadResponse>> download(@PathVariable String attemptId,
            @RequestParam String stage) {
        log.info("Download attempt file request: attemptId={}, stage={}", attemptId, stage);
        PresignedDownloadResponse response = uploadAttemptService.download(attemptId, stage, CurrentActor.id());
        log.info("Attempt download URL minted: attemptId={}, stage={}", attemptId, stage);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }
}
