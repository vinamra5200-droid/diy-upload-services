package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.BatchPublishRequest;
import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.UploadFile;
import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.UploadFileStatus;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.UploadFileMapper;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.repository.TemplateRepository;
import in.qualtechedge.qcp.templates.repository.UploadFileRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.BatchChunkPublisher;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import in.qualtechedge.qcp.templates.service.UploadEventPublisher;
import in.qualtechedge.qcp.templates.utils.DeploymentEnvironment;
import in.qualtechedge.qcp.templates.utils.S3ClientFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Runs the actual S3 PUT off the request thread (see {@link in.qualtechedge.qcp.templates.config.AsyncConfig})
 * so a large file doesn't hold the upload POST open for as long as the transfer takes. Publishes
 * every status transition via {@link UploadEventPublisher} so the frontend can watch over SSE
 * instead of polling. Once the PUT succeeds, hands the still-present temp file to
 * {@link BatchChunkPublisher} to be chunked onto Kafka for validation-service before it's deleted.
 * <p>
 * Must be a separate Spring bean from {@link S3UploadServiceImpl} — {@code @Async} is
 * proxy-based, so calling an {@code @Async} method on {@code this} from within the same class
 * silently runs synchronously instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UploadS3Worker {

    private static final String KEY_TEMPLATE = "diy-upload/%s/%s/%s/raw/%s";

    private final UploadFileRepository uploadFileRepository;
    private final StorageConfigRepository storageConfigRepository;
    private final DeploymentEnvironment deploymentEnvironment;
    private final UploadEventPublisher uploadEventPublisher;
    private final UploadFileMapper uploadFileMapper;
    private final AuditEventService auditEventService;
    private final TemplateRepository templateRepository;
    private final BatchChunkPublisher batchChunkPublisher;
    private final ConfigLockService configLockService;

    @Async("uploadTaskExecutor")
    public void process(String tenant, String uploadId, String filename, String contentType, Path tempFile) {
        // The @Async proxy hands this off to a fresh thread pool thread, which doesn't inherit the
        // request thread's HostContext ThreadLocal — without this, tenant-routed repository calls
        // below silently fall back to the system DB instead of the tenant's own database.
        HostContext.setCurrentTenant(tenant);
        try {
            UploadFile record = uploadFileRepository.findById(uploadId).orElse(null);
            if (record == null) {
                log.warn("Upload record {} vanished before background processing started", uploadId);
                deleteQuietly(tempFile);
                return;
            }

            record.setStatus(UploadFileStatus.inProgress);
            UploadFile inProgress = uploadFileRepository.save(record);
            uploadEventPublisher.publish(uploadFileMapper.toResponse(inProgress));

            try {
                putToS3(record, filename, contentType, tempFile);
                record.setStatus(UploadFileStatus.completed);
                // A real UUID, not the usual prefixed-id convention (IdGenerator) — this becomes
                // the Kafka batchId that BatchChunkPublisher sends, and validation-service's
                // consumer deserializes that field as java.util.UUID.
                String jobId = UUID.randomUUID().toString();
                record.setJobId(jobId);
                UploadFile completed = uploadFileRepository.save(record);
                // The config lock was acquired under uploadId (S3UploadServiceImpl, before jobId
                // existed) — move it forward to jobId so validation-service's completion event
                // (keyed by the Kafka batchId, i.e. this same jobId) can release it later.
                configLockService.reassignRef(completed.getProcessId(), uploadId, jobId);
                log.info("Uploaded to S3: bucket={}, key={}, uploadId={}, jobId={}",
                        completed.getS3Bucket(), completed.getS3Key(), uploadId, completed.getJobId());
                uploadEventPublisher.publish(uploadFileMapper.toResponse(completed));
                recordS3WriteCompleted(completed);
                // The S3 write above already succeeded and committed record as `completed` — a
                // false here (BatchChunkPublisher never throws, see its Javadoc) means the row
                // would otherwise stay stuck at `completed` forever despite validation-service
                // never receiving the data, which also permanently blocks any re-upload of the
                // same file (upload_files_dedup_uidx excludes only `failed` rows).
                BatchPublishRequest publishRequest = new BatchPublishRequest(
                        UUID.fromString(jobId), completed.getProcessId(), completed.getTemplateId(),
                        completed.getUploadedBy(), completed.getOriginalFilename(), jobId);
                if (!batchChunkPublisher.publish(publishRequest, tempFile)) {
                    completed.setStatus(UploadFileStatus.failed);
                    completed.setErrorMessage("Failed to publish batch chunks to validation-service — see ENQUEUE_FAILED audit event");
                    UploadFile enqueueFailed = uploadFileRepository.save(completed);
                    uploadEventPublisher.publish(uploadFileMapper.toResponse(enqueueFailed));
                }
            } catch (RuntimeException e) {
                log.error("Upload {} failed", uploadId, e);
                record.setStatus(UploadFileStatus.failed);
                record.setErrorMessage(e.getMessage());
                UploadFile failed = uploadFileRepository.save(record);
                // The lock ref may already have moved from uploadId to jobId (reassignRef, just
                // above) before this failure — release whichever one is actually current.
                configLockService.release(record.getJobId() != null ? record.getJobId() : uploadId);
                uploadEventPublisher.publish(uploadFileMapper.toResponse(failed));
            } finally {
                deleteQuietly(tempFile);
            }
        } finally {
            HostContext.clear();
        }
    }

    /**
     * S3_WRITE_COMPLETED (SD §12.3 #25 — "dual-control-off completed-file path"), not
     * JOB_METADATA_CREATED (#26): this flow has no maker-checker gate yet, so the raw S3 PUT
     * finishing here IS the dual-control-off completed-file write, not the downstream job that
     * SD §12.3 #26 fires after checker approval + S3 promote (events #20-25) — a stage this
     * codebase doesn't have yet (see V1_0_61's header on {@code upload_files.job_id}).
     * <p>
     * {@code uploadedBy} was captured on the request thread before the {@code @Async} hop (same
     * reason {@code tenant} is passed into {@link #process}) — {@link org.springframework.security.core.context.SecurityContextHolder}
     * is thread-local too, so {@code CurrentActor.id()} would throw here.
     */
    private void recordS3WriteCompleted(UploadFile record) {
        // templateCode/version for the audit row, not record.getTemplateId() (the DB id) — matches
        // what FILE_RECEIVED records for the same upload in S3UploadServiceImpl.
        Template template = templateRepository.findById(record.getTemplateId()).orElse(null);
        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.S3_WRITE_COMPLETED,
                record.getUploadedBy(),
                null,
                record.getProcessId(),
                template == null ? null : template.getTemplateCode(),
                template == null ? null : template.getVersion(),
                null,
                null,
                null,
                record.getJobId(),
                AuditOutcome.SUCCESS,
                "S3 write completed for upload " + record.getUploadId() + " (job " + record.getJobId() + ")",
                null));
    }

    private void putToS3(UploadFile record, String filename, String contentType, Path tempFile) {
        StorageConfig config = storageConfigRepository
                .findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));
        assertS3FieldsPresent(config);

        String key = KEY_TEMPLATE.formatted(deploymentEnvironment.current(), record.getProcessId(), record.getTemplateId(), filename);

        try (S3Client client = S3ClientFactory.build(config)) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .build();
            // RequestBody.fromFile streams from disk (content-length is derived from the file) —
            // the file never has to fit in JVM heap, which matters since a maker's upload can run
            // to lakhs of rows.
            PutObjectResponse response = client.putObject(request, RequestBody.fromFile(tempFile));
            record.setS3Bucket(config.getBucketName());
            record.setS3Key(key);
            record.setEtag(response.eTag());
        } catch (S3Exception e) {
            log.error("S3 upload failed: bucket={}, key={}, status={}", config.getBucketName(), key, e.statusCode(), e);
            String detail = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            throw new IllegalStateException("S3 upload failed: " + detail, e);
        }
    }

    private void assertS3FieldsPresent(StorageConfig config) {
        if (isBlank(config.getBucketName()) || isBlank(config.getBucketRegion())
                || isBlank(config.getAccessKeyId()) || isBlank(config.getSecretAccessKey())) {
            throw new ConflictException("The active AWS_S3 storage connection is missing bucket/credential fields");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp upload file: {}", path, e);
        }
    }
}
