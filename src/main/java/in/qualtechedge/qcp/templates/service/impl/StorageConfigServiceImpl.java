package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.StorageConfigRequest;
import in.qualtechedge.qcp.templates.dto.response.StorageConfigResponse;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.StorageConfigMapper;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.StorageConfigService;
import in.qualtechedge.qcp.templates.utils.ConfigLifecycleGuard;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import in.qualtechedge.qcp.templates.utils.S3ClientFactory;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortIncompleteMultipartUpload;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LifecycleRuleFilter;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageConfigServiceImpl implements StorageConfigService {

    /** S3 lifecycle rules only support day-granularity; this is the closest native approximation
     * of the 6-hour cleanup window this was asked for. */
    private static final int ABORT_INCOMPLETE_MULTIPART_UPLOAD_DAYS = 1;
    private static final String LIFECYCLE_RULE_ID = "abort-incomplete-multipart-uploads";

    private final StorageConfigRepository storageConfigRepository;
    private final StorageConfigMapper storageConfigMapper;
    private final AuditEventService auditEventService;

    @Override
    @Transactional
    public StorageConfigResponse create(StorageConfigRequest request) {
        log.debug("Creating storage config: label={}", request.connectionLabel());
        if (storageConfigRepository.existsByConnectionLabelIgnoreCase(request.connectionLabel())) {
            throw new ConflictException("A storage connection labeled '" + request.connectionLabel() + "' already exists");
        }
        String actorId = CurrentActor.id();
        StorageConfig entity = storageConfigMapper.toEntity(request, actorId);
        StorageConfig saved = storageConfigRepository.saveAndFlush(entity);
        auditEventService.record("ADMIN_STORAGE_CREATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Storage connection " + saved.getConfigId() + " created");
        return storageConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public StorageConfigResponse getById(String configId) {
        log.debug("Fetching storage config: id={}", configId);
        return storageConfigMapper.toResponse(findOrThrow(configId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StorageConfigResponse> getAll() {
        log.debug("Listing storage configs");
        return storageConfigRepository.findAll().stream().map(storageConfigMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public StorageConfigResponse update(String configId, StorageConfigRequest request) {
        log.debug("Updating storage config: id={}", configId);
        StorageConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertEditable(entity.getStatus());
        if (!entity.getConnectionLabel().equalsIgnoreCase(request.connectionLabel())
                && storageConfigRepository.existsByConnectionLabelIgnoreCaseAndConfigIdNot(request.connectionLabel(), configId)) {
            throw new ConflictException("A storage connection labeled '" + request.connectionLabel() + "' already exists");
        }
        String actorId = CurrentActor.id();
        storageConfigMapper.updateEntity(entity, request, actorId);
        StorageConfig saved = storageConfigRepository.save(entity);
        auditEventService.record("ADMIN_STORAGE_UPDATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Storage connection " + configId + " updated");
        return storageConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public StorageConfigResponse submit(String configId) {
        log.debug("Submitting storage config: id={}", configId);
        StorageConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertSubmittable(entity.getStatus());
        String actorId = CurrentActor.id();
        entity.setStatus(ConfigStatus.waitingForChecker);
        entity.setSubmittedBy(actorId);
        StorageConfig saved = storageConfigRepository.save(entity);
        auditEventService.record("ADMIN_STORAGE_SUBMITTED", actorId, null, null,
                AuditOutcome.SUCCESS, "Storage connection " + configId + " submitted for review");
        return storageConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public StorageConfigResponse accept(String configId) {
        log.debug("Accepting storage config: id={}", configId);
        StorageConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.active);
        entity.setRejectionReason(null);
        StorageConfig saved = storageConfigRepository.save(entity);
        ensureAbortIncompleteMultipartUploadLifecycleRule(saved);
        auditEventService.record("ADMIN_STORAGE_ACTIVATED", actorId, null, null,
                AuditOutcome.SUCCESS, "Storage connection " + configId + " activated");
        return storageConfigMapper.toResponse(saved);
    }

    /**
     * Applies (or refreshes) an {@code AbortIncompleteMultipartUpload} lifecycle rule on the
     * bucket, so a multipart upload left dangling by a failed transfer (see
     * {@link UploadS3Worker#putToS3}) doesn't sit there accumulating storage cost forever.
     * Merges with whatever lifecycle rules the bucket already has — {@code PutBucketLifecycleConfiguration}
     * replaces the bucket's entire lifecycle configuration, so a naive single-rule PUT would
     * silently wipe out any pre-existing rules the bucket owner set up outside this app.
     * <p>
     * Best-effort: a bucket-lifecycle PUT is a bucket-level admin permission that a
     * least-privilege upload credential may not have been granted, so a failure here is logged,
     * not thrown — it must not block activating an otherwise-working storage connection.
     */
    private void ensureAbortIncompleteMultipartUploadLifecycleRule(StorageConfig config) {
        if (config.getProvider() != InterimStoreProvider.AWS_S3) {
            return;
        }
        try (S3Client client = S3ClientFactory.build(config)) {
            List<LifecycleRule> rules = new ArrayList<>(existingLifecycleRules(client, config.getBucketName()));
            rules.removeIf(rule -> LIFECYCLE_RULE_ID.equals(rule.id()));
            rules.add(LifecycleRule.builder()
                    .id(LIFECYCLE_RULE_ID)
                    .status(ExpirationStatus.ENABLED)
                    .filter(LifecycleRuleFilter.builder().prefix("").build())
                    .abortIncompleteMultipartUpload(AbortIncompleteMultipartUpload.builder()
                            .daysAfterInitiation(ABORT_INCOMPLETE_MULTIPART_UPLOAD_DAYS)
                            .build())
                    .build());
            client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder()
                    .bucket(config.getBucketName())
                    .lifecycleConfiguration(BucketLifecycleConfiguration.builder().rules(rules).build())
                    .build());
            log.info("Applied abort-incomplete-multipart-upload lifecycle rule ({} day(s)) to bucket {}",
                    ABORT_INCOMPLETE_MULTIPART_UPLOAD_DAYS, config.getBucketName());
        } catch (RuntimeException e) {
            // Broad on purpose: this must never fail activation of an otherwise-working storage
            // connection — a bad region string or missing credentials fails client construction
            // itself (not an S3Exception), same as a genuine S3-side rejection of the lifecycle PUT.
            log.error("Could not apply the abort-incomplete-multipart-upload lifecycle rule to bucket {} "
                            + "(storage config {}) — dangling multipart uploads on this bucket won't be auto-aborted",
                    config.getBucketName(), config.getConfigId(), e);
        }
    }

    private List<LifecycleRule> existingLifecycleRules(S3Client client, String bucket) {
        try {
            return client.getBucketLifecycleConfiguration(
                    GetBucketLifecycleConfigurationRequest.builder().bucket(bucket).build()).rules();
        } catch (S3Exception e) {
            if ("NoSuchLifecycleConfiguration".equals(e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null)) {
                return List.of();
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public StorageConfigResponse reject(String configId, RejectRequest request) {
        log.debug("Rejecting storage config: id={}", configId);
        StorageConfig entity = findOrThrow(configId);
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.rejected);
        entity.setRejectionReason(request.reason());
        StorageConfig saved = storageConfigRepository.save(entity);
        auditEventService.record("ADMIN_STORAGE_REJECTED", actorId, null, null,
                AuditOutcome.SUCCESS, "Storage connection " + configId + " rejected: " + request.reason());
        return storageConfigMapper.toResponse(saved);
    }

    private StorageConfig findOrThrow(String configId) {
        return storageConfigRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("Storage connection not found with id: " + configId));
    }
}
