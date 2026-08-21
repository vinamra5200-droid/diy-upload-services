package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.ProcessRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.ProcessResponse;
import in.qualtechedge.qcp.templates.entity.UploadProcess;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.exception.ConfigLockedException;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.ProcessMapper;
import in.qualtechedge.qcp.templates.repository.UploadProcessRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import in.qualtechedge.qcp.templates.service.ProcessService;
import in.qualtechedge.qcp.templates.utils.ConfigLifecycleGuard;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessServiceImpl implements ProcessService {

    private final UploadProcessRepository uploadProcessRepository;
    private final ProcessMapper processMapper;
    private final AuditEventService auditEventService;
    private final ConfigLockService configLockService;

    @Override
    @Transactional
    public ProcessResponse create(ProcessRequest request) {
        log.debug("Creating process: name={}", request.processName());
        if (uploadProcessRepository.existsByProcessNameIgnoreCase(request.processName())) {
            throw new ConflictException("A process named '" + request.processName() + "' already exists");
        }
        String actorId = CurrentActor.id();
        UploadProcess entity = processMapper.toEntity(request, actorId);
        UploadProcess saved = uploadProcessRepository.saveAndFlush(entity);
        auditEventService.record("ADMIN_PROCESS_CREATED", actorId, saved.getProcessId(), null,
                AuditOutcome.SUCCESS, "Process " + saved.getProcessId() + " created");
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessResponse getById(String processId) {
        log.debug("Fetching process: id={}", processId);
        return toResponse(findOrThrow(processId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProcessResponse> list(ConfigStatus status, String search, int page, int limit) {
        log.debug("Listing processes: status={}, search={}", status, search);
        Specification<UploadProcess> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("processId")), like),
                        cb.like(cb.lower(root.get("processName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        int size = Math.min(Math.max(limit, 1), 100);
        int zeroBasedPage = Math.max(page - 1, 0);
        Page<UploadProcess> result = uploadProcessRepository.findAll(spec,
                PageRequest.of(zeroBasedPage, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Override
    @Transactional
    public ProcessResponse update(String processId, ProcessRequest request) {
        log.debug("Updating process: id={}", processId);
        UploadProcess entity = findOrThrow(processId);
        assertNotLocked(entity);
        ConfigLifecycleGuard.assertEditable(entity.getStatus());
        if (!entity.getProcessName().equalsIgnoreCase(request.processName())
                && uploadProcessRepository.existsByProcessNameIgnoreCaseAndProcessIdNot(request.processName(), processId)) {
            throw new ConflictException("A process named '" + request.processName() + "' already exists");
        }
        processMapper.updateEntity(entity, request);
        UploadProcess saved = uploadProcessRepository.save(entity);
        String actorId = CurrentActor.id();
        auditEventService.record("ADMIN_PROCESS_UPDATED", actorId, processId, null,
                AuditOutcome.SUCCESS, "Process " + processId + " updated");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProcessResponse submit(String processId) {
        log.debug("Submitting process: id={}", processId);
        UploadProcess entity = findOrThrow(processId);
        assertNotLocked(entity);
        ConfigLifecycleGuard.assertSubmittable(entity.getStatus());
        String actorId = CurrentActor.id();
        entity.setStatus(ConfigStatus.waitingForChecker);
        entity.setSubmittedBy(actorId);
        UploadProcess saved = uploadProcessRepository.save(entity);
        auditEventService.record("ADMIN_PROCESS_SUBMITTED", actorId, processId, null,
                AuditOutcome.SUCCESS, "Process " + processId + " submitted for review");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProcessResponse accept(String processId) {
        log.debug("Accepting process: id={}", processId);
        UploadProcess entity = findOrThrow(processId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.active);
        entity.setRejectionReason(null);
        UploadProcess saved = uploadProcessRepository.save(entity);
        auditEventService.record("ADMIN_PROCESS_ACTIVATED", actorId, processId, null,
                AuditOutcome.SUCCESS, "Process " + processId + " activated");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProcessResponse reject(String processId, RejectRequest request) {
        log.debug("Rejecting process: id={}", processId);
        UploadProcess entity = findOrThrow(processId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.rejected);
        entity.setRejectionReason(request.reason());
        UploadProcess saved = uploadProcessRepository.save(entity);
        auditEventService.record("ADMIN_PROCESS_REJECTED", actorId, processId, null,
                AuditOutcome.SUCCESS, "Process " + processId + " rejected: " + request.reason());
        return toResponse(saved);
    }

    private ProcessResponse toResponse(UploadProcess entity) {
        return processMapper.toResponse(entity, configLockService.isLocked(entity.getProcessId()));
    }

    private void assertNotLocked(UploadProcess entity) {
        if (configLockService.isLocked(entity.getProcessId())) {
            throw new ConfigLockedException("Config locked for process " + entity.getProcessId());
        }
    }

    private UploadProcess findOrThrow(String processId) {
        return uploadProcessRepository.findById(processId)
                .orElseThrow(() -> new ResourceNotFoundException("Process not found with id: " + processId));
    }
}
