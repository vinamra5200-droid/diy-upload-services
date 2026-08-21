package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.ProcessRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.ProcessResponse;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Processes", description = "Upload process definitions (admin-api-contract.md §1)")
public interface ProcessDocumentation {

    @Operation(summary = "List processes")
    ResponseEntity<APIResponse<PageResponse<ProcessResponse>>> list(ConfigStatus status, String search, int page, int limit);

    @Operation(summary = "Get a process by id")
    ResponseEntity<APIResponse<ProcessResponse>> getById(String processId);

    @Operation(summary = "Create a process")
    ResponseEntity<APIResponse<ProcessResponse>> create(ProcessRequest request);

    @Operation(summary = "Update a process")
    ResponseEntity<APIResponse<ProcessResponse>> update(String processId, ProcessRequest request);

    @Operation(summary = "Submit a process for review")
    ResponseEntity<APIResponse<ProcessResponse>> submit(String processId);

    @Operation(summary = "Accept a submitted process")
    ResponseEntity<APIResponse<ProcessResponse>> accept(String processId);

    @Operation(summary = "Reject a submitted process")
    ResponseEntity<APIResponse<ProcessResponse>> reject(String processId, RejectRequest request);
}
