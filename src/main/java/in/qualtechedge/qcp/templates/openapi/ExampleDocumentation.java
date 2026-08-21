package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.ExampleRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.ExampleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/**
 * OpenAPI documentation contract (QCP rule: Swagger annotations live here; the controller
 * implements this interface and keeps validation/security/request-mapping annotations).
 * Swagger's own @ApiResponse is referenced fully qualified to avoid clashing with the QCP
 * APIResponse envelope.
 */
@Tag(name = "Examples", description = "CRUD operations for the example resource")
public interface ExampleDocumentation {

    @Operation(summary = "Create an example", description = "Creates a new example resource.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Example created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    })
    ResponseEntity<APIResponse<ExampleResponse>> create(ExampleRequest request);

    @Operation(summary = "Get an example by id", description = "Returns a single example resource.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Example found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Example not found")
    })
    ResponseEntity<APIResponse<ExampleResponse>> getById(UUID id);

    @Operation(summary = "List all examples", description = "Returns all example resources.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Examples returned")
    ResponseEntity<APIResponse<List<ExampleResponse>>> getAll();

    @Operation(summary = "Update an example", description = "Updates an existing example resource.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Example updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Example not found")
    })
    ResponseEntity<APIResponse<ExampleResponse>> update(UUID id, ExampleRequest request);

    @Operation(summary = "Delete an example", description = "Deletes an existing example resource.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Example deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Example not found")
    })
    ResponseEntity<APIResponse<Void>> delete(UUID id);
}
