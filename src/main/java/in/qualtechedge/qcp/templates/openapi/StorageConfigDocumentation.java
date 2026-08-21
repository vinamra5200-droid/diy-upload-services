package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.StorageConfigRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.StorageConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Storage Connections", description = "Interim object-store connections (admin-api-contract.md §5)")
public interface StorageConfigDocumentation {

    @Operation(summary = "List storage connections")
    ResponseEntity<APIResponse<List<StorageConfigResponse>>> list();

    @Operation(summary = "Get a storage connection by id")
    ResponseEntity<APIResponse<StorageConfigResponse>> getById(String configId);

    @Operation(summary = "Create a storage connection")
    ResponseEntity<APIResponse<StorageConfigResponse>> create(StorageConfigRequest request);

    @Operation(summary = "Update a storage connection")
    ResponseEntity<APIResponse<StorageConfigResponse>> update(String configId, StorageConfigRequest request);

    @Operation(summary = "Submit a storage connection for review")
    ResponseEntity<APIResponse<StorageConfigResponse>> submit(String configId);

    @Operation(summary = "Accept a submitted storage connection")
    ResponseEntity<APIResponse<StorageConfigResponse>> accept(String configId);

    @Operation(summary = "Reject a submitted storage connection")
    ResponseEntity<APIResponse<StorageConfigResponse>> reject(String configId, RejectRequest request);
}
