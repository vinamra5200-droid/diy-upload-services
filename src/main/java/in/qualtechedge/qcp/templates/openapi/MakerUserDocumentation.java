package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.MakerUserRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.MakerUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Maker Users", description = "Batch upload operators (admin-api-contract.md §4)")
public interface MakerUserDocumentation {

    @Operation(summary = "List maker users")
    ResponseEntity<APIResponse<List<MakerUserResponse>>> list();

    @Operation(summary = "Get a maker user by id")
    ResponseEntity<APIResponse<MakerUserResponse>> getById(String userId);

    @Operation(summary = "Create a maker user")
    ResponseEntity<APIResponse<MakerUserResponse>> create(MakerUserRequest request);

    @Operation(summary = "Update a maker user")
    ResponseEntity<APIResponse<MakerUserResponse>> update(String userId, MakerUserRequest request);

    @Operation(summary = "Submit a maker user for review")
    ResponseEntity<APIResponse<MakerUserResponse>> submit(String userId);

    @Operation(summary = "Accept a submitted maker user")
    ResponseEntity<APIResponse<MakerUserResponse>> accept(String userId);

    @Operation(summary = "Reject a submitted maker user")
    ResponseEntity<APIResponse<MakerUserResponse>> reject(String userId, RejectRequest request);
}
