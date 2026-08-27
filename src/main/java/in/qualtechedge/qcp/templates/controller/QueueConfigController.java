package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.QueueConfigRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.QueueConfigResponse;
import in.qualtechedge.qcp.templates.openapi.QueueConfigDocumentation;
import in.qualtechedge.qcp.templates.service.QueueConfigService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/queues")
@RequiredArgsConstructor
@Slf4j
public class QueueConfigController implements QueueConfigDocumentation {

    private final QueueConfigService queueConfigService;

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<List<QueueConfigResponse>>> list() {
        log.info("List queue configs request");
        List<QueueConfigResponse> response = queueConfigService.getAll();
        log.info("Queue configs retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/{configId}")
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<QueueConfigResponse>> getById(@PathVariable String configId) {
        log.info("Get queue config request: id={}", configId);
        QueueConfigResponse response = queueConfigService.getById(configId);
        log.info("Queue config retrieved: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<QueueConfigResponse>> create(@Valid @RequestBody QueueConfigRequest request) {
        log.info("Create queue config request: name={}", request.queueConfigName());
        QueueConfigResponse response = queueConfigService.create(request);
        log.info("Queue config created: id={}", response.queueConfigId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.success(HttpStatus.CREATED.value(), "Queue config created", response));
    }

    @Override
    @PutMapping("/{configId}")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<QueueConfigResponse>> update(@PathVariable String configId,
                                                                    @Valid @RequestBody QueueConfigRequest request) {
        log.info("Update queue config request: id={}", configId);
        QueueConfigResponse response = queueConfigService.update(configId, request);
        log.info("Queue config updated: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Queue config updated", response));
    }

    @Override
    @PostMapping("/{configId}/submit")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<QueueConfigResponse>> submit(@PathVariable String configId) {
        log.info("Submit queue config request: id={}", configId);
        QueueConfigResponse response = queueConfigService.submit(configId);
        log.info("Queue config submitted: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Queue config submitted", response));
    }

    @Override
    @PostMapping("/{configId}/accept")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<QueueConfigResponse>> accept(@PathVariable String configId) {
        log.info("Accept queue config request: id={}", configId);
        QueueConfigResponse response = queueConfigService.accept(configId);
        log.info("Queue config accepted: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Queue config accepted", response));
    }

    @Override
    @PostMapping("/{configId}/reject")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<QueueConfigResponse>> reject(@PathVariable String configId,
                                                                    @Valid @RequestBody RejectRequest request) {
        log.info("Reject queue config request: id={}", configId);
        QueueConfigResponse response = queueConfigService.reject(configId, request);
        log.info("Queue config rejected: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Queue config rejected", response));
    }
}
