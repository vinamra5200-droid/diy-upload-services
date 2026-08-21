package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.DatabaseConnectionRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.DatabaseConnectionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Database Connections", description = "Database connections (admin-api-contract.md §6)")
public interface DatabaseConnectionDocumentation {

    @Operation(summary = "List database connections")
    ResponseEntity<APIResponse<List<DatabaseConnectionResponse>>> list();

    @Operation(summary = "Get a database connection by id")
    ResponseEntity<APIResponse<DatabaseConnectionResponse>> getById(String connectionId);

    @Operation(summary = "Create a database connection")
    ResponseEntity<APIResponse<DatabaseConnectionResponse>> create(DatabaseConnectionRequest request);

    @Operation(summary = "Update a database connection")
    ResponseEntity<APIResponse<DatabaseConnectionResponse>> update(String connectionId, DatabaseConnectionRequest request);

    @Operation(summary = "Submit a database connection for review")
    ResponseEntity<APIResponse<DatabaseConnectionResponse>> submit(String connectionId);

    @Operation(summary = "Accept a submitted database connection")
    ResponseEntity<APIResponse<DatabaseConnectionResponse>> accept(String connectionId);

    @Operation(summary = "Reject a submitted database connection")
    ResponseEntity<APIResponse<DatabaseConnectionResponse>> reject(String connectionId, RejectRequest request);
}
