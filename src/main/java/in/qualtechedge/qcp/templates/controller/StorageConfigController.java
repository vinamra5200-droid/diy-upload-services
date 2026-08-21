package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.StorageConfigRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.StorageConfigResponse;
import in.qualtechedge.qcp.templates.openapi.StorageConfigDocumentation;
import in.qualtechedge.qcp.templates.service.StorageConfigService;
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
@RequestMapping("/api/v1/admin/storage")
@RequiredArgsConstructor
@Slf4j
public class StorageConfigController implements StorageConfigDocumentation {

    private final StorageConfigService storageConfigService;

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<List<StorageConfigResponse>>> list() {
        log.info("List storage connections request");
        List<StorageConfigResponse> response = storageConfigService.getAll();
        log.info("Storage connections retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/{configId}")
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<StorageConfigResponse>> getById(@PathVariable String configId) {
        log.info("Get storage connection request: id={}", configId);
        StorageConfigResponse response = storageConfigService.getById(configId);
        log.info("Storage connection retrieved: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<StorageConfigResponse>> create(@Valid @RequestBody StorageConfigRequest request) {
        log.info("Create storage connection request: label={}", request.connectionLabel());
        StorageConfigResponse response = storageConfigService.create(request);
        log.info("Storage connection created: id={}", response.configId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.success(HttpStatus.CREATED.value(), "Storage connection created", response));
    }

    @Override
    @PutMapping("/{configId}")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<StorageConfigResponse>> update(@PathVariable String configId,
                                                                     @Valid @RequestBody StorageConfigRequest request) {
        log.info("Update storage connection request: id={}", configId);
        StorageConfigResponse response = storageConfigService.update(configId, request);
        log.info("Storage connection updated: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Storage connection updated", response));
    }

    @Override
    @PostMapping("/{configId}/submit")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<StorageConfigResponse>> submit(@PathVariable String configId) {
        log.info("Submit storage connection request: id={}", configId);
        StorageConfigResponse response = storageConfigService.submit(configId);
        log.info("Storage connection submitted: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Storage connection submitted", response));
    }

    @Override
    @PostMapping("/{configId}/accept")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<StorageConfigResponse>> accept(@PathVariable String configId) {
        log.info("Accept storage connection request: id={}", configId);
        StorageConfigResponse response = storageConfigService.accept(configId);
        log.info("Storage connection accepted: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Storage connection accepted", response));
    }

    @Override
    @PostMapping("/{configId}/reject")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<StorageConfigResponse>> reject(@PathVariable String configId,
                                                                     @Valid @RequestBody RejectRequest request) {
        log.info("Reject storage connection request: id={}", configId);
        StorageConfigResponse response = storageConfigService.reject(configId, request);
        log.info("Storage connection rejected: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Storage connection rejected", response));
    }
}
