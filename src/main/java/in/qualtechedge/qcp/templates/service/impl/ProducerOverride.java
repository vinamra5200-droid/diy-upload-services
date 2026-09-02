package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.entity.QueueConfig;

/**
 * Per-target Kafka producer config override, and the cache key {@link KafkaProducerRegistry} keys
 * its dynamic {@code KafkaTemplate}s by. A {@code null} field means "inherit from the shared
 * {@code spring.kafka.producer.*} defaults" — since equality is structural (record), editing a
 * queue config's producer settings naturally produces a new cache entry instead of {@link
 * KafkaProducerRegistry} silently reusing a producer built from the old settings.
 */
public record ProducerOverride(
        String bootstrapServers,
        String clientId,
        String acks,
        Integer batchSizeKb,
        Integer lingerMs,
        String compressionType,
        Integer retries,
        Integer maxInFlightRequests) {

    /** No override at all — {@link KafkaProducerRegistry#get} returns the shared default template for this. */
    public static final ProducerOverride NONE =
            new ProducerOverride(null, null, null, null, null, null, null, null);

    /** {@code Template.kafkaMode == custom}: only a bootstrap-servers override is available. */
    public static ProducerOverride bootstrapServersOnly(String bootstrapServers) {
        String normalized = blankToNull(bootstrapServers);
        return normalized == null
                ? NONE
                : new ProducerOverride(normalized, null, null, null, null, null, null, null);
    }

    /**
     * {@code Template.kafkaMode == useExisting}: apply the bound {@link QueueConfig}'s full
     * producer settings. Always publishes on the shared cluster — {@link QueueConfig} has no
     * bootstrap-servers override of its own.
     */
    public static ProducerOverride of(QueueConfig queueConfig) {
        return new ProducerOverride(
                null,
                blankToNull(queueConfig.getProducerClientId()),
                queueConfig.getProducerAcks(),
                queueConfig.getProducerBatchSizeKb(),
                queueConfig.getProducerLingerMs(),
                queueConfig.getProducerCompressionType().name(),
                queueConfig.getProducerRetries(),
                queueConfig.getProducerMaxInFlightRequests());
    }

    boolean isEmpty() {
        return this.equals(NONE);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
