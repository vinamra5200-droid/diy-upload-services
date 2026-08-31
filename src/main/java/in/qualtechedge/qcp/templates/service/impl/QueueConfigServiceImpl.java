package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.QueueConfigRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.QueueConfigResponse;
import in.qualtechedge.qcp.templates.entity.QueueConfig;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.QueueConfigMapper;
import in.qualtechedge.qcp.templates.repository.ApiConfigRepository;
import in.qualtechedge.qcp.templates.repository.QueueConfigRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.KafkaTopicAdminService;
import in.qualtechedge.qcp.templates.service.QueueConfigService;
import in.qualtechedge.qcp.templates.utils.ConfigLifecycleGuard;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs diy-upload-web's "Queue Orchestration" screen — named Kafka queue configurations
 * (producer settings, topic settings, an optional consumer-callback binding to an {@code
 * ApiConfig}), list+create+edit+maker-checker like {@code StorageConfigServiceImpl}/{@code
 * DatabaseConnectionServiceImpl}. The one addition over that shape: {@link #accept} also creates
 * {@code topicName} on the broker named by {@code topicBootstrapServers} (or the shared cluster,
 * if blank) via {@link KafkaTopicAdminService}, so an activated queue config and broker reality
 * stay in sync. If the topic already exists on the broker (e.g. a retried acceptance, or someone
 * pre-created it), that's treated as success, not a blocker — the goal is "topic exists with these
 * settings", not "this call must be the one that created it".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueueConfigServiceImpl implements QueueConfigService {

    private final QueueConfigRepository queueConfigRepository;
    private final ApiConfigRepository apiConfigRepository;
    private final QueueConfigMapper queueConfigMapper;
    private final KafkaTopicAdminService kafkaTopicAdminService;
    private final AuditEventService auditEventService;

    @Override
    @Transactional
    public QueueConfigResponse create(QueueConfigRequest request) {
        log.debug("Creating queue config: name={}", request.queueConfigName());
        assertNameAndTopicAvailable(request, null);
        assertApiConfigExists(request.apiConfigId());
        String actorId = CurrentActor.id();
        QueueConfig entity = queueConfigMapper.toEntity(request, actorId);
        QueueConfig saved = queueConfigRepository.saveAndFlush(entity);
        auditEventService.record("ADMIN_QUEUE_CREATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Queue config " + saved.getQueueConfigId() + " created");
        return queueConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public QueueConfigResponse getById(String configId) {
        log.debug("Fetching queue config: id={}", configId);
        return queueConfigMapper.toResponse(findOrThrow(configId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<QueueConfigResponse> getAll() {
        log.debug("Listing queue configs");
        return queueConfigRepository.findAll().stream().map(queueConfigMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public QueueConfigResponse update(String configId, QueueConfigRequest request) {
        log.debug("Updating queue config: id={}", configId);
        QueueConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertEditable(entity.getStatus());
        assertNameAndTopicAvailable(request, configId);
        assertApiConfigExists(request.apiConfigId());
        queueConfigMapper.updateEntity(entity, request);
        QueueConfig saved = queueConfigRepository.save(entity);
        auditEventService.record("ADMIN_QUEUE_UPDATED", CurrentActor.id(), null, null,
                AuditOutcome.SUCCESS, "Queue config " + configId + " updated");
        return queueConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public QueueConfigResponse submit(String configId) {
        log.debug("Submitting queue config: id={}", configId);
        QueueConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertSubmittable(entity.getStatus());
        assertTopicConfigured(entity);
        String actorId = CurrentActor.id();
        entity.setStatus(ConfigStatus.waitingForChecker);
        entity.setSubmittedBy(actorId);
        QueueConfig saved = queueConfigRepository.save(entity);
        auditEventService.record("ADMIN_QUEUE_SUBMITTED", actorId, null, null,
                AuditOutcome.SUCCESS, "Queue config " + configId + " submitted for review");
        return queueConfigMapper.toResponse(saved);
    }

    /**
     * topic_name is nullable until the Topic wizard step is filled in via Update (V1_4_10) — but
     * {@link #accept} passes it straight to the Kafka broker, so a draft that skipped that step
     * must not be submittable.
     */
    private void assertTopicConfigured(QueueConfig entity) {
        if (entity.getTopicName() == null || entity.getTopicName().isBlank()) {
            throw new ConflictException("Complete the Topic step before submitting this queue config for review");
        }
    }

    @Override
    @Transactional
    public QueueConfigResponse accept(String configId) {
        log.debug("Accepting queue config: id={}", configId);
        QueueConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);

        Map<String, String> topicConfigs = Map.of(
                "retention.ms", String.valueOf(entity.getTopicRetentionHours() * 3_600_000L),
                "cleanup.policy", entity.getTopicCleanupPolicy().name());
        try {
            kafkaTopicAdminService.createTopic(entity.getTopicBootstrapServers(), entity.getTopicName(),
                    entity.getTopicPartitions(), entity.getTopicReplicationFactor(), topicConfigs);
        } catch (ConflictException e) {
            log.warn("Topic {} already exists on the broker — activating queue config {} against it as-is",
                    entity.getTopicName(), configId);
        }

        entity.setStatus(ConfigStatus.active);
        entity.setRejectionReason(null);
        QueueConfig saved = queueConfigRepository.save(entity);
        auditEventService.record("ADMIN_QUEUE_ACTIVATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Queue config " + configId + " activated (topic " + entity.getTopicName() + " ensured on broker)");
        return queueConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public QueueConfigResponse reject(String configId, RejectRequest request) {
        log.debug("Rejecting queue config: id={}", configId);
        QueueConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.rejected);
        entity.setRejectionReason(request.reason());
        QueueConfig saved = queueConfigRepository.save(entity);
        auditEventService.record("ADMIN_QUEUE_REJECTED", actorId, null, null,
                AuditOutcome.SUCCESS, "Queue config " + configId + " rejected: " + request.reason());
        return queueConfigMapper.toResponse(saved);
    }

    private void assertNameAndTopicAvailable(QueueConfigRequest request, String excludingConfigId) {
        boolean nameTaken = excludingConfigId == null
                ? queueConfigRepository.existsByQueueConfigNameIgnoreCase(request.queueConfigName())
                : queueConfigRepository.existsByQueueConfigNameIgnoreCaseAndQueueConfigIdNot(request.queueConfigName(), excludingConfigId);
        if (nameTaken) {
            throw new ConflictException("A queue config named '" + request.queueConfigName() + "' already exists");
        }
        // topic is absent on Create (admin-api-contract.md §7.2) — nothing to check for uniqueness yet.
        if (request.topic() == null) {
            return;
        }
        String topicName = request.topic().topicName();
        boolean topicTaken = excludingConfigId == null
                ? queueConfigRepository.existsByTopicNameIgnoreCase(topicName)
                : queueConfigRepository.existsByTopicNameIgnoreCaseAndQueueConfigIdNot(topicName, excludingConfigId);
        if (topicTaken) {
            throw new ConflictException("A queue config for topic '" + topicName + "' already exists");
        }
    }

    private void assertApiConfigExists(String apiConfigId) {
        if (apiConfigId != null && !apiConfigId.isBlank() && !apiConfigRepository.existsById(apiConfigId)) {
            throw new ResourceNotFoundException("API config not found with id: " + apiConfigId);
        }
    }

    private QueueConfig findOrThrow(String configId) {
        return queueConfigRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue config not found with id: " + configId));
    }
}
