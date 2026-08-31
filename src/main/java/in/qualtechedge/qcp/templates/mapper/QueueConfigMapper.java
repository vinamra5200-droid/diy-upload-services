package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.request.QueueConfigRequest;
import in.qualtechedge.qcp.templates.dto.response.QueueConfigResponse;
import in.qualtechedge.qcp.templates.entity.QueueConfig;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import org.springframework.stereotype.Component;

@Component
public class QueueConfigMapper {

    public QueueConfig toEntity(QueueConfigRequest request, String createdBy) {
        QueueConfig entity = new QueueConfig();
        entity.setQueueConfigId(IdGenerator.generate("queue"));
        applyRequest(entity, request);
        entity.setStatus(ConfigStatus.draft);
        entity.setCreatedBy(createdBy);
        return entity;
    }

    public void updateEntity(QueueConfig entity, QueueConfigRequest request) {
        applyRequest(entity, request);
    }

    private void applyRequest(QueueConfig entity, QueueConfigRequest request) {
        entity.setQueueConfigName(request.queueConfigName());
        entity.setDescription(request.description() == null ? "" : request.description());

        // producer/topic are absent on Create (admin-api-contract.md §7.2) — a bare `new Producer(...)`/
        // `new Topic(...)` of all-null fields falls through to the same per-field defaults Update relies on.
        QueueConfigRequest.Producer producer = request.producer() == null
                ? new QueueConfigRequest.Producer(null, null, null, null, null, null, null)
                : request.producer();
        entity.setProducerClientId(producer.clientId() == null ? "" : producer.clientId());
        entity.setProducerAcks(producer.acks() == null ? "1" : producer.acks());
        entity.setProducerBatchSizeKb(producer.batchSizeKb() == null ? 16 : producer.batchSizeKb());
        entity.setProducerLingerMs(producer.lingerMs() == null ? 0 : producer.lingerMs());
        if (producer.compressionType() != null) {
            entity.setProducerCompressionType(producer.compressionType());
        }
        entity.setProducerRetries(producer.retries() == null ? 3 : producer.retries());
        entity.setProducerMaxInFlightRequests(producer.maxInFlightRequests() == null ? 5 : producer.maxInFlightRequests());

        // topicName has no fallback (unlike every other field here) — it stays null until the
        // Topic wizard step is actually filled in via Update; the DB column allows that (V1_4_10).
        QueueConfigRequest.Topic topic = request.topic() == null
                ? new QueueConfigRequest.Topic(null, null, null, null, null, null)
                : request.topic();
        entity.setTopicName(topic.topicName());
        entity.setTopicBootstrapServers(topic.bootstrapServers() == null ? "" : topic.bootstrapServers());
        entity.setTopicPartitions(topic.partitions() == null ? 3 : topic.partitions());
        entity.setTopicReplicationFactor(topic.replicationFactor() == null ? 1 : topic.replicationFactor());
        entity.setTopicRetentionHours(topic.retentionHours() == null ? 168 : topic.retentionHours());
        if (topic.cleanupPolicy() != null) {
            entity.setTopicCleanupPolicy(topic.cleanupPolicy());
        }

        entity.setApiConfigId(request.apiConfigId());
    }

    public QueueConfigResponse toResponse(QueueConfig entity) {
        QueueConfigResponse.Producer producer = new QueueConfigResponse.Producer(
                entity.getProducerClientId(),
                entity.getProducerAcks(),
                entity.getProducerBatchSizeKb(),
                entity.getProducerLingerMs(),
                entity.getProducerCompressionType(),
                entity.getProducerRetries(),
                entity.getProducerMaxInFlightRequests());
        QueueConfigResponse.Topic topic = new QueueConfigResponse.Topic(
                entity.getTopicName(),
                entity.getTopicBootstrapServers(),
                entity.getTopicPartitions(),
                entity.getTopicReplicationFactor(),
                entity.getTopicRetentionHours(),
                entity.getTopicCleanupPolicy());
        return new QueueConfigResponse(
                entity.getQueueConfigId(),
                entity.getQueueConfigName(),
                entity.getDescription(),
                producer,
                topic,
                entity.getApiConfigId(),
                entity.getStatus(),
                entity.getSubmittedBy(),
                entity.getRejectionReason(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
