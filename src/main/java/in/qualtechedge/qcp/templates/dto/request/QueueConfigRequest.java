package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.ProducerCompressionType;
import in.qualtechedge.qcp.templates.enums.TopicCleanupPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Wire shape locked by admin-api-contract.md §7.1 — nested {@code producer}/{@code topic},
 * matching {@link in.qualtechedge.qcp.templates.dto.response.QueueConfigResponse}. {@code
 * apiConfigId} is the "Consumer Callback" step — nullable until that step is completed (see
 * {@link in.qualtechedge.qcp.templates.entity.QueueConfig}). {@code producer.acks} is
 * {@code '0'}/{@code '1'}/{@code 'all'}, not an enum — those first two aren't valid Java
 * identifiers.
 */
public record QueueConfigRequest(
        @NotBlank(message = "queueConfigName must not be blank")
        @Size(max = 120, message = "queueConfigName must be at most 120 characters")
        String queueConfigName,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @NotNull(message = "producer must not be null")
        @Valid
        Producer producer,

        @NotNull(message = "topic must not be null")
        @Valid
        Topic topic,

        String apiConfigId
) {
    public record Producer(
            @Size(max = 120, message = "producer.clientId must be at most 120 characters")
            String clientId,

            @Pattern(regexp = "0|1|all", message = "producer.acks must be '0', '1', or 'all'")
            String acks,

            @Min(value = 1, message = "producer.batchSizeKb must be at least 1")
            @Max(value = 1000, message = "producer.batchSizeKb must be at most 1000")
            Integer batchSizeKb,

            @Min(value = 0, message = "producer.lingerMs must be at least 0")
            Integer lingerMs,

            ProducerCompressionType compressionType,

            @Min(value = 0, message = "producer.retries must be at least 0")
            Integer retries,

            @Min(value = 1, message = "producer.maxInFlightRequests must be at least 1")
            Integer maxInFlightRequests
    ) {
    }

    public record Topic(
            @NotBlank(message = "topic.topicName must not be blank")
            @Size(max = 249, message = "topic.topicName must be at most 249 characters")
            String topicName,

            @Size(max = 500, message = "topic.bootstrapServers must be at most 500 characters")
            String bootstrapServers,

            @Min(value = 1, message = "topic.partitions must be at least 1")
            @Max(value = 1000, message = "topic.partitions must be at most 1000")
            Integer partitions,

            @Min(value = 1, message = "topic.replicationFactor must be at least 1")
            @Max(value = 32, message = "topic.replicationFactor must be at most 32")
            Integer replicationFactor,

            @Min(value = 1, message = "topic.retentionHours must be at least 1")
            Integer retentionHours,

            TopicCleanupPolicy cleanupPolicy
    ) {
    }
}
