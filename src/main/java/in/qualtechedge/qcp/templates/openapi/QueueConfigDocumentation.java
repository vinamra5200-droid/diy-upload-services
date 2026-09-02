package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.QueueConfigRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.QueueConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Queue Configurations", description = "Named Kafka queue (topic) configurations, maker-checker governed")
public interface QueueConfigDocumentation {

    @Operation(summary = "List queue configs")
    ResponseEntity<APIResponse<List<QueueConfigResponse>>> list();

    @Operation(summary = "Get a queue config by id")
    ResponseEntity<APIResponse<QueueConfigResponse>> getById(String configId);

    @Operation(summary = "Create a queue config")
    ResponseEntity<APIResponse<QueueConfigResponse>> create(QueueConfigRequest request);

    @Operation(summary = "Update a queue config")
    ResponseEntity<APIResponse<QueueConfigResponse>> update(String configId, QueueConfigRequest request);

    @Operation(summary = "Submit a queue config for review")
    ResponseEntity<APIResponse<QueueConfigResponse>> submit(String configId);

    @Operation(summary = "Accept a submitted queue config", description = "Also creates the topic on the shared Kafka broker.")
    ResponseEntity<APIResponse<QueueConfigResponse>> accept(String configId);

    @Operation(summary = "Reject a submitted queue config")
    ResponseEntity<APIResponse<QueueConfigResponse>> reject(String configId, RejectRequest request);
}
