package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.StorageConfigRequest;
import in.qualtechedge.qcp.templates.dto.response.StorageConfigResponse;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.StorageConfigMapper;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.StorageConfigService;
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
public class StorageConfigServiceImpl implements StorageConfigService {

    private final StorageConfigRepository storageConfigRepository;
    private final StorageConfigMapper storageConfigMapper;
    private final AuditEventService auditEventService;

    @Override
    @Transactional
    public StorageConfigResponse create(StorageConfigRequest request) {
        log.debug("Creating storage config: label={}", request.connectionLabel());
        if (storageConfigRepository.existsByConnectionLabelIgnoreCase(request.connectionLabel())) {
            throw new ConflictException("A storage connection labeled '" + request.connectionLabel() + "' already exists");
        }
        String actorId = CurrentActor.id();
        StorageConfig entity = storageConfigMapper.toEntity(request, actorId);
        StorageConfig saved = storageConfigRepository.saveAndFlush(entity);
        auditEventService.record("ADMIN_STORAGE_CREATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Storage connection " + saved.getConfigId() + " created");
        return storageConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public StorageConfigResponse getById(String configId) {
        log.debug("Fetching storage config: id={}", configId);
        return storageConfigMapper.toResponse(findOrThrow(configId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StorageConfigResponse> getAll() {
        log.debug("Listing storage configs");
        return storageConfigRepository.findAll().stream().map(storageConfigMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public StorageConfigResponse update(String configId, StorageConfigRequest request) {
        log.debug("Updating storage config: id={}", configId);
        StorageConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertEditable(entity.getStatus());
        if (!entity.getConnectionLabel().equalsIgnoreCase(request.connectionLabel())
                && storageConfigRepository.existsByConnectionLabelIgnoreCaseAndConfigIdNot(request.connectionLabel(), configId)) {
            throw new ConflictException("A storage connection labeled '" + request.connectionLabel() + "' already exists");
        }
        String actorId = CurrentActor.id();
        storageConfigMapper.updateEntity(entity, request, actorId);
        StorageConfig saved = storageConfigRepository.save(entity);
        auditEventService.record("ADMIN_STORAGE_UPDATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Storage connection " + configId + " updated");
        return storageConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public StorageConfigResponse submit(String configId) {
        log.debug("Submitting storage config: id={}", configId);
        StorageConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertSubmittable(entity.getStatus());
        String actorId = CurrentActor.id();
        entity.setStatus(ConfigStatus.waitingForChecker);
        entity.setSubmittedBy(actorId);
        StorageConfig saved = storageConfigRepository.save(entity);
        auditEventService.record("ADMIN_STORAGE_SUBMITTED", actorId, null, null,
                AuditOutcome.SUCCESS, "Storage connection " + configId + " submitted for review");
        return storageConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public StorageConfigResponse accept(String configId) {
        log.debug("Accepting storage config: id={}", configId);
        StorageConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.active);
        entity.setRejectionReason(null);
        StorageConfig saved = storageConfigRepository.save(entity);
        auditEventService.record("ADMIN_STORAGE_ACTIVATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Storage connection " + configId + " activated");
        return storageConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public StorageConfigResponse reject(String configId, RejectRequest request) {
        log.debug("Rejecting storage config: id={}", configId);
        StorageConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.rejected);
        entity.setRejectionReason(request.reason());
        StorageConfig saved = storageConfigRepository.save(entity);
        auditEventService.record("ADMIN_STORAGE_REJECTED", actorId, null, null,
                AuditOutcome.SUCCESS, "Storage connection " + configId + " rejected: " + request.reason());
        return storageConfigMapper.toResponse(saved);
    }

    private StorageConfig findOrThrow(String configId) {
        return storageConfigRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("Storage connection not found with id: " + configId));
    }
}
