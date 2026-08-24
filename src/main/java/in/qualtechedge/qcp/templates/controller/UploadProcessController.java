package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.BlankTemplateFileResponse;
import in.qualtechedge.qcp.templates.dto.response.ProcessResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateResponse;
import in.qualtechedge.qcp.templates.openapi.UploadProcessDocumentation;
import in.qualtechedge.qcp.templates.service.UploadCatalogService;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
@Slf4j
public class UploadProcessController implements UploadProcessDocumentation {

    private final UploadCatalogService uploadCatalogService;

    @Override
    @GetMapping("/processes")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<List<ProcessResponse>>> listProcesses() {
        log.info("List permitted processes request");
        List<ProcessResponse> response = uploadCatalogService.listPermittedProcesses(CurrentActor.id());
        log.info("Permitted processes retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/processes/{processId}/active-template")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<APIResponse<TemplateResponse>> getActiveTemplate(@PathVariable String processId) {
        log.info("Get active template request: processId={}", processId);
        TemplateResponse response = uploadCatalogService.getActiveTemplate(processId);
        log.info("Active template retrieved: processId={}, templateId={}", processId, response.templateId());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/processes/{processId}/blank-template")
    @PreAuthorize("hasRole('makerBatchUpload')")
    public ResponseEntity<byte[]> downloadBlankTemplate(@PathVariable String processId,
            @RequestParam String format) {
        log.info("Download blank template request: processId={}, format={}", processId, format);
        BlankTemplateFileResponse file = uploadCatalogService.downloadBlankTemplate(processId, format);
        log.info("Blank template streamed: processId={}, filename={}", processId, file.filename());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, file.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }
}
