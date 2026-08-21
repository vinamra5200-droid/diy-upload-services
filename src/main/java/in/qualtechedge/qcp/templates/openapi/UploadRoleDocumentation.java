package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.UploadRoleRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadRoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Upload Roles", description = "Upload roles gating maker-user access to processes (admin-api-contract.md §3)")
public interface UploadRoleDocumentation {

    @Operation(summary = "List upload roles")
    ResponseEntity<APIResponse<List<UploadRoleResponse>>> list();

    @Operation(summary = "Get an upload role by id")
    ResponseEntity<APIResponse<UploadRoleResponse>> getById(String roleId);

    @Operation(summary = "Create an upload role")
    ResponseEntity<APIResponse<UploadRoleResponse>> create(UploadRoleRequest request);

    @Operation(summary = "Update an upload role")
    ResponseEntity<APIResponse<UploadRoleResponse>> update(String roleId, UploadRoleRequest request);

    @Operation(summary = "Submit an upload role for review")
    ResponseEntity<APIResponse<UploadRoleResponse>> submit(String roleId);

    @Operation(summary = "Accept a submitted upload role")
    ResponseEntity<APIResponse<UploadRoleResponse>> accept(String roleId);

    @Operation(summary = "Reject a submitted upload role")
    ResponseEntity<APIResponse<UploadRoleResponse>> reject(String roleId, RejectRequest request);
}
