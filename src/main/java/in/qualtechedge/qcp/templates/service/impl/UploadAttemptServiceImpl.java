package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.BatchPublishRequest;
import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.dto.request.ValidationServiceRowsResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.ProceedResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadAttemptResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationRowResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationSummaryResponse;
import in.qualtechedge.qcp.templates.entity.MakerUser;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.TemplateUploadFormat;
import in.qualtechedge.qcp.templates.entity.UploadAttempt;
import in.qualtechedge.qcp.templates.entity.UploadJob;
import in.qualtechedge.qcp.templates.entity.UploadProcess;
import in.qualtechedge.qcp.templates.entity.UploadSubmission;
import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import in.qualtechedge.qcp.templates.enums.SubmissionStatus;
import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import in.qualtechedge.qcp.templates.enums.UploadDecision;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import in.qualtechedge.qcp.templates.exception.BusinessConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.exception.UnprocessableEntityException;
import in.qualtechedge.qcp.templates.mapper.UploadAttemptMapper;
import in.qualtechedge.qcp.templates.mapper.UploadJobMapper;
import in.qualtechedge.qcp.templates.mapper.UploadSubmissionMapper;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.repository.MakerUserRepository;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.repository.TemplateRepository;
import in.qualtechedge.qcp.templates.repository.TemplateUploadFormatRepository;
import in.qualtechedge.qcp.templates.repository.UploadAttemptRepository;
import in.qualtechedge.qcp.templates.repository.UploadJobRepository;
import in.qualtechedge.qcp.templates.repository.UploadProcessRepository;
import in.qualtechedge.qcp.templates.repository.UploadRoleRepository;
import in.qualtechedge.qcp.templates.repository.UploadSubmissionRepository;
import in.qualtechedge.qcp.templates.service.BatchChunkPublisher;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import in.qualtechedge.qcp.templates.service.UploadAttemptEventPublisher;
import in.qualtechedge.qcp.templates.service.UploadAttemptService;
import in.qualtechedge.qcp.templates.service.ValidationServiceResultsClient;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import in.qualtechedge.qcp.templates.utils.DeploymentEnvironment;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import in.qualtechedge.qcp.templates.utils.S3ClientFactory;
import in.qualtechedge.qcp.templates.utils.UploadFileRowReader;
import in.qualtechedge.qcp.templates.utils.UploadObjectKeys;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadAttemptServiceImpl implements UploadAttemptService {

    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(5);
    private static final int S3_UPLOAD_MAX_ATTEMPTS = 3;
    private static final Duration S3_UPLOAD_RETRY_DELAY = Duration.ofSeconds(3);

    private final UploadAttemptRepository uploadAttemptRepository;
    private final UploadSubmissionRepository uploadSubmissionRepository;
    private final UploadJobRepository uploadJobRepository;
    private final UploadProcessRepository uploadProcessRepository;
    private final TemplateRepository templateRepository;
    private final TemplateUploadFormatRepository templateUploadFormatRepository;
    private final MakerUserRepository makerUserRepository;
    private final UploadRoleRepository uploadRoleRepository;
    private final StorageConfigRepository storageConfigRepository;
    private final DeploymentEnvironment deploymentEnvironment;
    private final UploadAttemptMapper uploadAttemptMapper;
    private final UploadSubmissionMapper uploadSubmissionMapper;
    private final UploadJobMapper uploadJobMapper;
    private final ConfigLockService configLockService;
    private final AuditEventService auditEventService;
    private final BatchChunkPublisher batchChunkPublisher;
    private final UploadAttemptEventPublisher uploadAttemptEventPublisher;
    private final ValidationServiceResultsClient validationServiceResultsClient;
    private final PassedRowsFileBuilder passedRowsFileBuilder;

    @Override
    @Transactional
    public UploadAttemptResponse create(String processId, String templateId, String actorId, MultipartFile file) {
        log.debug("Creating upload attempt: processId={}, templateId={}, actorId={}", processId, templateId, actorId);
        UploadProcess process = uploadProcessRepository.findById(processId)
                .filter(p -> p.getStatus() == ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("Process not found or not active: " + processId));
        assertProcessPermitted(processId, actorId);
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + templateId));
        if (!template.getProcessId().equals(processId) || template.getStatus() != ConfigStatus.active) {
            throw new UnprocessableEntityException("templateId must be the process's current active template");
        }
        if (file == null || file.isEmpty()) {
            throw new UnprocessableEntityException("A file is required");
        }
        String filename = sanitizeFilename(file.getOriginalFilename());
        UploadFormatKey format = detectAndAssertEnabledFormat(filename, templateId);

        StorageConfig config = activeStorageConfig();
        Path tempFile = tempFilePath();
        String checksum;
        long size;
        // Generated up front — the raw object key below is namespaced by this id so a later
        // attempt against the same process/template with the same original filename can never
        // overwrite this one's S3 object (see UploadObjectKeys).
        String attemptId = IdGenerator.generate("upl");
        try {
            checksum = spoolAndDigest(file, tempFile);
            size = Files.size(tempFile);
            String key = UploadObjectKeys.raw(deploymentEnvironment.current(), HostContext.getCurrentTenant(),
                    processId, templateId, attemptId, filename);
            putToS3(config, key, tempFile, file.getContentType());

            UploadAttempt entity = new UploadAttempt();
            entity.setUploadAttemptId(attemptId);
            entity.setProcessId(processId);
            entity.setProcessName(process.getProcessName());
            entity.setTemplateId(templateId);
            entity.setTemplateCode(template.getTemplateCode());
            entity.setTemplateVersion(template.getVersion());
            entity.setMakerUserId(actorId);
            entity.setOriginalFilename(filename);
            entity.setUploadFormat(format);
            entity.setFileSizeBytes(size);
            entity.setOriginalFileChecksumSha256(checksum);
            entity.setRawObjectKey(key);
            entity.setStatus(UploadAttemptStatus.ACCEPTED);
            entity.setMakerCheckerEnabled(template.isMakerCheckerEnabled());
            entity.setValidationsEnabled(template.isValidationsEnabled());

            UploadAttempt saved = uploadAttemptRepository.saveAndFlush(entity);

            auditEventService.record(new PipelineAuditEventRequest(
                    AuditEventCode.FILE_RECEIVED, actorId, null, processId, template.getTemplateCode(),
                    template.getVersion(), null, saved.getUploadAttemptId(), null, null,
                    AuditOutcome.SUCCESS, "Upload attempt accepted: " + filename, null));
            return uploadAttemptMapper.toResponse(saved);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to stage the uploaded file", e);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    @Override
    @Transactional
    public UploadAttemptResponse startValidation(String attemptId, String actorId) {
        log.debug("Starting validation: attemptId={}, actorId={}", attemptId, actorId);
        UploadAttempt attempt = findOrThrow(attemptId);
        assertOwnership(attempt, actorId);

        if (attempt.getStatus() == UploadAttemptStatus.VALIDATING || attempt.getStatus() == UploadAttemptStatus.READY_FOR_DECISION) {
            return uploadAttemptMapper.toResponse(attempt);
        }
        if (attempt.getStatus() != UploadAttemptStatus.ACCEPTED) {
            throw new BusinessConflictException("Attempt " + attemptId + " is not in a state that can start validation: " + attempt.getStatus());
        }

        if (!attempt.isValidationsEnabled()) {
            return skipValidation(attempt);
        }
        return startKafkaValidation(attempt);
    }

    private UploadAttemptResponse skipValidation(UploadAttempt attempt) {
        int rowCount = countRows(attempt);
        ValidationSummaryResponse summary = new ValidationSummaryResponse(rowCount, rowCount, 0);
        attempt.setSummary(JsonColumnMapper.write(summary));
        attempt.setIssues("[]");
        attempt.setValidatedObjectKey(promoteToValidated(attempt));
        attempt.setStatus(UploadAttemptStatus.READY_FOR_DECISION);
        UploadAttempt saved = uploadAttemptRepository.save(attempt);
        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.VALIDATION_SKIPPED, attempt.getMakerUserId(), null, attempt.getProcessId(),
                attempt.getTemplateCode(), attempt.getTemplateVersion(), null, attempt.getUploadAttemptId(),
                null, null, AuditOutcome.SUCCESS, "Validation skipped — validationsEnabled is false", null));
        UploadAttemptResponse response = uploadAttemptMapper.toResponse(saved);
        // Leaves ACCEPTED straight for READY_FOR_DECISION in this same call — §2.2's "done" event,
        // for whichever SSE subscriber opened the stream between create() and this validate() call.
        uploadAttemptEventPublisher.publish(response);
        return response;
    }

    private UploadAttemptResponse startKafkaValidation(UploadAttempt attempt) {
        UUID batchId = UUID.randomUUID();
        attempt.setBatchId(batchId);
        attempt.setStatus(UploadAttemptStatus.VALIDATING);
        UploadAttempt saved = uploadAttemptRepository.save(attempt);
        // §2.2's "attempt" event — still in flight, but the client watching /events should see
        // the ACCEPTED -> VALIDATING transition rather than waiting silently for "done".
        uploadAttemptEventPublisher.publish(uploadAttemptMapper.toResponse(saved));

        configLockService.acquire(attempt.getProcessId(), batchId.toString());
        StorageConfig config = activeStorageConfig();
        Path tempFile = tempFilePath();
        try {
            downloadFromS3(config, attempt.getRawObjectKey(), tempFile);
            BatchPublishRequest request = new BatchPublishRequest(batchId, attempt.getProcessId(), attempt.getTemplateId(),
                    attempt.getMakerUserId(), attempt.getOriginalFilename(), batchId.toString());
            boolean published = batchChunkPublisher.publish(request, tempFile);
            if (!published) {
                saved.setStatus(UploadAttemptStatus.ABORTED);
                saved = uploadAttemptRepository.save(saved);
                // Leaves VALIDATING right back out again in the same call — §2.2's "done" event.
                uploadAttemptEventPublisher.publish(uploadAttemptMapper.toResponse(saved));
            } else {
                auditEventService.record(new PipelineAuditEventRequest(
                        AuditEventCode.VALIDATION_STARTED, attempt.getMakerUserId(), null, attempt.getProcessId(),
                        attempt.getTemplateCode(), attempt.getTemplateVersion(), null, attempt.getUploadAttemptId(),
                        null, batchId.toString(), AuditOutcome.SUCCESS, "Validation started for attempt " + attempt.getUploadAttemptId(), null));
            }
            return uploadAttemptMapper.toResponse(saved);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UploadAttemptResponse get(String attemptId) {
        return uploadAttemptMapper.toResponse(findOrThrow(attemptId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ValidationRowResponse> getRows(String attemptId, String rowStatus, List<String> ruleTypes,
            String search, Pageable pageable) {
        UploadAttempt attempt = findOrThrow(attemptId);
        if (attempt.getBatchId() == null) {
            throw new ResourceNotFoundException("Attempt " + attemptId + " has not started validation yet");
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
    public SseEmitter subscribe(String attemptId) {
        log.debug("Subscribing to upload attempt events: attemptId={}", attemptId);
        // 404s here, before any emitter is registered — matches §2.2a: no stream is opened for
        // an unknown attemptId.
        UploadAttempt attempt = findOrThrow(attemptId);
        SseEmitter emitter = uploadAttemptEventPublisher.subscribe(attemptId);
        // Covers the race where validation already finished before the client opened this
        // connection — the subscriber's first event is always the current state ("attempt" if
        // still in flight, "done" and an immediate close if already terminal).
        uploadAttemptEventPublisher.publish(uploadAttemptMapper.toResponse(attempt));
        return emitter;
    }

    @Override
    @Transactional
    public ProceedResponse proceed(String attemptId, String actorId) {
        log.debug("Proceeding on attempt: attemptId={}, actorId={}", attemptId, actorId);
        UploadAttempt attempt = findOrThrow(attemptId);
        assertOwnership(attempt, actorId);
        assertReadyForDecision(attempt);

        Template template = templateRepository.findById(attempt.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + attempt.getTemplateId()));
        String sourceKey = attempt.getValidatedObjectKey() != null ? attempt.getValidatedObjectKey() : attempt.getRawObjectKey();

        attempt.setStatus(UploadAttemptStatus.CONTINUED);
        attempt.setDecision(UploadDecision.PROCEED);
        attempt.setDecidedAt(OffsetDateTime.now());
        UploadAttempt savedAttempt = uploadAttemptRepository.save(attempt);

        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.DECISION_PROCEED, actorId, null, attempt.getProcessId(), attempt.getTemplateCode(),
                attempt.getTemplateVersion(), null, attemptId, null, null, AuditOutcome.SUCCESS,
                "Maker proceeded on attempt " + attemptId, null));

        if (attempt.isMakerCheckerEnabled()) {
            UploadSubmission submission = createSubmission(attempt, template, sourceKey, actorId);
            return new ProceedResponse(uploadAttemptMapper.toResponse(savedAttempt),
                    uploadSubmissionMapper.toResponse(submission, attempt.getTemplateId()), null);
        }
        UploadJob job = createDirectJob(attempt, template, sourceKey);
        return new ProceedResponse(uploadAttemptMapper.toResponse(savedAttempt), null, uploadJobMapper.toResponse(job));
    }

    private UploadSubmission createSubmission(UploadAttempt attempt, Template template, String sourceKey, String actorId) {
        // No separate pending_approval copy — dispatch (built once, right here) already does that
        // job: a clean, passed-rows-only file the checker can download to see exactly what proceeding
        // will send, and the same key CheckerServiceImpl.accept() reads straight off the submission
        // to build the job's completedFileKey, with no second build/S3 write at accept time.
        String dispatchKey = passedRowsFileBuilder.build(attempt, sourceKey);
        MakerUser makerUser = makerUserRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Maker user not found with id: " + actorId));

        UploadSubmission submission = new UploadSubmission();
        submission.setSubmissionId(IdGenerator.generate("sub"));
        submission.setUploadAttemptId(attempt.getUploadAttemptId());
        submission.setProcessId(attempt.getProcessId());
        submission.setProcessName(attempt.getProcessName());
        submission.setTemplateCode(attempt.getTemplateCode());
        submission.setTemplateVersion(attempt.getTemplateVersion());
        submission.setMakerUserId(actorId);
        submission.setMakerDisplayName(makerUser.getFullName());
        submission.setPendingObjectKey(dispatchKey);
        submission.setSourceObjectKey(dispatchKey);
        submission.setStorageProvider(InterimStoreProvider.AWS_S3);
        submission.setSummary(attempt.getSummary());
        submission.setIssues(attempt.getIssues());
        submission.setOriginalFileChecksumSha256(attempt.getOriginalFileChecksumSha256());
        submission.setStatus(SubmissionStatus.WAITING_FOR_CHECKER);
        submission.setExpiresAt(OffsetDateTime.now().plusHours(template.getMakerCheckerSlaHours()));
        UploadSubmission saved = uploadSubmissionRepository.save(submission);

        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.CHECKER_SUBMITTED, actorId, null, attempt.getProcessId(), attempt.getTemplateCode(),
                attempt.getTemplateVersion(), null, attempt.getUploadAttemptId(), saved.getSubmissionId(), null,
                AuditOutcome.SUCCESS, "Submitted for checker review: " + saved.getSubmissionId(), null));
        return saved;
    }

    private UploadJob createDirectJob(UploadAttempt attempt, Template template, String sourceKey) {
        // No pending_processing copy — the job reads the validated (or raw) object directly, the
        // same file promote-to-validated/ValidatedResultS3Exporter already produced.
        ValidationSummaryResponse summary = JsonColumnMapper.read(attempt.getSummary(), ValidationSummaryResponse.class);
        int passedCount = summary == null ? 0 : summary.passedRecords();
        // Never dispatch a row that failed validation to the third party — PassedRowsFileBuilder
        // rebuilds a clean, passed-only CSV from validation-service's own per-row results (a no-op
        // returning sourceKey unchanged when validation was skipped, since every row already counts
        // as passed then). totalRecords/passedRecords collapse to the same number below because that
        // rebuilt file is now the job's entire content — there is nothing left in it that failed.
        String dispatchKey = passedRowsFileBuilder.build(attempt, sourceKey);

        UploadJob job = new UploadJob();
        job.setJobId(IdGenerator.generate("job"));
        job.setProcessCode(attempt.getProcessId());
        job.setProcessName(attempt.getProcessName());
        job.setTemplateCode(attempt.getTemplateCode());
        job.setTemplateVersion(attempt.getTemplateVersion());
        job.setMakerUserId(attempt.getMakerUserId());
        job.setUploadAttemptId(attempt.getUploadAttemptId());
        job.setUploadFormat(attempt.getUploadFormat());
        job.setTotalRecords(passedCount);
        job.setPassedRecords(passedCount);
        job.setFailedRecords(0);
        job.setCompletedFileKey(dispatchKey);
        job.setOriginalObjectKey(dispatchKey);
        job.setStorageProvider(InterimStoreProvider.AWS_S3);
        job.setMakerCheckerEnabled(false);
        job.setOriginalFileChecksumSha256(attempt.getOriginalFileChecksumSha256());
        job.setStatus(JobStatus.QUEUED);
        UploadJob saved = uploadJobRepository.save(job);

        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.JOB_METADATA_CREATED, attempt.getMakerUserId(), null, attempt.getProcessId(),
                attempt.getTemplateCode(), attempt.getTemplateVersion(), null, attempt.getUploadAttemptId(),
                null, saved.getJobId(), AuditOutcome.SUCCESS, "Job created directly: " + saved.getJobId(), null));
        return saved;
    }

    @Override
    @Transactional
    public UploadAttemptResponse reupload(String attemptId, String actorId) {
        log.debug("Reupload decision: attemptId={}, actorId={}", attemptId, actorId);
        UploadAttempt attempt = findOrThrow(attemptId);
        assertOwnership(attempt, actorId);
        assertReadyForDecision(attempt);

        attempt.setStatus(UploadAttemptStatus.REUPLOADED);
        attempt.setDecision(UploadDecision.REUPLOAD);
        attempt.setDecidedAt(OffsetDateTime.now());
        UploadAttempt saved = uploadAttemptRepository.save(attempt);

        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.DECISION_REUPLOAD, actorId, null, attempt.getProcessId(), attempt.getTemplateCode(),
                attempt.getTemplateVersion(), null, attemptId, null, null, AuditOutcome.SUCCESS,
                "Maker chose to reupload for attempt " + attemptId, null));
        return uploadAttemptMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UploadAttemptResponse> listByMaker(String makerUserId) {
        return uploadAttemptRepository.findByMakerUserIdOrderByCreatedAtDesc(makerUserId).stream()
                .map(uploadAttemptMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedDownloadResponse download(String attemptId, String stage, String actorId) {
        UploadAttempt attempt = findOrThrow(attemptId);
        assertOwnership(attempt, actorId);
        String key = switch (stage == null ? "" : stage) {
            case "raw" -> attempt.getRawObjectKey();
            case "validated" -> attempt.getValidatedObjectKey();
            default -> throw new UnprocessableEntityException("stage must be 'raw' or 'validated'");
        };
        if (key == null) {
            throw new ResourceNotFoundException("Attempt " + attemptId + " has no object at stage " + stage);
        }
        StorageConfig config = activeStorageConfig();
        PresignedGetObjectRequest presigned = S3ClientFactory.presign(config, key, DOWNLOAD_URL_EXPIRY);
        return new PresignedDownloadResponse(presigned.url().toString(), OffsetDateTime.now().plus(DOWNLOAD_URL_EXPIRY));
    }

    @Override
    @Transactional(readOnly = true)
    public UploadJobResponse getJobForAttempt(String attemptId, String actorId) {
        UploadAttempt attempt = findOrThrow(attemptId);
        assertOwnership(attempt, actorId);
        UploadJob job = uploadJobRepository.findFirstByUploadAttemptIdOrderByCreatedAtDesc(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("No job has been created from attempt " + attemptId + " yet"));
        return uploadJobMapper.toResponse(job);
    }

    // ---- helpers ----

    /** §2.1 precondition: "Process must be active and permitted for the actor's role" — a process
     * outside the actor's {@code UploadRole.processAccess} is treated as not found, same as an
     * unknown id, rather than leaking its existence via a distinct 403. */
    private void assertProcessPermitted(String processId, String actorId) {
        MakerUser makerUser = makerUserRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Maker user not found with id: " + actorId));
        boolean permitted = uploadRoleRepository.findAllById(makerUser.getRoleIds()).stream()
                .anyMatch(role -> role.getProcessAccess().contains(processId));
        if (!permitted) {
            throw new ResourceNotFoundException("Process not found or not active: " + processId);
        }
    }

    private UploadAttempt findOrThrow(String attemptId) {
        return uploadAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload attempt not found with id: " + attemptId));
    }

    private void assertOwnership(UploadAttempt attempt, String actorId) {
        if (!attempt.getMakerUserId().equals(actorId) && !CurrentActor.hasCrossActorReadAccess()) {
            throw new AccessDeniedException("Attempt " + attempt.getUploadAttemptId() + " does not belong to actor " + actorId);
        }
    }

    private void assertReadyForDecision(UploadAttempt attempt) {
        if (attempt.getStatus() != UploadAttemptStatus.READY_FOR_DECISION) {
            throw new BusinessConflictException("Attempt " + attempt.getUploadAttemptId()
                    + " is not READY_FOR_DECISION (current status: " + attempt.getStatus() + ")");
        }
    }

    private UploadFormatKey detectAndAssertEnabledFormat(String filename, String templateId) {
        UploadFormatKey format;
        try {
            format = UploadFileRowReader.detectFormat(filename);
        } catch (IllegalArgumentException e) {
            throw new UnprocessableEntityException(e.getMessage());
        }
        boolean enabled = templateUploadFormatRepository.findByTemplateId(templateId).stream()
                .filter(f -> f.getFormatKey() == format)
                .map(TemplateUploadFormat::isEnabled)
                .findFirst().orElse(false);
        if (!enabled) {
            throw new UnprocessableEntityException("Upload format " + format + " is not enabled for template " + templateId);
        }
        return format;
    }

    private int countRows(UploadAttempt attempt) {
        StorageConfig config = activeStorageConfig();
        Path tempFile = tempFilePath();
        try {
            downloadFromS3(config, attempt.getRawObjectKey(), tempFile);
            int[] count = {0};
            UploadFileRowReader.readRows(tempFile, attempt.getUploadFormat(), (rowNumber, data) -> count[0]++);
            return count[0];
        } catch (IOException e) {
            throw new IllegalStateException("Failed to count rows for attempt " + attempt.getUploadAttemptId(), e);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    private String promoteToValidated(UploadAttempt attempt) {
        String validatedKey = UploadObjectKeys.validated(deploymentEnvironment.current(), HostContext.getCurrentTenant(),
                attempt.getProcessId(), attempt.getTemplateId(), attempt.getUploadAttemptId(), attempt.getOriginalFilename());
        copyS3(attempt.getRawObjectKey(), validatedKey);
        return validatedKey;
    }

    private StorageConfig activeStorageConfig() {
        return storageConfigRepository.findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));
    }

    /**
     * Transfer-manager multipart upload, not a plain {@code putObject} — same reasoning as
     * {@link UploadS3Worker#putToS3}: an upload-attempt file can run to lakhs of rows just like a
     * raw maker upload, and a single-stream PUT serializes it through one HTTP connection.
     */
    private void putToS3(StorageConfig config, String key, Path file, String contentType) {
        for (int attempt = 1; ; attempt++) {
            try (S3AsyncClient asyncClient = S3ClientFactory.buildAsync(config);
                 S3TransferManager transferManager = S3TransferManager.builder().s3Client(asyncClient).build()) {
                UploadFileRequest uploadRequest = UploadFileRequest.builder()
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(config.getBucketName())
                                .key(key)
                                .contentType(contentType)
                                .build())
                        .source(file)
                        .build();
                // Result discarded — callers of putToS3 only need the upload to have completed, they
                // already know the key (set before calling), unlike UploadS3Worker which reads the
                // eTag back onto its own record.
                transferManager.uploadFile(uploadRequest).completionFuture().join();
                return;
            } catch (CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                // SdkClientException means the SDK's own per-part retries never even reached S3 (DNS
                // failure, connection reset) — distinct from S3Exception, a real response from the
                // service that a retry won't change. Only the former is worth retrying at this level.
                if (attempt < S3_UPLOAD_MAX_ATTEMPTS && cause instanceof SdkClientException) {
                    log.warn("S3 upload attempt {} failed transiently, retrying: bucket={}, key={}",
                            attempt, config.getBucketName(), key, cause);
                    sleepBeforeRetry(S3_UPLOAD_RETRY_DELAY.multipliedBy(attempt));
                    continue;
                }
                String detail = cause instanceof S3Exception s3e && s3e.awsErrorDetails() != null
                        ? s3e.awsErrorDetails().errorMessage() : cause.getMessage();
                log.error("S3 upload failed: bucket={}, key={}", config.getBucketName(), key, cause);
                throw new IllegalStateException("S3 upload failed: " + detail, cause);
            }
        }
    }

    private void sleepBeforeRetry(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying S3 upload", e);
        }
    }

    private void downloadFromS3(StorageConfig config, String key, Path destination) {
        try (S3Client client = S3ClientFactory.build(config)) {
            client.getObject(GetObjectRequest.builder().bucket(config.getBucketName()).key(key).build(), destination);
        } catch (S3Exception e) {
            log.error("S3 download failed: bucket={}, key={}, status={}", config.getBucketName(), key, e.statusCode(), e);
            String detail = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            throw new IllegalStateException("S3 download failed: " + detail, e);
        }
    }

    private void copyS3(String sourceKey, String destinationKey) {
        StorageConfig config = activeStorageConfig();
        try (S3Client client = S3ClientFactory.build(config)) {
            client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(config.getBucketName())
                    .sourceKey(sourceKey)
                    .destinationBucket(config.getBucketName())
                    .destinationKey(destinationKey)
                    .build());
        } catch (S3Exception e) {
            log.error("S3 copy failed: source={}, destination={}, status={}", sourceKey, destinationKey, e.statusCode(), e);
            String detail = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            throw new IllegalStateException("S3 copy failed: " + detail, e);
        }
    }

    private Path tempFilePath() {
        return Path.of(System.getProperty("java.io.tmpdir"), "diy-upload-attempt-" + UUID.randomUUID() + ".tmp");
    }

    private String spoolAndDigest(MultipartFile file, Path tempFile) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
        try (InputStream in = file.getInputStream();
             DigestInputStream digestIn = new DigestInputStream(in, digest);
             OutputStream out = Files.newOutputStream(tempFile)) {
            digestIn.transferTo(out);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {}", path, e);
        }
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new UnprocessableEntityException("A filename is required");
        }
        String normalized = originalFilename.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String name = (lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized).trim();
        if (name.isEmpty()) {
            throw new UnprocessableEntityException("A filename is required");
        }
        return name;
    }
}
