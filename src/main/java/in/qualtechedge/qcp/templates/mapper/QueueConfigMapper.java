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

        QueueConfigRequest.Producer producer = request.producer();
        entity.setProducerClientId(producer.clientId() == null ? "" : producer.clientId());
        entity.setProducerAcks(producer.acks() == null ? "1" : producer.acks());
        entity.setProducerBatchSizeKb(producer.batchSizeKb() == null ? 16 : producer.batchSizeKb());
        entity.setProducerLingerMs(producer.lingerMs() == null ? 0 : producer.lingerMs());
        if (producer.compressionType() != null) {
            entity.setProducerCompressionType(producer.compressionType());
        }
        entity.setProducerRetries(producer.retries() == null ? 3 : producer.retries());
        entity.setProducerMaxInFlightRequests(producer.maxInFlightRequests() == null ? 5 : producer.maxInFlightRequests());

        QueueConfigRequest.Topic topic = request.topic();
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
