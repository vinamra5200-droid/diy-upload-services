package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.DatabaseConnectionRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.DatabaseConnectionResponse;
import in.qualtechedge.qcp.templates.entity.DatabaseConnection;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.DatabaseConnectionMapper;
import in.qualtechedge.qcp.templates.repository.DatabaseConnectionRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.DatabaseConnectionService;
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
public class DatabaseConnectionServiceImpl implements DatabaseConnectionService {

    private final DatabaseConnectionRepository databaseConnectionRepository;
    private final DatabaseConnectionMapper databaseConnectionMapper;
    private final AuditEventService auditEventService;

    @Override
    @Transactional
    public DatabaseConnectionResponse create(DatabaseConnectionRequest request) {
        log.debug("Creating database connection: label={}", request.connectionLabel());
        if (databaseConnectionRepository.existsByConnectionLabelIgnoreCase(request.connectionLabel())) {
            throw new ConflictException("A database connection labeled '" + request.connectionLabel() + "' already exists");
        }
        String actorId = CurrentActor.id();
        DatabaseConnection entity = databaseConnectionMapper.toEntity(request, actorId);
        DatabaseConnection saved = databaseConnectionRepository.saveAndFlush(entity);
        auditEventService.record("ADMIN_DATABASE_CREATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Database connection " + saved.getConnectionId() + " created");
        return databaseConnectionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DatabaseConnectionResponse getById(String connectionId) {
        log.debug("Fetching database connection: id={}", connectionId);
        return databaseConnectionMapper.toResponse(findOrThrow(connectionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DatabaseConnectionResponse> getAll() {
        log.debug("Listing database connections");
        return databaseConnectionRepository.findAll().stream().map(databaseConnectionMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public DatabaseConnectionResponse update(String connectionId, DatabaseConnectionRequest request) {
        log.debug("Updating database connection: id={}", connectionId);
        DatabaseConnection entity = findOrThrow(connectionId);
        ConfigLifecycleGuard.assertEditable(entity.getStatus());
        if (!entity.getConnectionLabel().equalsIgnoreCase(request.connectionLabel())
                && databaseConnectionRepository.existsByConnectionLabelIgnoreCaseAndConnectionIdNot(request.connectionLabel(), connectionId)) {
            throw new ConflictException("A database connection labeled '" + request.connectionLabel() + "' already exists");
        }
        String actorId = CurrentActor.id();
        databaseConnectionMapper.updateEntity(entity, request, actorId);
        DatabaseConnection saved = databaseConnectionRepository.save(entity);
        auditEventService.record("ADMIN_DATABASE_UPDATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Database connection " + connectionId + " updated");
        return databaseConnectionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DatabaseConnectionResponse submit(String connectionId) {
        log.debug("Submitting database connection: id={}", connectionId);
        DatabaseConnection entity = findOrThrow(connectionId);
        ConfigLifecycleGuard.assertSubmittable(entity.getStatus());
        String actorId = CurrentActor.id();
        entity.setStatus(ConfigStatus.waitingForChecker);
        entity.setSubmittedBy(actorId);
        DatabaseConnection saved = databaseConnectionRepository.save(entity);
        auditEventService.record("ADMIN_DATABASE_SUBMITTED", actorId, null, null,
                AuditOutcome.SUCCESS, "Database connection " + connectionId + " submitted for review");
        return databaseConnectionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DatabaseConnectionResponse accept(String connectionId) {
        log.debug("Accepting database connection: id={}", connectionId);
        DatabaseConnection entity = findOrThrow(connectionId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.active);
        entity.setRejectionReason(null);
        DatabaseConnection saved = databaseConnectionRepository.save(entity);
        auditEventService.record("ADMIN_DATABASE_ACTIVATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Database connection " + connectionId + " activated");
        return databaseConnectionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DatabaseConnectionResponse reject(String connectionId, RejectRequest request) {
        log.debug("Rejecting database connection: id={}", connectionId);
        DatabaseConnection entity = findOrThrow(connectionId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.rejected);
        entity.setRejectionReason(request.reason());
        DatabaseConnection saved = databaseConnectionRepository.save(entity);
        auditEventService.record("ADMIN_DATABASE_REJECTED", actorId, null, null,
                AuditOutcome.SUCCESS, "Database connection " + connectionId + " rejected: " + request.reason());
        return databaseConnectionMapper.toResponse(saved);
    }

    private DatabaseConnection findOrThrow(String connectionId) {
        return databaseConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Database connection not found with id: " + connectionId));
    }
}
