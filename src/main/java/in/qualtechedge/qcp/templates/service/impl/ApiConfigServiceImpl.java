package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.ApiConfigRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.ApiConfigResponse;
import in.qualtechedge.qcp.templates.entity.ApiConfig;
import in.qualtechedge.qcp.templates.entity.QueueConfig;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.ApiConfigMapper;
import in.qualtechedge.qcp.templates.repository.ApiConfigRepository;
import in.qualtechedge.qcp.templates.repository.QueueConfigRepository;
import in.qualtechedge.qcp.templates.service.ApiConfigService;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.QueueConfigEventPublisher;
import in.qualtechedge.qcp.templates.utils.ConfigLifecycleGuard;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiConfigServiceImpl implements ApiConfigService {

    private final ApiConfigRepository apiConfigRepository;
    private final QueueConfigRepository queueConfigRepository;
    private final ApiConfigMapper apiConfigMapper;
    private final AuditEventService auditEventService;
    private final QueueConfigEventPublisher queueConfigEventPublisher;

    @Override
    @Transactional
    public ApiConfigResponse create(ApiConfigRequest request) {
        log.debug("Creating API config: label={}", request.label());
        if (apiConfigRepository.existsByLabelIgnoreCase(request.label())) {
            throw new ConflictException("An API config labeled '" + request.label() + "' already exists");
        }
        String actorId = CurrentActor.id();
        ApiConfig entity = apiConfigMapper.toEntity(request, actorId);
        ApiConfig saved = apiConfigRepository.saveAndFlush(entity);
        auditEventService.record("ADMIN_API_CONFIG_CREATED", actorId, null, null,
                AuditOutcome.SUCCESS, "API config " + saved.getConfigId() + " created");
        return apiConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiConfigResponse getById(String configId) {
        log.debug("Fetching API config: id={}", configId);
        return apiConfigMapper.toResponse(findOrThrow(configId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiConfigResponse> getAll() {
        log.debug("Listing API configs");
        return apiConfigRepository.findAll().stream().map(apiConfigMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ApiConfigResponse update(String configId, ApiConfigRequest request) {
        log.debug("Updating API config: id={}", configId);
        ApiConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertEditable(entity.getStatus());
        if (!entity.getLabel().equalsIgnoreCase(request.label())
                && apiConfigRepository.existsByLabelIgnoreCaseAndConfigIdNot(request.label(), configId)) {
            throw new ConflictException("An API config labeled '" + request.label() + "' already exists");
        }
        String actorId = CurrentActor.id();
        apiConfigMapper.updateEntity(entity, request, actorId);
        ApiConfig saved = apiConfigRepository.save(entity);
        auditEventService.record("ADMIN_API_CONFIG_UPDATED", actorId, null, null,
                AuditOutcome.SUCCESS, "API config " + configId + " updated");
        // This API config's method/uri/headers/body are embedded in every active queue config's
        // queue-config-topic event (QueueConfigEvent.apiConfig) — an edit here is invisible to a
        // subscriber's cache until each of those queue configs is re-published.
        for (QueueConfig queueConfig : queueConfigRepository.findAllByApiConfigIdAndStatus(configId, ConfigStatus.active)) {
            queueConfigEventPublisher.publish(queueConfig);
        }
        return apiConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ApiConfigResponse submit(String configId) {
        log.debug("Submitting API config: id={}", configId);
        ApiConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertSubmittable(entity.getStatus());
        String actorId = CurrentActor.id();
        entity.setStatus(ConfigStatus.waitingForChecker);
        entity.setSubmittedBy(actorId);
        ApiConfig saved = apiConfigRepository.save(entity);
        auditEventService.record("ADMIN_API_CONFIG_SUBMITTED", actorId, null, null,
                AuditOutcome.SUCCESS, "API config " + configId + " submitted for review");
        return apiConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ApiConfigResponse accept(String configId) {
        log.debug("Accepting API config: id={}", configId);
        ApiConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.active);
        entity.setRejectionReason(null);
        ApiConfig saved = apiConfigRepository.save(entity);
        auditEventService.record("ADMIN_API_CONFIG_ACTIVATED", actorId, null, null,
                AuditOutcome.SUCCESS, "API config " + configId + " activated");
        return apiConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ApiConfigResponse reject(String configId, RejectRequest request) {
        log.debug("Rejecting API config: id={}", configId);
        ApiConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.rejected);
        entity.setRejectionReason(request.reason());
        ApiConfig saved = apiConfigRepository.save(entity);
        auditEventService.record("ADMIN_API_CONFIG_REJECTED", actorId, null, null,
                AuditOutcome.SUCCESS, "API config " + configId + " rejected: " + request.reason());
        return apiConfigMapper.toResponse(saved);
    }

    private ApiConfig findOrThrow(String configId) {
        return apiConfigRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("API config not found with id: " + configId));
    }
}
