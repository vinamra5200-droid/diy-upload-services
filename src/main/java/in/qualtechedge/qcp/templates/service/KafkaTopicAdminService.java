package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.KafkaTopicRequest;
import in.qualtechedge.qcp.templates.dto.response.KafkaTopicResponse;
import java.util.List;
import java.util.Map;

/**
 * Backs diy-upload-web's topic picker for a template's {@code post_load_action} (kafkaTopic) — so
 * an admin can see and create topics on the shared Kafka cluster instead of typing a name blind.
 * Talks to the broker via {@code AdminClient}, not this service's own DB.
 */
public interface KafkaTopicAdminService {

    List<KafkaTopicResponse> listTopics();

    KafkaTopicResponse createTopic(KafkaTopicRequest request);

    /**
     * Used by {@link in.qualtechedge.qcp.templates.service.impl.QueueConfigServiceImpl#accept} —
     * a blank/null {@code bootstrapServersOverride} uses the shared cluster (same as
     * {@link #createTopic(KafkaTopicRequest)}); a non-blank one talks to that cluster instead, so a
     * queue config's own {@code topicBootstrapServers} is honored for topic creation. The actual
     * Kafka producer honors the same override at publish time via
     * {@link in.qualtechedge.qcp.templates.service.impl.KafkaProducerRegistry}, not just this
     * one-off admin call.
     */
    void createTopic(String bootstrapServersOverride, String topicName, int partitions, int replicationFactor,
            Map<String, String> topicConfigs);
}
