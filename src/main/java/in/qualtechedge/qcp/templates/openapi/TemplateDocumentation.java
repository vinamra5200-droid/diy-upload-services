package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.CloneTemplateRequest;
import in.qualtechedge.qcp.templates.dto.request.CreateTemplateRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.UpdateTemplateRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateVersionSnapshotResponse;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Templates", description = "Upload templates (admin-api-contract.md §2)")
public interface TemplateDocumentation {

    @Operation(summary = "List templates by process")
    ResponseEntity<APIResponse<List<TemplateSummaryResponse>>> listByProcess(String processId, ConfigStatus status);

    @Operation(summary = "Get a template by id")
    ResponseEntity<APIResponse<TemplateResponse>> getById(String templateId);

    @Operation(summary = "Create a template")
    ResponseEntity<APIResponse<TemplateResponse>> create(String processId, CreateTemplateRequest request);

    @Operation(summary = "Update a template (full replace)")
    ResponseEntity<APIResponse<TemplateResponse>> update(String templateId, UpdateTemplateRequest request);

    @Operation(summary = "Submit a template for review")
    ResponseEntity<APIResponse<TemplateResponse>> submit(String templateId);

    @Operation(summary = "Accept a submitted template")
    ResponseEntity<APIResponse<TemplateResponse>> accept(String templateId);

    @Operation(summary = "Reject a submitted template")
    ResponseEntity<APIResponse<TemplateResponse>> reject(String templateId, RejectRequest request);

    @Operation(summary = "Clone a template")
    ResponseEntity<APIResponse<TemplateResponse>> clone(String templateId, CloneTemplateRequest request);

    @Operation(summary = "List a template's version history")
    ResponseEntity<APIResponse<List<TemplateVersionSnapshotResponse>>> listVersions(String templateId);

    @Operation(summary = "Get a specific template version snapshot")
    ResponseEntity<APIResponse<TemplateVersionSnapshotResponse>> getVersion(String templateId, String version);
}
