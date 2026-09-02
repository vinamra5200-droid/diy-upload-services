package in.qualtechedge.qcp.templates.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Backs {@link in.qualtechedge.qcp.templates.service.impl.BatchChunkPublisherImpl} — the topic
 * it publishes batch-upload chunks to (must match validation-service's
 * {@code validation-service.kafka.topics.batch-chunk}) and how many rows go in one chunk. Also
 * backs {@link in.qualtechedge.qcp.templates.service.impl.PostLoadActionDispatcherImpl}'s chunk
 * size — its topic is per-template ({@code Template.kafkaTopic}), not configured here. And
 * {@code topics.queueConfig} backs {@link in.qualtechedge.qcp.templates.scheduler.QueueConfigOutboxPublisher}
 * — must match consumer-callback-service's own topic name for the same control-plane feed.
 * Deliberately not under {@code spring.kafka.*} — that namespace belongs to Spring's own
 * ProducerFactory/KafkaTemplate wiring in application.yaml.
 */
@Component
@ConfigurationProperties(prefix = "qcp.kafka")
@Data
public class KafkaBatchProperties {

    private Topics topics = new Topics();
    private int batchChunkSize;
    private int postLoadActionChunkSize;

    @Data
    public static class Topics {
        private String batchChunk;
        private String queueConfig;
    }
}
