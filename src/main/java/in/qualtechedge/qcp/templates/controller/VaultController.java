package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.VaultSecretRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.VaultSecretResponse;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.service.vault.VaultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/vault")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_TENANT_ADMIN')")
@Slf4j
public class VaultController {

    private final VaultService vaultService;

    @GetMapping("/secrets")
    public ResponseEntity<APIResponse<VaultSecretResponse>> getTenantSecret() {

        String tenantCode = HostContext.getCurrentTenant();
        log.info("Received request to get secrets for tenant: {}", tenantCode);

        try {
            Map<String, Object> secrets = vaultService.getTenantSecret(tenantCode);

            VaultSecretResponse response = VaultSecretResponse.builder()
                    .tenantCode(tenantCode)
                    .secrets(secrets)
                    .build();

            log.info("Successfully retrieved secrets for tenant: {}", tenantCode);
            return ResponseEntity.ok(APIResponse.success(
                    HttpStatus.OK.value(),
                    String.format("Tenant secrets retrieved successfully for: %s", tenantCode),
                    response));

        } catch (IllegalArgumentException e) {
            log.error("Invalid request parameters: {}", e.getMessage());
            return ResponseEntity.badRequest().body(APIResponse.error(
                    HttpStatus.BAD_REQUEST.value(),
                    e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to retrieve tenant secrets for {}: {}", tenantCode, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(APIResponse.error(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    String.format("Failed to retrieve tenant secrets: %s", e.getMessage())));
        }
    }

    @PostMapping("/secrets")
    public ResponseEntity<APIResponse<VaultSecretResponse>> createOrUpdateTenantSecret(
            @Valid @RequestBody VaultSecretRequest request) {

        String tenantCode = HostContext.getCurrentTenant();
        log.info("Received request to create/update secrets for tenant: {}", tenantCode);

        try {
            Map<String, Object> result = vaultService.createOrUpdateTenantSecret(tenantCode, request.getSecrets());

            VaultSecretResponse response = VaultSecretResponse.builder()
                    .tenantCode(tenantCode)
                    .secrets(request.getSecrets())
                    .build();

            log.info("Successfully created/updated secrets for tenant: {}", tenantCode);
            return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.success(
                    HttpStatus.CREATED.value(),
                    String.format("Tenant secrets created/updated successfully for: %s", tenantCode),
                    response));

        } catch (IllegalArgumentException e) {
            log.error("Invalid request parameters: {}", e.getMessage());
            return ResponseEntity.badRequest().body(APIResponse.error(
                    HttpStatus.BAD_REQUEST.value(),
                    e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create/update tenant secrets for {}: {}", tenantCode, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(APIResponse.error(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    String.format("Failed to create/update tenant secrets: %s", e.getMessage())));
        }
    }

    @PutMapping("/secrets")
    public ResponseEntity<APIResponse<VaultSecretResponse>> updateTenantSecret(
            @Valid @RequestBody VaultSecretRequest request) {

        String tenantCode = HostContext.getCurrentTenant();
        log.info("Received request to update secrets for tenant: {}", tenantCode);

        try {
            Map<String, Object> result = vaultService.updateTenantSecret(tenantCode, request.getSecrets());

            VaultSecretResponse response = VaultSecretResponse.builder()
                    .tenantCode(tenantCode)
                    .secrets(request.getSecrets())
                    .build();

            log.info("Successfully updated secrets for tenant: {}", tenantCode);
            return ResponseEntity.ok(APIResponse.success(
                    HttpStatus.OK.value(),
                    String.format("Tenant secrets updated successfully for: %s", tenantCode),
                    response));

        } catch (IllegalArgumentException e) {
            log.error("Invalid request parameters: {}", e.getMessage());
            return ResponseEntity.badRequest().body(APIResponse.error(
                    HttpStatus.BAD_REQUEST.value(),
                    e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update tenant secrets for {}: {}", tenantCode, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(APIResponse.error(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    String.format("Failed to update tenant secrets: %s", e.getMessage())));
        }
    }
}
