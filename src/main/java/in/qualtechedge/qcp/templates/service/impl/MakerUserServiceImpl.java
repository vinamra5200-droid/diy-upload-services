package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.MakerUserRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.MakerUserResponse;
import in.qualtechedge.qcp.templates.entity.MakerUser;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.MakerUserMapper;
import in.qualtechedge.qcp.templates.repository.MakerUserRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.MakerUserService;
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
public class MakerUserServiceImpl implements MakerUserService {

    private final MakerUserRepository makerUserRepository;
    private final MakerUserMapper makerUserMapper;
    private final AuditEventService auditEventService;

    @Override
    @Transactional
    public MakerUserResponse create(MakerUserRequest request) {
        log.debug("Creating maker user: username={}", request.username());
        if (makerUserRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException("A maker user named '" + request.username() + "' already exists");
        }
        String actorId = CurrentActor.id();
        MakerUser entity = makerUserMapper.toEntity(request, actorId);
        MakerUser saved = makerUserRepository.saveAndFlush(entity);
        auditEventService.record("ADMIN_USER_CREATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Maker user " + saved.getUserId() + " created");
        return makerUserMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public MakerUserResponse getById(String userId) {
        log.debug("Fetching maker user: id={}", userId);
        return makerUserMapper.toResponse(findOrThrow(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MakerUserResponse> getAll() {
        log.debug("Listing maker users");
        return makerUserRepository.findAll().stream().map(makerUserMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public MakerUserResponse update(String userId, MakerUserRequest request) {
        log.debug("Updating maker user: id={}", userId);
        MakerUser entity = findOrThrow(userId);
        ConfigLifecycleGuard.assertEditable(entity.getStatus());
        if (!entity.getUsername().equalsIgnoreCase(request.username())
                && makerUserRepository.existsByUsernameIgnoreCaseAndUserIdNot(request.username(), userId)) {
            throw new ConflictException("A maker user named '" + request.username() + "' already exists");
        }
        makerUserMapper.updateEntity(entity, request);
        MakerUser saved = makerUserRepository.save(entity);
        String actorId = CurrentActor.id();
        auditEventService.record("ADMIN_USER_UPDATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Maker user " + userId + " updated");
        return makerUserMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MakerUserResponse submit(String userId) {
        log.debug("Submitting maker user: id={}", userId);
        MakerUser entity = findOrThrow(userId);
        ConfigLifecycleGuard.assertSubmittable(entity.getStatus());
        String actorId = CurrentActor.id();
        entity.setStatus(ConfigStatus.waitingForChecker);
        entity.setSubmittedBy(actorId);
        MakerUser saved = makerUserRepository.save(entity);
        auditEventService.record("ADMIN_USER_SUBMITTED", actorId, null, null,
                AuditOutcome.SUCCESS, "Maker user " + userId + " submitted for review");
        return makerUserMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MakerUserResponse accept(String userId) {
        log.debug("Accepting maker user: id={}", userId);
        MakerUser entity = findOrThrow(userId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.active);
        entity.setRejectionReason(null);
        MakerUser saved = makerUserRepository.save(entity);
        auditEventService.record("ADMIN_USER_ACTIVATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Maker user " + userId + " activated");
        return makerUserMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MakerUserResponse reject(String userId, RejectRequest request) {
        log.debug("Rejecting maker user: id={}", userId);
        MakerUser entity = findOrThrow(userId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.rejected);
        entity.setRejectionReason(request.reason());
        MakerUser saved = makerUserRepository.save(entity);
        auditEventService.record("ADMIN_USER_REJECTED", actorId, null, null,
                AuditOutcome.SUCCESS, "Maker user " + userId + " rejected: " + request.reason());
        return makerUserMapper.toResponse(saved);
    }

    private MakerUser findOrThrow(String userId) {
        return makerUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Maker user not found with id: " + userId));
    }
}
