package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.TenantRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.TenantResponse;
import in.qualtechedge.qcp.templates.openapi.TenantAdminDocumentation;
import in.qualtechedge.qcp.templates.service.TenantAdminService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Superadmin tenant administration. Excluded from tenant resolution (system scope) — every
 * operation runs against the system database; tenant business data is never reachable from here
 * (QCC Multi-Tenancy §4: even superadmin cannot query tenant databases).
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
@Slf4j
public class TenantAdminController implements TenantAdminDocumentation {

    private final TenantAdminService tenantAdminService;

    @Override
    @GetMapping
    public ResponseEntity<APIResponse<List<TenantResponse>>> getAll() {
        log.info("List tenants request");
        List<TenantResponse> responses = tenantAdminService.getAll();
        log.info("Tenants retrieved: count={}", responses.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", responses));
    }

    @Override
    @PostMapping
    public ResponseEntity<APIResponse<TenantResponse>> create(@Valid @RequestBody TenantRequest request) {
        log.info("Onboard tenant request: shortCode={}", request.shortCode());
        TenantResponse response = tenantAdminService.create(request);
        log.info("Tenant onboarded: shortCode={} dbUrl={}", response.shortCode(), response.dbUrl());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.success(HttpStatus.CREATED.value(), "Tenant onboarded", response));
    }
}
