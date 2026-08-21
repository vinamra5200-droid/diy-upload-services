package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.ProcessRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.ProcessResponse;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.openapi.ProcessDocumentation;
import in.qualtechedge.qcp.templates.service.ProcessService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/admin/processes")
@RequiredArgsConstructor
@Slf4j
public class ProcessController implements ProcessDocumentation {

    private final ProcessService processService;

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<PageResponse<ProcessResponse>>> list(
            @RequestParam(required = false) ConfigStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("List processes request: status={}, search={}", status, search);
        PageResponse<ProcessResponse> response = processService.list(status, search, page, limit);
        log.info("Processes retrieved: count={}", response.content().size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/{processId}")
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<ProcessResponse>> getById(@PathVariable String processId) {
        log.info("Get process request: id={}", processId);
        ProcessResponse response = processService.getById(processId);
        log.info("Process retrieved: id={}", processId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<ProcessResponse>> create(@Valid @RequestBody ProcessRequest request) {
        log.info("Create process request: name={}", request.processName());
        ProcessResponse response = processService.create(request);
        log.info("Process created: id={}", response.processId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.success(HttpStatus.CREATED.value(), "Process created", response));
    }

    @Override
    @PutMapping("/{processId}")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<ProcessResponse>> update(@PathVariable String processId,
                                                               @Valid @RequestBody ProcessRequest request) {
        log.info("Update process request: id={}", processId);
        ProcessResponse response = processService.update(processId, request);
        log.info("Process updated: id={}", processId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Process updated", response));
    }

    @Override
    @PostMapping("/{processId}/submit")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<ProcessResponse>> submit(@PathVariable String processId) {
        log.info("Submit process request: id={}", processId);
        ProcessResponse response = processService.submit(processId);
        log.info("Process submitted: id={}", processId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Process submitted", response));
    }

    @Override
    @PostMapping("/{processId}/accept")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<ProcessResponse>> accept(@PathVariable String processId) {
        log.info("Accept process request: id={}", processId);
        ProcessResponse response = processService.accept(processId);
        log.info("Process accepted: id={}", processId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Process accepted", response));
    }

    @Override
    @PostMapping("/{processId}/reject")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<ProcessResponse>> reject(@PathVariable String processId,
                                                               @Valid @RequestBody RejectRequest request) {
        log.info("Reject process request: id={}", processId);
        ProcessResponse response = processService.reject(processId, request);
        log.info("Process rejected: id={}", processId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Process rejected", response));
    }
}
