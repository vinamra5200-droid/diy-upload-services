package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.ApiConfigRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.ApiConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "API Configs", description = "Reusable outbound HTTP call definitions (admin-api-contract.md §7)")
public interface ApiConfigDocumentation {

    @Operation(summary = "List API configs")
    ResponseEntity<APIResponse<List<ApiConfigResponse>>> list();

    @Operation(summary = "Get an API config by id")
    ResponseEntity<APIResponse<ApiConfigResponse>> getById(String configId);

    @Operation(summary = "Create an API config")
    ResponseEntity<APIResponse<ApiConfigResponse>> create(ApiConfigRequest request);

    @Operation(summary = "Update an API config")
    ResponseEntity<APIResponse<ApiConfigResponse>> update(String configId, ApiConfigRequest request);

    @Operation(summary = "Submit an API config for review")
    ResponseEntity<APIResponse<ApiConfigResponse>> submit(String configId);

    @Operation(summary = "Accept a submitted API config")
    ResponseEntity<APIResponse<ApiConfigResponse>> accept(String configId);

    @Operation(summary = "Reject a submitted API config")
    ResponseEntity<APIResponse<ApiConfigResponse>> reject(String configId, RejectRequest request);
}
