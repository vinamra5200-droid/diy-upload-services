package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.UploadRoleRequest;
import in.qualtechedge.qcp.templates.dto.response.UploadRoleResponse;
import in.qualtechedge.qcp.templates.entity.UploadRole;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.UploadRoleMapper;
import in.qualtechedge.qcp.templates.repository.UploadRoleRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.UploadRoleService;
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
public class UploadRoleServiceImpl implements UploadRoleService {

    private final UploadRoleRepository uploadRoleRepository;
    private final UploadRoleMapper uploadRoleMapper;
    private final AuditEventService auditEventService;

    @Override
    @Transactional
    public UploadRoleResponse create(UploadRoleRequest request) {
        log.debug("Creating upload role: name={}", request.roleName());
        if (uploadRoleRepository.existsByRoleNameIgnoreCase(request.roleName())) {
            throw new ConflictException("An upload role named '" + request.roleName() + "' already exists");
        }
        String actorId = CurrentActor.id();
        UploadRole entity = uploadRoleMapper.toEntity(request, actorId);
        UploadRole saved = uploadRoleRepository.saveAndFlush(entity);
        auditEventService.record("ADMIN_ROLE_CREATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Upload role " + saved.getRoleId() + " created");
        return uploadRoleMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UploadRoleResponse getById(String roleId) {
        log.debug("Fetching upload role: id={}", roleId);
        return uploadRoleMapper.toResponse(findOrThrow(roleId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UploadRoleResponse> getAll() {
        log.debug("Listing upload roles");
        return uploadRoleRepository.findAll().stream().map(uploadRoleMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public UploadRoleResponse update(String roleId, UploadRoleRequest request) {
        log.debug("Updating upload role: id={}", roleId);
        UploadRole entity = findOrThrow(roleId);
        ConfigLifecycleGuard.assertEditable(entity.getStatus());
        if (!entity.getRoleName().equalsIgnoreCase(request.roleName())
                && uploadRoleRepository.existsByRoleNameIgnoreCaseAndRoleIdNot(request.roleName(), roleId)) {
            throw new ConflictException("An upload role named '" + request.roleName() + "' already exists");
        }
        uploadRoleMapper.updateEntity(entity, request);
        UploadRole saved = uploadRoleRepository.save(entity);
        String actorId = CurrentActor.id();
        auditEventService.record("ADMIN_ROLE_UPDATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Upload role " + roleId + " updated");
        return uploadRoleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UploadRoleResponse submit(String roleId) {
        log.debug("Submitting upload role: id={}", roleId);
        UploadRole entity = findOrThrow(roleId);
        ConfigLifecycleGuard.assertSubmittable(entity.getStatus());
        String actorId = CurrentActor.id();
        entity.setStatus(ConfigStatus.waitingForChecker);
        entity.setSubmittedBy(actorId);
        UploadRole saved = uploadRoleRepository.save(entity);
        auditEventService.record("ADMIN_ROLE_SUBMITTED", actorId, null, null,
                AuditOutcome.SUCCESS, "Upload role " + roleId + " submitted for review");
        return uploadRoleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UploadRoleResponse accept(String roleId) {
        log.debug("Accepting upload role: id={}", roleId);
        UploadRole entity = findOrThrow(roleId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.active);
        entity.setRejectionReason(null);
        UploadRole saved = uploadRoleRepository.save(entity);
        auditEventService.record("ADMIN_ROLE_ACTIVATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Upload role " + roleId + " activated");
        return uploadRoleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UploadRoleResponse reject(String roleId, RejectRequest request) {
        log.debug("Rejecting upload role: id={}", roleId);
        UploadRole entity = findOrThrow(roleId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.rejected);
        entity.setRejectionReason(request.reason());
        UploadRole saved = uploadRoleRepository.save(entity);
        auditEventService.record("ADMIN_ROLE_REJECTED", actorId, null, null,
                AuditOutcome.SUCCESS, "Upload role " + roleId + " rejected: " + request.reason());
        return uploadRoleMapper.toResponse(saved);
    }

    private UploadRole findOrThrow(String roleId) {
        return uploadRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload role not found with id: " + roleId));
    }
}
