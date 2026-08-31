package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.CheckerInboxItemResponse;
import in.qualtechedge.qcp.templates.entity.CheckerInboxItem;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.CheckerInboxMapper;
import in.qualtechedge.qcp.templates.repository.CheckerInboxRepository;
import in.qualtechedge.qcp.templates.service.ApiConfigService;
import in.qualtechedge.qcp.templates.service.DatabaseConnectionService;
import in.qualtechedge.qcp.templates.service.InboxService;
import in.qualtechedge.qcp.templates.service.MakerUserService;
import in.qualtechedge.qcp.templates.service.ProcessService;
import in.qualtechedge.qcp.templates.service.QueueConfigService;
import in.qualtechedge.qcp.templates.service.StorageConfigService;
import in.qualtechedge.qcp.templates.service.TemplateService;
import in.qualtechedge.qcp.templates.service.UploadRoleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatches Checker Inbox accept/reject to the matching resource service, resolved from the
 * {@code v_checker_inbox} view row's {@code entityType}/{@code entityId} rather than parsing the
 * {@code changeId} string — entity ids themselves contain hyphens, so a fresh lookup is more
 * robust than splitting the {@code chg-<type>-<id>} prefix.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboxServiceImpl implements InboxService {

    private final CheckerInboxRepository checkerInboxRepository;
    private final CheckerInboxMapper checkerInboxMapper;
    private final ProcessService processService;
    private final TemplateService templateService;
    private final UploadRoleService uploadRoleService;
    private final MakerUserService makerUserService;
    private final StorageConfigService storageConfigService;
    private final DatabaseConnectionService databaseConnectionService;
    private final ApiConfigService apiConfigService;
    private final QueueConfigService queueConfigService;

    @Override
    @Transactional(readOnly = true)
    public List<CheckerInboxItemResponse> list(String actorId) {
        log.debug("Listing checker inbox: actorId={}", actorId);
        return checkerInboxRepository.findBySubmittedByNot(actorId).stream()
                .map(checkerInboxMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public Object accept(String changeId) {
        log.debug("Accepting inbox item: changeId={}", changeId);
        CheckerInboxItem item = findOrThrow(changeId);
        return switch (item.getEntityType()) {
            case process -> processService.accept(item.getEntityId());
            case template -> templateService.accept(item.getEntityId());
            case role -> uploadRoleService.accept(item.getEntityId());
            case user -> makerUserService.accept(item.getEntityId());
            case storage -> storageConfigService.accept(item.getEntityId());
            case database -> databaseConnectionService.accept(item.getEntityId());
            case apiConfig -> apiConfigService.accept(item.getEntityId());
            case queueConfig -> queueConfigService.accept(item.getEntityId());
        };
    }

    @Override
    @Transactional
    public Object reject(String changeId, RejectRequest request) {
        log.debug("Rejecting inbox item: changeId={}", changeId);
        CheckerInboxItem item = findOrThrow(changeId);
        return switch (item.getEntityType()) {
            case process -> processService.reject(item.getEntityId(), request);
            case template -> templateService.reject(item.getEntityId(), request);
            case role -> uploadRoleService.reject(item.getEntityId(), request);
            case user -> makerUserService.reject(item.getEntityId(), request);
            case storage -> storageConfigService.reject(item.getEntityId(), request);
            case database -> databaseConnectionService.reject(item.getEntityId(), request);
            case apiConfig -> apiConfigService.reject(item.getEntityId(), request);
            case queueConfig -> queueConfigService.reject(item.getEntityId(), request);
        };
    }

    private CheckerInboxItem findOrThrow(String changeId) {
        return checkerInboxRepository.findById(changeId)
                .orElseThrow(() -> new ResourceNotFoundException("Inbox item not found or no longer pending: " + changeId));
    }
}
