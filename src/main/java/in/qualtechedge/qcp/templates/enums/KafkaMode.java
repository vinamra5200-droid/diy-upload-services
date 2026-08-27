package in.qualtechedge.qcp.templates.enums;

/**
 * How a template's Kafka post-load action resolves its topic (V1_4_0 — templates.kafka_mode).
 * {@code useExisting} binds {@code kafkaQueueConfigId} (a saved {@code queue_configs} row, which
 * supplies the topic/producer/consumer-callback settings); {@code custom} (or {@code null}, for
 * templates saved before Queue Orchestration existed) uses {@code kafkaTopic}/
 * {@code kafkaBootstrapServers} directly.
 */
public enum KafkaMode {
    useExisting,
    custom
}
