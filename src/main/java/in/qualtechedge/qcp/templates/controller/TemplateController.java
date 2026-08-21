package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.CloneTemplateRequest;
import in.qualtechedge.qcp.templates.dto.request.CreateTemplateRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.UpdateTemplateRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateVersionSnapshotResponse;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.openapi.TemplateDocumentation;
import in.qualtechedge.qcp.templates.service.TemplateService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class TemplateController implements TemplateDocumentation {

    private final TemplateService templateService;

    @Override
    @GetMapping("/processes/{processId}/templates")
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<List<TemplateSummaryResponse>>> listByProcess(@PathVariable String processId,
            @RequestParam(required = false) ConfigStatus status) {
        log.info("List templates request: processId={}, status={}", processId, status);
        List<TemplateSummaryResponse> response = templateService.listByProcess(processId, status);
        log.info("Templates retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/templates/{templateId}")
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<TemplateResponse>> getById(@PathVariable String templateId) {
        log.info("Get template request: id={}", templateId);
        TemplateResponse response = templateService.getById(templateId);
        log.info("Template retrieved: id={}", templateId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping("/processes/{processId}/templates")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<TemplateResponse>> create(@PathVariable String processId,
                                                                @Valid @RequestBody CreateTemplateRequest request) {
        log.info("Create template request: processId={}, name={}", processId, request.templateName());
        TemplateResponse response = templateService.create(processId, request);
        log.info("Template created: id={}", response.templateId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.success(HttpStatus.CREATED.value(), "Template created", response));
    }

    @Override
    @PutMapping("/templates/{templateId}")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<TemplateResponse>> update(@PathVariable String templateId,
                                                                 @Valid @RequestBody UpdateTemplateRequest request) {
        log.info("Update template request: id={}", templateId);
        TemplateResponse response = templateService.update(templateId, request);
        log.info("Template updated: id={}", templateId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Template updated", response));
    }

    @Override
    @PostMapping("/templates/{templateId}/submit")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<TemplateResponse>> submit(@PathVariable String templateId) {
        log.info("Submit template request: id={}", templateId);
        TemplateResponse response = templateService.submit(templateId);
        log.info("Template submitted: id={}", templateId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Template submitted", response));
    }

    @Override
    @PostMapping("/templates/{templateId}/accept")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<TemplateResponse>> accept(@PathVariable String templateId) {
        log.info("Accept template request: id={}", templateId);
        TemplateResponse response = templateService.accept(templateId);
        log.info("Template accepted: id={}", templateId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Template accepted", response));
    }

    @Override
    @PostMapping("/templates/{templateId}/reject")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<TemplateResponse>> reject(@PathVariable String templateId,
                                                                 @Valid @RequestBody RejectRequest request) {
        log.info("Reject template request: id={}", templateId);
        TemplateResponse response = templateService.reject(templateId, request);
        log.info("Template rejected: id={}", templateId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Template rejected", response));
    }

    @Override
    @PostMapping("/templates/{templateId}/clone")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<TemplateResponse>> clone(@PathVariable String templateId,
                                                                @Valid @RequestBody CloneTemplateRequest request) {
        log.info("Clone template request: id={}, newName={}", templateId, request.newName());
        TemplateResponse response = templateService.clone(templateId, request);
        log.info("Template cloned: sourceId={}, newId={}", templateId, response.templateId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.success(HttpStatus.CREATED.value(), "Template cloned", response));
    }

    @Override
    @GetMapping("/templates/{templateId}/versions")
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<List<TemplateVersionSnapshotResponse>>> listVersions(@PathVariable String templateId) {
        log.info("List template versions request: id={}", templateId);
        List<TemplateVersionSnapshotResponse> response = templateService.listVersions(templateId);
        log.info("Template versions retrieved: id={}, count={}", templateId, response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/templates/{templateId}/versions/{version}")
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<TemplateVersionSnapshotResponse>> getVersion(@PathVariable String templateId,
                                                                                    @PathVariable String version) {
        log.info("Get template version request: id={}, version={}", templateId, version);
        TemplateVersionSnapshotResponse response = templateService.getVersion(templateId, version);
        log.info("Template version retrieved: id={}, version={}", templateId, version);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }
}
