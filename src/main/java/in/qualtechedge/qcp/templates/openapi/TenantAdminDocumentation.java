package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.TenantRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.TenantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

/**
 * OpenAPI documentation contract for the tenant admin API (QCP rule: Swagger annotations live
 * here; the controller implements this interface).
 */
@Tag(name = "Tenant Administration",
        description = "Superadmin tenant registry operations — system database scope, no tenant context")
public interface TenantAdminDocumentation {

    @Operation(summary = "List all tenants", description = "Returns every tenant in the registry.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tenants returned")
    ResponseEntity<APIResponse<List<TenantResponse>>> getAll();

    @Operation(summary = "Onboard a tenant",
            description = "Creates the registry row, provisions the isolated tenant database and role, "
                    + "runs the tenant Flyway migrations and registers the connection pool — "
                    + "the tenant is live immediately, no restart. DB credentials for the short code "
                    + "must be configured (qcp.multitenancy.tenants.<code>.*) before calling.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tenant onboarded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Short code already exists"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Provisioning failed")
    })
    ResponseEntity<APIResponse<TenantResponse>> create(TenantRequest request);
}
