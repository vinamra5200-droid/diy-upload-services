package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.ProducerCompressionType;
import in.qualtechedge.qcp.templates.enums.TopicCleanupPolicy;
import java.time.OffsetDateTime;

/** Wire shape locked by admin-api-contract.md §7.1 — nested {@code producer}/{@code topic}, not flat. */
public record QueueConfigResponse(
        String queueConfigId,
        String queueConfigName,
        String description,
        Producer producer,
        Topic topic,
        String apiConfigId,
        ConfigStatus status,
        String submittedBy,
        String rejectionReason,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record Producer(
            String clientId,
            String acks,
            int batchSizeKb,
            int lingerMs,
            ProducerCompressionType compressionType,
            int retries,
            int maxInFlightRequests
    ) {
    }

    /** {@code dltTopicName} is derived, not stored — null while {@code topicName} itself is null. */
    public record Topic(
            String topicName,
            int partitions,
            int replicationFactor,
            int retentionHours,
            TopicCleanupPolicy cleanupPolicy,
            int consumerConcurrency,
            String dltTopicName
    ) {
    }
}
