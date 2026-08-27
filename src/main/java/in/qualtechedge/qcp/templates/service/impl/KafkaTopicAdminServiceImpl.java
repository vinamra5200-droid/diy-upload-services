package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.KafkaTopicRequest;
import in.qualtechedge.qcp.templates.dto.response.KafkaTopicResponse;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.service.KafkaTopicAdminService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

/**
 * Talks to the one shared Kafka cluster (spring.kafka.bootstrap-servers) via a short-lived
 * {@code AdminClient} built from the autoconfigured {@link KafkaAdmin} bean — same cluster
 * {@link BatchChunkPublisherImpl} and {@link PostLoadActionDispatcherImpl} publish to. Every
 * tenant admin hitting this (mounted like any other {@code /api/v1/admin/*} controller) sees and
 * can create topics on that one cluster; there is no per-tenant topic namespacing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaTopicAdminServiceImpl implements KafkaTopicAdminService {

    private static final int ADMIN_TIMEOUT_SECONDS = 10;

    private final KafkaAdmin kafkaAdmin;

    @Override
    public List<KafkaTopicResponse> listTopics() {
        try (AdminClient client = buildClient(null)) {
            Set<String> names = client.listTopics().listings().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .stream().map(TopicListing::name).collect(Collectors.toSet());
            Map<String, TopicDescription> descriptions = client.describeTopics(names).allTopicNames()
                    .get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return descriptions.values().stream()
                    .map(this::toResponse)
                    .sorted(Comparator.comparing(KafkaTopicResponse::name))
                    .toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while listing Kafka topics", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to list Kafka topics", e);
        }
    }

    @Override
    public KafkaTopicResponse createTopic(KafkaTopicRequest request) {
        createTopic(null, request.name(), request.partitions(), request.replicationFactor(), Map.of());
        return new KafkaTopicResponse(request.name(), request.partitions(), request.replicationFactor());
    }

    @Override
    public void createTopic(String bootstrapServersOverride, String topicName, int partitions, int replicationFactor,
            Map<String, String> topicConfigs) {
        NewTopic newTopic = new NewTopic(topicName, partitions, (short) replicationFactor).configs(topicConfigs);
        try (AdminClient client = buildClient(bootstrapServersOverride)) {
            client.createTopics(List.of(newTopic)).all().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating Kafka topic " + topicName, e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                throw new ConflictException("Topic " + topicName + " already exists");
            }
            throw new IllegalStateException("Failed to create Kafka topic " + topicName, e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out creating Kafka topic " + topicName, e);
        }
        log.info("Kafka topic created: name={}, partitions={}, replicationFactor={}, bootstrapServers={}",
                topicName, partitions, replicationFactor,
                bootstrapServersOverride == null || bootstrapServersOverride.isBlank() ? "<shared>" : bootstrapServersOverride);
    }

    private AdminClient buildClient(String bootstrapServersOverride) {
        if (bootstrapServersOverride == null || bootstrapServersOverride.isBlank()) {
            return AdminClient.create(kafkaAdmin.getConfigurationProperties());
        }
        Map<String, Object> configs = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServersOverride);
        return AdminClient.create(configs);
    }

    private KafkaTopicResponse toResponse(TopicDescription description) {
        int partitions = description.partitions().size();
        int replicationFactor = description.partitions().isEmpty() ? 0 : description.partitions().get(0).replicas().size();
        return new KafkaTopicResponse(description.name(), partitions, replicationFactor);
    }
}
