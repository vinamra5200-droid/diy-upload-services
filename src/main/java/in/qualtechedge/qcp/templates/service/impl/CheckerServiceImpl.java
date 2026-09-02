package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.ValidationServiceRowsResponse;
import in.qualtechedge.qcp.templates.dto.response.AcceptSubmissionResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationRowResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationSummaryResponse;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.UploadAttempt;
import in.qualtechedge.qcp.templates.entity.UploadJob;
import in.qualtechedge.qcp.templates.entity.UploadSubmission;
import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import in.qualtechedge.qcp.templates.enums.SubmissionStatus;
import in.qualtechedge.qcp.templates.exception.ActorNeSubmitterException;
import in.qualtechedge.qcp.templates.exception.BusinessConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.exception.SubmissionExpiredException;
import in.qualtechedge.qcp.templates.mapper.UploadJobMapper;
import in.qualtechedge.qcp.templates.mapper.UploadSubmissionMapper;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.repository.TemplateRepository;
import in.qualtechedge.qcp.templates.repository.UploadAttemptRepository;
import in.qualtechedge.qcp.templates.repository.UploadJobRepository;
import in.qualtechedge.qcp.templates.repository.UploadSubmissionRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.CheckerService;
import in.qualtechedge.qcp.templates.service.ValidationServiceResultsClient;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import in.qualtechedge.qcp.templates.utils.S3ClientFactory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckerServiceImpl implements CheckerService {

    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final UploadSubmissionRepository uploadSubmissionRepository;
    private final UploadAttemptRepository uploadAttemptRepository;
    private final UploadJobRepository uploadJobRepository;
    private final TemplateRepository templateRepository;
    private final StorageConfigRepository storageConfigRepository;
    private final UploadSubmissionMapper uploadSubmissionMapper;
    private final UploadJobMapper uploadJobMapper;
    private final AuditEventService auditEventService;
    private final ValidationServiceResultsClient validationServiceResultsClient;

    @Override
    @Transactional(readOnly = true)
    public List<UploadSubmissionResponse> inbox(String checkerId) {
        return uploadSubmissionRepository
                .findByStatusAndMakerUserIdNotOrderByCreatedAtDesc(SubmissionStatus.WAITING_FOR_CHECKER, checkerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UploadSubmissionResponse get(String submissionId) {
        return toResponse(findOrThrow(submissionId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ValidationRowResponse> getRows(String submissionId, String rowStatus, List<String> ruleTypes,
            String search, Pageable pageable) {
        UploadSubmission submission = findOrThrow(submissionId);
        UploadAttempt attempt = uploadAttemptRepository.findById(submission.getUploadAttemptId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Upload attempt not found with id: " + submission.getUploadAttemptId()));
        if (attempt.getBatchId() == null) {
            throw new ResourceNotFoundException("Submission " + submissionId + " has no validated batch");
        }
        ValidationServiceRowsResponse.Data data = validationServiceResultsClient.fetchRowsPage(
                attempt.getBatchId(), HostContext.getCurrentTenant(), rowStatus, ruleTypes, search,
                pageable.getPageNumber(), pageable.getPageSize());
        List<ValidationRowResponse> content = data.content().stream()
                .map(row -> new ValidationRowResponse(row.rowNumber(), row.rowData(), row.errors(), row.rowStatus()))
                .toList();
        PageResponse.PageMeta meta = new PageResponse.PageMeta(data.page().number(), data.page().size(),
                data.page().totalElements(), data.page().totalPages());
        return new PageResponse<>(content, meta);
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedDownloadResponse download(String submissionId, String checkerId) {
        UploadSubmission submission = findOrThrow(submissionId);
        assertNotOwnSubmission(submission, checkerId);
        StorageConfig config = activeStorageConfig();
        PresignedGetObjectRequest presigned = S3ClientFactory.presign(config, submission.getPendingObjectKey(), DOWNLOAD_URL_EXPIRY);
        return new PresignedDownloadResponse(presigned.url().toString(), OffsetDateTime.now().plus(DOWNLOAD_URL_EXPIRY));
    }

    @Override
    @Transactional
    public AcceptSubmissionResponse accept(String submissionId, String checkerId) {
        log.debug("Accepting submission: submissionId={}, checkerId={}", submissionId, checkerId);
        UploadSubmission submission = findOrThrow(submissionId);
        assertWaitingForChecker(submission);
        assertNotOwnSubmission(submission, checkerId);
        assertNotExpired(submission);

        UploadAttempt attempt = uploadAttemptRepository.findById(submission.getUploadAttemptId())
                .orElseThrow(() -> new ResourceNotFoundException("Upload attempt not found with id: " + submission.getUploadAttemptId()));
        if (!templateRepository.existsById(attempt.getTemplateId())) {
            throw new ResourceNotFoundException("Template not found with id: " + attempt.getTemplateId());
        }

        // Already the passed-rows-only dispatch file — UploadAttemptServiceImpl#createSubmission
        // builds it once, at proceed() time, specifically so accepting a submission never has to
        // rebuild it (or risk a checker approving rows that failed validation for dispatch).
        String dispatchKey = submission.getSourceObjectKey();
        ValidationSummaryResponse summary = JsonColumnMapper.read(submission.getSummary(), ValidationSummaryResponse.class);
        int passedCount = summary == null ? 0 : summary.passedRecords();

        UploadJob job = new UploadJob();
        job.setJobId(IdGenerator.generate("job"));
        job.setProcessCode(submission.getProcessId());
        job.setProcessName(submission.getProcessName());
        job.setTemplateCode(submission.getTemplateCode());
        job.setTemplateVersion(submission.getTemplateVersion());
        job.setMakerUserId(submission.getMakerUserId());
        job.setCheckerUserId(checkerId);
        job.setSubmissionId(submission.getSubmissionId());
        job.setUploadAttemptId(submission.getUploadAttemptId());
        job.setUploadFormat(attempt.getUploadFormat());
        job.setTotalRecords(passedCount);
        job.setPassedRecords(passedCount);
        job.setFailedRecords(0);
        job.setCompletedFileKey(dispatchKey);
        // Unified with the no-maker-checker path (UploadAttemptServiceImpl#createDirectJob):
        // originalObjectKey is always the dispatched (passed-only) object, never a review-stage copy.
        job.setOriginalObjectKey(dispatchKey);
        job.setStorageProvider(submission.getStorageProvider());
        job.setMakerCheckerEnabled(true);
        job.setOriginalFileChecksumSha256(submission.getOriginalFileChecksumSha256());
        job.setStatus(JobStatus.QUEUED);
        UploadJob savedJob = uploadJobRepository.save(job);

        submission.setStatus(SubmissionStatus.ACCEPTED);
        submission.setCheckerUserId(checkerId);
        UploadSubmission savedSubmission = uploadSubmissionRepository.save(submission);

        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.CHECKER_APPROVED, checkerId, null, submission.getProcessId(), submission.getTemplateCode(),
                submission.getTemplateVersion(), null, submission.getUploadAttemptId(), submissionId, null,
                AuditOutcome.SUCCESS, "Checker approved submission " + submissionId, null));
        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.JOB_METADATA_CREATED, checkerId, null, submission.getProcessId(), submission.getTemplateCode(),
                submission.getTemplateVersion(), null, submission.getUploadAttemptId(), submissionId, savedJob.getJobId(),
                AuditOutcome.SUCCESS, "Job created after checker approval: " + savedJob.getJobId(), null));

        return new AcceptSubmissionResponse(uploadSubmissionMapper.toResponse(savedSubmission, attempt.getTemplateId()),
                uploadJobMapper.toResponse(savedJob));
    }

    @Override
    @Transactional
    public UploadSubmissionResponse reject(String submissionId, String checkerId, RejectRequest request) {
        log.debug("Rejecting submission: submissionId={}, checkerId={}", submissionId, checkerId);
        UploadSubmission submission = findOrThrow(submissionId);
        assertWaitingForChecker(submission);
        assertNotOwnSubmission(submission, checkerId);
        assertNotExpired(submission);

        submission.setStatus(SubmissionStatus.REJECTED);
        submission.setCheckerUserId(checkerId);
        submission.setReviewReason(request.reason());
        UploadSubmission saved = uploadSubmissionRepository.save(submission);

        // Nothing to clean up in S3 — the dispatch file sourceObjectKey names stays in place either
        // way; rejecting the submission just means no job ever reads it.

        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.CHECKER_REJECTED, checkerId, null, submission.getProcessId(), submission.getTemplateCode(),
                submission.getTemplateVersion(), null, submission.getUploadAttemptId(), submissionId, null,
                AuditOutcome.SUCCESS, "Checker rejected submission " + submissionId + ": " + request.reason(), null));

        return toResponse(saved);
    }

    private UploadSubmissionResponse toResponse(UploadSubmission submission) {
        String templateId = uploadAttemptRepository.findById(submission.getUploadAttemptId())
                .map(UploadAttempt::getTemplateId).orElse(null);
        return uploadSubmissionMapper.toResponse(submission, templateId);
    }

    private void assertWaitingForChecker(UploadSubmission submission) {
        if (submission.getStatus() != SubmissionStatus.WAITING_FOR_CHECKER) {
            throw new BusinessConflictException("Submission " + submission.getSubmissionId()
                    + " is not WAITING_FOR_CHECKER (current status: " + submission.getStatus() + ")");
        }
    }

    private void assertNotOwnSubmission(UploadSubmission submission, String checkerId) {
        if (CurrentActor.hasCrossActorReadAccess()) {
            return;
        }
        if (submission.getMakerUserId().equals(checkerId)) {
            throw new ActorNeSubmitterException("A checker may not act on their own submission: " + submission.getSubmissionId());
        }
    }

    private void assertNotExpired(UploadSubmission submission) {
        if (submission.getExpiresAt() != null && submission.getExpiresAt().isBefore(OffsetDateTime.now())) {
            submission.setStatus(SubmissionStatus.EXPIRED);
            uploadSubmissionRepository.save(submission);
            throw new SubmissionExpiredException("Submission " + submission.getSubmissionId() + " expired at " + submission.getExpiresAt());
        }
    }

    private UploadSubmission findOrThrow(String submissionId) {
        return uploadSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));
    }

    private StorageConfig activeStorageConfig() {
        return storageConfigRepository.findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));
    }
}
