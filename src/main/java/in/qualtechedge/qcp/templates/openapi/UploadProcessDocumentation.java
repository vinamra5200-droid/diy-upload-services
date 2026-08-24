package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.ProcessResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Upload Processes & Templates", description = "Read-only process/template access for the upload-operator flow (upload-api-contract.md §1)")
public interface UploadProcessDocumentation {

    @Operation(summary = "List permitted processes",
            description = "Active processes the current actor's UploadRole(s) grant access to.")
    ResponseEntity<APIResponse<List<ProcessResponse>>> listProcesses();

    @Operation(summary = "Get a process's active template", description = "404 if the process has no active template.")
    ResponseEntity<APIResponse<TemplateResponse>> getActiveTemplate(String processId);

    @Operation(summary = "Download a blank template",
            description = "Headers-only file (from template.fields[].sourceColumn) in the requested format "
                    + "(xlsx/csv/json), wrapped at uploadFormats.json.rootArrayPath for JSON.")
    ResponseEntity<byte[]> downloadBlankTemplate(String processId, String format);
}
