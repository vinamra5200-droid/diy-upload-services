package in.qualtechedge.qcp.templates.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link KafkaTemplate} to publish on for a given bootstrap-servers override —
 * {@link PostLoadActionDispatcherImpl} is the only caller, via {@code QueueConfig.topicBootstrapServers}
 * (queue orchestration, "useExisting" mode) or {@code Template.kafkaBootstrapServers} ("custom"
 * mode). A blank/null override returns the one shared {@link KafkaTemplate} bean every other
 * producer in this service uses (see {@code application.yaml}'s {@code spring.kafka.producer.*}).
 * A non-blank override gets its own lazily-created, cached {@link DefaultKafkaProducerFactory} —
 * same base producer config (serializers, acks, etc.) as the shared one, with only
 * {@code bootstrap.servers} replaced — reused across calls instead of opening a fresh connection
 * per publish. Unlike {@link KafkaTopicAdminServiceImpl}'s per-call {@code AdminClient} (cheap,
 * short-lived, throwaway), a Kafka producer is comparatively expensive to open (connection pool,
 * buffers, background I/O thread) — hence the cache, and {@link #destroy} to close them on
 * shutdown since they're created programmatically, not as Spring beans Spring itself would close.
 */
@Component
@Slf4j
public class KafkaProducerRegistry implements DisposableBean {

    private final KafkaTemplate<Object, Object> defaultKafkaTemplate;
    private final ConcurrentMap<String, KafkaTemplate<Object, Object>> overrideTemplates = new ConcurrentHashMap<>();

    public KafkaProducerRegistry(KafkaTemplate<Object, Object> defaultKafkaTemplate) {
        this.defaultKafkaTemplate = defaultKafkaTemplate;
    }

    public KafkaTemplate<Object, Object> get(String bootstrapServersOverride) {
        if (bootstrapServersOverride == null || bootstrapServersOverride.isBlank()) {
            return defaultKafkaTemplate;
        }
        return overrideTemplates.computeIfAbsent(bootstrapServersOverride, this::newTemplate);
    }

    private KafkaTemplate<Object, Object> newTemplate(String bootstrapServersOverride) {
        log.info("Opening a Kafka producer for override cluster: {}", bootstrapServersOverride);
        Map<String, Object> configs = new HashMap<>(defaultKafkaTemplate.getProducerFactory().getConfigurationProperties());
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServersOverride);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configs));
    }

    @Override
    public void destroy() {
        overrideTemplates.values().forEach(template -> {
            ProducerFactory<Object, Object> factory = template.getProducerFactory();
            if (factory instanceof DisposableBean disposable) {
                try {
                    disposable.destroy();
                } catch (Exception e) {
                    log.warn("Failed to close a dynamic Kafka producer", e);
                }
            }
        });
    }
}
