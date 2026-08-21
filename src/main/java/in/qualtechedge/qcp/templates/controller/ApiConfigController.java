package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.ApiConfigRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.ApiConfigResponse;
import in.qualtechedge.qcp.templates.openapi.ApiConfigDocumentation;
import in.qualtechedge.qcp.templates.service.ApiConfigService;
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
@RequestMapping("/api/v1/admin/api-configs")
@RequiredArgsConstructor
@Slf4j
public class ApiConfigController implements ApiConfigDocumentation {

    private final ApiConfigService apiConfigService;

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<List<ApiConfigResponse>>> list() {
        log.info("List API configs request");
        List<ApiConfigResponse> response = apiConfigService.getAll();
        log.info("API configs retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/{configId}")
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<ApiConfigResponse>> getById(@PathVariable String configId) {
        log.info("Get API config request: id={}", configId);
        ApiConfigResponse response = apiConfigService.getById(configId);
        log.info("API config retrieved: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<ApiConfigResponse>> create(@Valid @RequestBody ApiConfigRequest request) {
        log.info("Create API config request: label={}", request.label());
        ApiConfigResponse response = apiConfigService.create(request);
        log.info("API config created: id={}", response.configId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.success(HttpStatus.CREATED.value(), "API config created", response));
    }

    @Override
    @PutMapping("/{configId}")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<ApiConfigResponse>> update(@PathVariable String configId,
                                                                  @Valid @RequestBody ApiConfigRequest request) {
        log.info("Update API config request: id={}", configId);
        ApiConfigResponse response = apiConfigService.update(configId, request);
        log.info("API config updated: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "API config updated", response));
    }

    @Override
    @PostMapping("/{configId}/submit")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<ApiConfigResponse>> submit(@PathVariable String configId) {
        log.info("Submit API config request: id={}", configId);
        ApiConfigResponse response = apiConfigService.submit(configId);
        log.info("API config submitted: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "API config submitted", response));
    }

    @Override
    @PostMapping("/{configId}/accept")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<ApiConfigResponse>> accept(@PathVariable String configId) {
        log.info("Accept API config request: id={}", configId);
        ApiConfigResponse response = apiConfigService.accept(configId);
        log.info("API config accepted: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "API config accepted", response));
    }

    @Override
    @PostMapping("/{configId}/reject")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<ApiConfigResponse>> reject(@PathVariable String configId,
                                                                  @Valid @RequestBody RejectRequest request) {
        log.info("Reject API config request: id={}", configId);
        ApiConfigResponse response = apiConfigService.reject(configId, request);
        log.info("API config rejected: id={}", configId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "API config rejected", response));
    }
}
