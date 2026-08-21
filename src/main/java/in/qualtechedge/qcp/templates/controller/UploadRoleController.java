package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.UploadRoleRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadRoleResponse;
import in.qualtechedge.qcp.templates.openapi.UploadRoleDocumentation;
import in.qualtechedge.qcp.templates.service.UploadRoleService;
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
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@Slf4j
public class UploadRoleController implements UploadRoleDocumentation {

    private final UploadRoleService uploadRoleService;

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<List<UploadRoleResponse>>> list() {
        log.info("List upload roles request");
        List<UploadRoleResponse> response = uploadRoleService.getAll();
        log.info("Upload roles retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/{roleId}")
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<UploadRoleResponse>> getById(@PathVariable String roleId) {
        log.info("Get upload role request: id={}", roleId);
        UploadRoleResponse response = uploadRoleService.getById(roleId);
        log.info("Upload role retrieved: id={}", roleId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<UploadRoleResponse>> create(@Valid @RequestBody UploadRoleRequest request) {
        log.info("Create upload role request: name={}", request.roleName());
        UploadRoleResponse response = uploadRoleService.create(request);
        log.info("Upload role created: id={}", response.roleId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.success(HttpStatus.CREATED.value(), "Upload role created", response));
    }

    @Override
    @PutMapping("/{roleId}")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<UploadRoleResponse>> update(@PathVariable String roleId,
                                                                   @Valid @RequestBody UploadRoleRequest request) {
        log.info("Update upload role request: id={}", roleId);
        UploadRoleResponse response = uploadRoleService.update(roleId, request);
        log.info("Upload role updated: id={}", roleId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Upload role updated", response));
    }

    @Override
    @PostMapping("/{roleId}/submit")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<UploadRoleResponse>> submit(@PathVariable String roleId) {
        log.info("Submit upload role request: id={}", roleId);
        UploadRoleResponse response = uploadRoleService.submit(roleId);
        log.info("Upload role submitted: id={}", roleId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Upload role submitted", response));
    }

    @Override
    @PostMapping("/{roleId}/accept")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<UploadRoleResponse>> accept(@PathVariable String roleId) {
        log.info("Accept upload role request: id={}", roleId);
        UploadRoleResponse response = uploadRoleService.accept(roleId);
        log.info("Upload role accepted: id={}", roleId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Upload role accepted", response));
    }

    @Override
    @PostMapping("/{roleId}/reject")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<UploadRoleResponse>> reject(@PathVariable String roleId,
                                                                   @Valid @RequestBody RejectRequest request) {
        log.info("Reject upload role request: id={}", roleId);
        UploadRoleResponse response = uploadRoleService.reject(roleId, request);
        log.info("Upload role rejected: id={}", roleId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Upload role rejected", response));
    }
}
