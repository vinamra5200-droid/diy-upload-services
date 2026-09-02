package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.ProcessRecordsSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadAttemptResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.UploadAttempt;
import in.qualtechedge.qcp.templates.entity.UploadJob;
import in.qualtechedge.qcp.templates.entity.UploadSubmission;
import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import in.qualtechedge.qcp.templates.enums.SubmissionStatus;
import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import in.qualtechedge.qcp.templates.exception.BusinessConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.UploadAttemptMapper;
import in.qualtechedge.qcp.templates.mapper.UploadJobMapper;
import in.qualtechedge.qcp.templates.mapper.UploadSubmissionMapper;
import in.qualtechedge.qcp.templates.repository.TemplateRepository;
import in.qualtechedge.qcp.templates.repository.UploadAttemptRepository;
import in.qualtechedge.qcp.templates.repository.UploadJobRepository;
import in.qualtechedge.qcp.templates.repository.UploadSubmissionRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.ViewerService;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ViewerServiceImpl implements ViewerService {

    private final UploadAttemptRepository uploadAttemptRepository;
    private final UploadSubmissionRepository uploadSubmissionRepository;
    private final UploadJobRepository uploadJobRepository;
    private final TemplateRepository templateRepository;
    private final UploadAttemptMapper uploadAttemptMapper;
    private final UploadSubmissionMapper uploadSubmissionMapper;
    private final UploadJobMapper uploadJobMapper;
    private final AuditEventService auditEventService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UploadAttemptResponse> listAttempts(List<UploadAttemptStatus> statuses, String processId,
            OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        Specification<UploadAttempt> spec = filterSpec(
                effective(statuses, UploadAttemptStatus.values()), processId, from, to, "processId");
        Page<UploadAttemptResponse> page = uploadAttemptRepository
                .findAll(spec, newestFirst(pageable))
                .map(uploadAttemptMapper::toResponse);
        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UploadSubmissionResponse> listSubmissions(List<SubmissionStatus> statuses, String processId,
            OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        Specification<UploadSubmission> spec = filterSpec(
                effective(statuses, SubmissionStatus.values()), processId, from, to, "processId");
        Page<UploadSubmissionResponse> page = uploadSubmissionRepository
                .findAll(spec, newestFirst(pageable))
                .map(this::toSubmissionResponse);
        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UploadJobResponse> listJobs(List<JobStatus> statuses, String processId,
            OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        Specification<in.qualtechedge.qcp.templates.entity.UploadJob> spec = filterSpec(
                effective(statuses, JobStatus.values()), processId, from, to, "processCode");
        Page<UploadJobResponse> page = uploadJobRepository
                .findAll(spec, newestFirst(pageable))
                .map(this::toJobResponse);
        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessRecordsSummaryResponse> getProcessSummary() {
        return uploadJobRepository.aggregateRecordsByProcess(JobStatus.COMPLETED, JobStatus.FAILED).stream()
                .map(row -> new ProcessRecordsSummaryResponse(
                        (String) row[0],
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue(),
                        ((Number) row[4]).longValue(),
                        ((Number) row[5]).longValue()))
                .toList();
    }

    // Matches CheckerServiceImpl#toResponse — upload_submissions has no template_id column of its
    // own, so it's resolved from the owning attempt on every row (bounded by page size).
    private UploadSubmissionResponse toSubmissionResponse(UploadSubmission submission) {
        String templateId = uploadAttemptRepository.findById(submission.getUploadAttemptId())
                .map(UploadAttempt::getTemplateId).orElse(null);
        return uploadSubmissionMapper.toResponse(submission, templateId);
    }

    // upload_jobs has no raw/validated key columns of its own (only completedFileKey) — resolved
    // from the owning attempt on every row (bounded by page size), same pattern as
    // toSubmissionResponse above, so the viewer dashboard's job list can offer every stage's
    // download without a per-row follow-up call.
    private UploadJobResponse toJobResponse(in.qualtechedge.qcp.templates.entity.UploadJob job) {
        UploadAttempt attempt = uploadAttemptRepository.findById(job.getUploadAttemptId()).orElse(null);
        String rawObjectKey = attempt != null ? attempt.getRawObjectKey() : null;
        String validatedObjectKey = attempt != null ? attempt.getValidatedObjectKey() : null;
        return uploadJobMapper.toResponse(job, rawObjectKey, validatedObjectKey);
    }

    /** Shared filter builder for all three viewer list endpoints — status-in, an optional exact
     * process match (field name differs per entity: {@code processId} on attempts/submissions,
     * {@code processCode} on jobs), and an optional createdAt window. */
    private <T, E> Specification<T> filterSpec(List<E> statuses, String processId, OffsetDateTime from,
            OffsetDateTime to, String processField) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(statuses));
            if (processId != null && !processId.isBlank()) {
                predicates.add(cb.equal(root.get(processField), processId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Pageable newestFirst(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private <E> List<E> effective(List<E> statuses, E[] allValues) {
        return (statuses == null || statuses.isEmpty()) ? List.of(allValues) : statuses;
    }

    // --- Admin-only manual overrides. Every current-status precondition below is deliberately
    // narrow — only "stuck" or already-failed statuses qualify, so an admin can never flip a
    // record that already moved on successfully (CONTINUED/ACCEPTED-submission/COMPLETED-job). ---

    private static final Set<UploadAttemptStatus> ATTEMPT_RETRYABLE =
            Set.of(UploadAttemptStatus.TIMED_OUT, UploadAttemptStatus.ABORTED);
    private static final Set<UploadAttemptStatus> ATTEMPT_REJECTABLE =
            Set.of(UploadAttemptStatus.READY_FOR_DECISION, UploadAttemptStatus.TIMED_OUT);
    private static final Set<JobStatus> JOB_REJECTABLE = Set.of(JobStatus.QUEUED, JobStatus.PROCESSING);

    @Override
    @Transactional
    public UploadAttemptResponse retryAttempt(String attemptId, String actorId) {
        UploadAttempt attempt = findAttemptOrThrow(attemptId);
        if (!ATTEMPT_RETRYABLE.contains(attempt.getStatus())) {
            throw new BusinessConflictException(
                    "Attempt " + attemptId + " cannot be retried from status " + attempt.getStatus());
        }
        attempt.setStatus(UploadAttemptStatus.ACCEPTED);
        UploadAttempt saved = uploadAttemptRepository.save(attempt);
        auditEventService.record(new PipelineAuditEventRequest(AuditEventCode.ADMIN_ATTEMPT_RETRIED, actorId, null,
                attempt.getProcessId(), attempt.getTemplateCode(), attempt.getTemplateVersion(), null, attemptId,
                null, null, AuditOutcome.SUCCESS, "Admin reset attempt " + attemptId + " to ACCEPTED for retry", null));
        return uploadAttemptMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UploadAttemptResponse rejectAttempt(String attemptId, String actorId) {
        UploadAttempt attempt = findAttemptOrThrow(attemptId);
        if (!ATTEMPT_REJECTABLE.contains(attempt.getStatus())) {
            throw new BusinessConflictException(
                    "Attempt " + attemptId + " cannot be rejected from status " + attempt.getStatus());
        }
        attempt.setStatus(UploadAttemptStatus.ABORTED);
        UploadAttempt saved = uploadAttemptRepository.save(attempt);
        auditEventService.record(new PipelineAuditEventRequest(AuditEventCode.ADMIN_ATTEMPT_REJECTED, actorId, null,
                attempt.getProcessId(), attempt.getTemplateCode(), attempt.getTemplateVersion(), null, attemptId,
                null, null, AuditOutcome.SUCCESS, "Admin aborted attempt " + attemptId, null));
        return uploadAttemptMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UploadSubmissionResponse retrySubmission(String submissionId, String actorId) {
        UploadSubmission submission = findSubmissionOrThrow(submissionId);
        if (submission.getStatus() != SubmissionStatus.EXPIRED) {
            throw new BusinessConflictException(
                    "Submission " + submissionId + " cannot be retried from status " + submission.getStatus());
        }
        // Reopening the review window needs the same SLA the submission was originally given —
        // resolved via the owning attempt's template, matching how it was set the first time
        // (UploadAttemptServiceImpl#createSubmission).
        UploadAttempt attempt = findAttemptOrThrow(submission.getUploadAttemptId());
        Template template = templateRepository.findById(attempt.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + attempt.getTemplateId()));
        submission.setStatus(SubmissionStatus.WAITING_FOR_CHECKER);
        submission.setExpiresAt(OffsetDateTime.now().plusHours(template.getMakerCheckerSlaHours()));
        UploadSubmission saved = uploadSubmissionRepository.save(submission);
        auditEventService.record(new PipelineAuditEventRequest(AuditEventCode.ADMIN_SUBMISSION_RETRIED, actorId, null,
                submission.getProcessId(), submission.getTemplateCode(), submission.getTemplateVersion(), null,
                submission.getUploadAttemptId(), submissionId, null, AuditOutcome.SUCCESS,
                "Admin reset submission " + submissionId + " to WAITING_FOR_CHECKER for retry", null));
        return toSubmissionResponse(saved);
    }

    @Override
    @Transactional
    public UploadSubmissionResponse rejectSubmission(String submissionId, String actorId) {
        UploadSubmission submission = findSubmissionOrThrow(submissionId);
        if (submission.getStatus() != SubmissionStatus.WAITING_FOR_CHECKER) {
            throw new BusinessConflictException(
                    "Submission " + submissionId + " cannot be expired from status " + submission.getStatus());
        }
        submission.setStatus(SubmissionStatus.EXPIRED);
        UploadSubmission saved = uploadSubmissionRepository.save(submission);
        auditEventService.record(new PipelineAuditEventRequest(AuditEventCode.ADMIN_SUBMISSION_EXPIRED, actorId, null,
                submission.getProcessId(), submission.getTemplateCode(), submission.getTemplateVersion(), null,
                submission.getUploadAttemptId(), submissionId, null, AuditOutcome.SUCCESS,
                "Admin expired submission " + submissionId, null));
        return toSubmissionResponse(saved);
    }

    @Override
    @Transactional
    public UploadJobResponse retryJob(String jobId, String actorId) {
        UploadJob job = findJobOrThrow(jobId);
        if (job.getStatus() != JobStatus.FAILED) {
            throw new BusinessConflictException("Job " + jobId + " cannot be retried from status " + job.getStatus());
        }
        job.setStatus(JobStatus.QUEUED);
        UploadJob saved = uploadJobRepository.save(job);
        auditEventService.record(new PipelineAuditEventRequest(AuditEventCode.ADMIN_JOB_RETRIED, actorId, null,
                job.getProcessCode(), job.getTemplateCode(), job.getTemplateVersion(), null, job.getUploadAttemptId(),
                job.getSubmissionId(), jobId, AuditOutcome.SUCCESS, "Admin reset job " + jobId + " to QUEUED for retry", null));
        return uploadJobMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UploadJobResponse rejectJob(String jobId, String actorId) {
        UploadJob job = findJobOrThrow(jobId);
        if (!JOB_REJECTABLE.contains(job.getStatus())) {
            throw new BusinessConflictException(
                    "Job " + jobId + " cannot be marked failed from status " + job.getStatus());
        }
        job.setStatus(JobStatus.FAILED);
        UploadJob saved = uploadJobRepository.save(job);
        auditEventService.record(new PipelineAuditEventRequest(AuditEventCode.ADMIN_JOB_REJECTED, actorId, null,
                job.getProcessCode(), job.getTemplateCode(), job.getTemplateVersion(), null, job.getUploadAttemptId(),
                job.getSubmissionId(), jobId, AuditOutcome.SUCCESS, "Admin marked job " + jobId + " FAILED", null));
        return uploadJobMapper.toResponse(saved);
    }

    private UploadAttempt findAttemptOrThrow(String attemptId) {
        return uploadAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload attempt not found with id: " + attemptId));
    }

    private UploadSubmission findSubmissionOrThrow(String submissionId) {
        return uploadSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));
    }

    private UploadJob findJobOrThrow(String jobId) {
        return uploadJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
    }
}
