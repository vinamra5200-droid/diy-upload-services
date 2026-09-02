package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.request.KafkaTopicRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.KafkaTopicResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Kafka Topics (Admin)", description = "Topic picker backing a template's post-load-action kafkaTopic")
public interface KafkaTopicDocumentation {

    @Operation(summary = "List Kafka topics", description = "Topics on the shared Kafka cluster, with partition/replication counts.")
    ResponseEntity<APIResponse<List<KafkaTopicResponse>>> listTopics();

    @Operation(summary = "Create a Kafka topic", description = "Creates a topic on the shared Kafka cluster. 409 if it already exists.")
    ResponseEntity<APIResponse<KafkaTopicResponse>> createTopic(KafkaTopicRequest request);
}
