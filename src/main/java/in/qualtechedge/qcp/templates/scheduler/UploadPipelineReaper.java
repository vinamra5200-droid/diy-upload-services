package in.qualtechedge.qcp.templates.scheduler;

import in.qualtechedge.qcp.templates.entity.UploadAttempt;
import in.qualtechedge.qcp.templates.entity.UploadSubmission;
import in.qualtechedge.qcp.templates.enums.SubmissionStatus;
import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import in.qualtechedge.qcp.templates.mapper.UploadAttemptMapper;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.multitenancy.registry.Tenant;
import in.qualtechedge.qcp.templates.multitenancy.registry.TenantRepository;
import in.qualtechedge.qcp.templates.repository.UploadAttemptRepository;
import in.qualtechedge.qcp.templates.repository.UploadSubmissionRepository;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import in.qualtechedge.qcp.templates.service.UploadAttemptEventPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces upload-api-contract.md §7 notes 4–5: stale {@code ACCEPTED}/{@code VALIDATING}
 * attempts (a client that never called {@code /validate}, or validation-service that never
 * published completion) transition to {@code TIMED_OUT} once past their own frozen {@code
 * timeoutMinutes}; stale {@code WAITING_FOR_CHECKER} submissions transition to {@code EXPIRED}
 * once past {@code expiresAt}. Visits every active tenant explicitly, same as
 * {@link ConfigLockReaper} — a scheduled job has no request-scoped {@link HostContext}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UploadPipelineReaper {

    private final TenantRepository tenantRepository;
    private final UploadAttemptRepository uploadAttemptRepository;
    private final UploadSubmissionRepository uploadSubmissionRepository;
    private final ConfigLockService configLockService;
    private final UploadAttemptMapper uploadAttemptMapper;
    private final UploadAttemptEventPublisher uploadAttemptEventPublisher;

    @Scheduled(fixedDelayString = "${qcp.upload.pipeline-reaper-interval-ms:300000}")
    public void reapStalePipelineState() {
        for (Tenant tenant : tenantRepository.findAllByStatus(Tenant.STATUS_ACTIVE)) {
            HostContext.setCurrentTenant(tenant.getShortCode());
            try {
                reapAttempts();
                reapSubmissions();
            } finally {
                HostContext.clear();
            }
        }
    }

    @Transactional
    void reapAttempts() {
        List<UploadAttempt> candidates = uploadAttemptRepository.findByStatusIn(
                List.of(UploadAttemptStatus.ACCEPTED, UploadAttemptStatus.VALIDATING));
        OffsetDateTime now = OffsetDateTime.now();
        int reaped = 0;
        for (UploadAttempt attempt : candidates) {
            if (attempt.getCreatedAt().plusMinutes(attempt.getTimeoutMinutes()).isAfter(now)) {
                continue;
            }
            attempt.setStatus(UploadAttemptStatus.TIMED_OUT);
            UploadAttempt saved = uploadAttemptRepository.save(attempt);
            // Leaves ACCEPTED/VALIDATING via the timeout path, not the validation-completed
            // callback — still needs the same §2.2 "done" push to any open SSE connection.
            uploadAttemptEventPublisher.publish(uploadAttemptMapper.toResponse(saved));
            if (attempt.getBatchId() != null) {
                configLockService.release(attempt.getBatchId().toString());
            }
            reaped++;
        }
        if (reaped > 0) {
            log.warn("Upload pipeline reaper timed out {} stale upload attempt(s)", reaped);
        }
    }

    @Transactional
    void reapSubmissions() {
        List<UploadSubmission> candidates = uploadSubmissionRepository.findByStatus(SubmissionStatus.WAITING_FOR_CHECKER);
        OffsetDateTime now = OffsetDateTime.now();
        int reaped = 0;
        for (UploadSubmission submission : candidates) {
            if (submission.getExpiresAt().isAfter(now)) {
                continue;
            }
            submission.setStatus(SubmissionStatus.EXPIRED);
            uploadSubmissionRepository.save(submission);
            reaped++;
        }
        if (reaped > 0) {
            log.warn("Upload pipeline reaper expired {} stale submission(s)", reaped);
        }
    }
}
