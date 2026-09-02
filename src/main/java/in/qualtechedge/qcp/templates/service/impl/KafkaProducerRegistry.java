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
 * Resolves the {@link KafkaTemplate} to publish on for a given {@link ProducerOverride} —
 * {@link PostLoadActionDispatcherImpl} is the only caller, via a bound {@code QueueConfig}'s full
 * producer settings (queue orchestration, "useExisting" mode) or just {@code
 * Template.kafkaBootstrapServers} ("custom" mode, which has no other producer settings to give).
 * {@link ProducerOverride#NONE} (or an override with every field null) returns the one shared
 * {@link KafkaTemplate} bean every other producer in this service uses (see {@code
 * application.yaml}'s {@code spring.kafka.producer.*}). A non-empty override gets its own
 * lazily-created, cached {@link DefaultKafkaProducerFactory} — same base producer config
 * (serializers, etc.) as the shared one, with only the override's non-null fields replaced —
 * reused across calls instead of opening a fresh connection per publish. Unlike {@link
 * KafkaTopicAdminServiceImpl}'s per-call {@code AdminClient} (cheap, short-lived, throwaway), a
 * Kafka producer is comparatively expensive to open (connection pool, buffers, background I/O
 * thread) — hence the cache, and {@link #destroy} to close them on shutdown since they're created
 * programmatically, not as Spring beans Spring itself would close.
 */
@Component
@Slf4j
public class KafkaProducerRegistry implements DisposableBean {

    private final KafkaTemplate<Object, Object> defaultKafkaTemplate;
    private final ConcurrentMap<ProducerOverride, KafkaTemplate<Object, Object>> overrideTemplates = new ConcurrentHashMap<>();

    public KafkaProducerRegistry(KafkaTemplate<Object, Object> defaultKafkaTemplate) {
        this.defaultKafkaTemplate = defaultKafkaTemplate;
    }

    public KafkaTemplate<Object, Object> get(ProducerOverride override) {
        if (override == null || override.isEmpty()) {
            return defaultKafkaTemplate;
        }
        return overrideTemplates.computeIfAbsent(override, this::newTemplate);
    }

    private KafkaTemplate<Object, Object> newTemplate(ProducerOverride override) {
        log.info("Opening a Kafka producer for override: {}", override);
        Map<String, Object> configs = new HashMap<>(defaultKafkaTemplate.getProducerFactory().getConfigurationProperties());
        if (override.bootstrapServers() != null) {
            configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, override.bootstrapServers());
        }
        if (override.clientId() != null) {
            configs.put(ProducerConfig.CLIENT_ID_CONFIG, override.clientId());
        }
        if (override.acks() != null) {
            configs.put(ProducerConfig.ACKS_CONFIG, override.acks());
        }
        if (override.batchSizeKb() != null) {
            configs.put(ProducerConfig.BATCH_SIZE_CONFIG, override.batchSizeKb() * 1024);
        }
        if (override.lingerMs() != null) {
            configs.put(ProducerConfig.LINGER_MS_CONFIG, override.lingerMs());
        }
        if (override.compressionType() != null) {
            configs.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, override.compressionType());
        }
        if (override.retries() != null) {
            configs.put(ProducerConfig.RETRIES_CONFIG, override.retries());
        }
        if (override.maxInFlightRequests() != null) {
            configs.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, override.maxInFlightRequests());
        }
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
