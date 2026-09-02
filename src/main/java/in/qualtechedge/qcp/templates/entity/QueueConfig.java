package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.ProducerCompressionType;
import in.qualtechedge.qcp.templates.enums.TopicCleanupPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Reusable Kafka producer + topic settings bound to a consumer callback contract ({@code
 * queue_configs}, V1_4_0) — "Queue Orchestration" (admin-api-contract.md §7). List+create+edit+
 * maker-checker like storage/database/api-config, not a singleton. {@code apiConfigId} references
 * an {@link ApiConfig} row the consumer calls once it dequeues a message ("Consumer Callback" step)
 * — nullable until that step is completed. A {@link in.qualtechedge.qcp.templates.entity.Template}
 * binds to one of these via {@code kafkaMode = useExisting} / {@code kafkaQueueConfigId} instead of
 * typing {@code kafkaTopic}/{@code kafkaBootstrapServers} by hand.
 * {@link in.qualtechedge.qcp.templates.service.impl.QueueConfigServiceImpl#accept} creates {@code
 * topicName} on the one shared Kafka cluster once a checker approves.
 */
@Entity
@Table(name = "queue_configs")
@Getter
@Setter
@NoArgsConstructor
public class QueueConfig {

    @Id
    @Column(name = "queue_config_id")
    private String queueConfigId;

    @Column(name = "queue_config_name", nullable = false)
    private String queueConfigName;

    @Column(nullable = false, columnDefinition = "text")
    private String description = "";

    @Column(name = "producer_client_id", nullable = false)
    private String producerClientId = "";

    /** {@code '0'}, {@code '1'}, or {@code 'all'} — not a Java enum, those aren't valid identifiers. */
    @Column(name = "producer_acks", nullable = false)
    private String producerAcks = "1";

    @Column(name = "producer_batch_size_kb", nullable = false)
    private int producerBatchSizeKb = 16;

    @Column(name = "producer_linger_ms", nullable = false)
    private int producerLingerMs = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "producer_compression_type", nullable = false)
    private ProducerCompressionType producerCompressionType = ProducerCompressionType.none;

    @Column(name = "producer_retries", nullable = false)
    private int producerRetries = 3;

    @Column(name = "producer_max_in_flight_requests", nullable = false)
    private int producerMaxInFlightRequests = 5;

    /** Null until the Topic wizard step is filled in via Update — see {@code QueueConfigService#create}. */
    @Column(name = "topic_name")
    private String topicName;

    @Column(name = "topic_partitions", nullable = false)
    private int topicPartitions = 3;

    @Column(name = "topic_replication_factor", nullable = false)
    private int topicReplicationFactor = 1;

    @Column(name = "topic_retention_hours", nullable = false)
    private int topicRetentionHours = 168;

    @Enumerated(EnumType.STRING)
    @Column(name = "topic_cleanup_policy", nullable = false)
    private TopicCleanupPolicy topicCleanupPolicy = TopicCleanupPolicy.delete;

    /** consumer-callback-service's per-{@code (tenant, topic)} listener container concurrency (V1_4_19) — see {@code ChunkConsumerRegistry}. */
    @Column(name = "topic_consumer_concurrency", nullable = false)
    private int topicConsumerConcurrency = 1;

    /** The Outbound API Config the consumer calls once it dequeues a message from this topic. */
    @Column(name = "api_config_id")
    private String apiConfigId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigStatus status = ConfigStatus.draft;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
