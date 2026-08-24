package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.BatchValidationCompletedMessage;
import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.dto.request.ValidationServiceRowsResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationIssueResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationSummaryResponse;
import in.qualtechedge.qcp.templates.entity.BatchUploadResult;
import in.qualtechedge.qcp.templates.entity.BatchUploadResultRow;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.UploadAttempt;
import in.qualtechedge.qcp.templates.entity.UploadFile;
import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import in.qualtechedge.qcp.templates.enums.ValidationSeverity;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.repository.BatchUploadResultRepository;
import in.qualtechedge.qcp.templates.repository.BatchUploadResultRowRepository;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.repository.UploadAttemptRepository;
import in.qualtechedge.qcp.templates.repository.UploadFileRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.BatchValidationResultService;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import in.qualtechedge.qcp.templates.utils.S3ClientFactory;
import in.qualtechedge.qcp.templates.utils.UploadObjectKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchValidationResultServiceImpl implements BatchValidationResultService {

    /** Caps how many per-row issues an attempt's {@code issues} JSONB column carries — a batch can
     * run to lakhs of rows, and the full failed-row detail already lives in
     * {@code batch_upload_result_rows} (queryable via the existing results endpoints). */
    private static final int MAX_ISSUES_PER_ATTEMPT = 1000;

    private final UploadFileRepository uploadFileRepository;
    private final UploadAttemptRepository uploadAttemptRepository;
    private final BatchUploadResultRepository batchUploadResultRepository;
    private final BatchUploadResultRowRepository batchUploadResultRowRepository;
    private final StorageConfigRepository storageConfigRepository;
    private final AuditEventService auditEventService;
    private final ConfigLockService configLockService;

    @Override
    @Transactional
    public void recordCompletion(BatchValidationCompletedMessage message, List<ValidationServiceRowsResponse.Row> rows) {
        Optional<UploadAttempt> attempt = uploadAttemptRepository.findByBatchId(message.batchId());
        BatchOwner owner = attempt.<BatchOwner>map(a -> new BatchOwner(a.getMakerUserId(), a.getProcessId(), a.getTemplateId()))
                .orElseGet(() -> resolveUploadFileOwner(message.batchId()));

        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.VALIDATION_COMPLETED, owner.actorId(), null, owner.processId(),
                owner.templateId(), null, null, null, null, message.batchId().toString(),
                AuditOutcome.SUCCESS,
                "Validation completed: " + message.passedCount() + " passed, " + message.failedCount() + " failed",
                Map.of("totalRowsReceived", message.totalRowsReceived(), "passedCount", message.passedCount(),
                        "failedCount", message.failedCount())));

        BatchUploadResult result = new BatchUploadResult();
        result.setBatchId(message.batchId());
        result.setProcessId(owner.processId());
        result.setTemplateId(owner.templateId());
        result.setStatus(message.status());
        result.setTotalRowsReceived(message.totalRowsReceived());
        result.setPassedCount(message.passedCount());
        result.setFailedCount(message.failedCount());
        result.setWarningCount(message.warningCount());
        batchUploadResultRepository.save(result);

        List<BatchUploadResultRow> rowEntities = rows.stream().map(row -> {
            BatchUploadResultRow entity = new BatchUploadResultRow();
            entity.setId(IdGenerator.generate("bres"));
            entity.setBatchId(message.batchId());
            entity.setRowNumber(row.rowNumber());
            entity.setRowData(JsonColumnMapper.write(row.rowData()));
            entity.setErrors(JsonColumnMapper.write(row.errors()));
            entity.setRowStatus(row.rowStatus());
            return entity;
        }).toList();
        batchUploadResultRowRepository.saveAll(rowEntities);

        attempt.ifPresent(a -> completeAttempt(a, message, rows));

        configLockService.release(message.batchId().toString());
        log.debug("Batch validation completion recorded: batchId={}, rowCount={}", message.batchId(), rowEntities.size());
    }

    private BatchOwner resolveUploadFileOwner(java.util.UUID batchId) {
        UploadFile uploadFile = uploadFileRepository.findFirstByJobId(batchId.toString())
                .orElseThrow(() -> new ResourceNotFoundException("No upload_attempts or upload_files row for batchId " + batchId));
        return new BatchOwner(uploadFile.getUploadedBy(), uploadFile.getProcessId(), uploadFile.getTemplateId());
    }

    /** Transitions the owning {@link UploadAttempt} to {@code READY_FOR_DECISION} with its
     * validation outcome frozen on — the upload-attempt-flow half of what this listener does. */
    private void completeAttempt(UploadAttempt attempt, BatchValidationCompletedMessage message,
            List<ValidationServiceRowsResponse.Row> rows) {
        ValidationSummaryResponse summary = new ValidationSummaryResponse(
                message.totalRowsReceived(), message.passedCount(), message.failedCount(), message.warningCount());
        attempt.setSummary(JsonColumnMapper.write(summary));
        attempt.setIssues(JsonColumnMapper.write(extractIssues(rows)));
        attempt.setValidatedObjectKey(promoteValidatedCopy(attempt));
        attempt.setStatus(UploadAttemptStatus.READY_FOR_DECISION);
        uploadAttemptRepository.save(attempt);
    }

    private List<ValidationIssueResponse> extractIssues(List<ValidationServiceRowsResponse.Row> rows) {
        List<ValidationIssueResponse> issues = new ArrayList<>();
        for (ValidationServiceRowsResponse.Row row : rows) {
            if (row.errors() == null) {
                continue;
            }
            for (Map<String, Object> error : row.errors()) {
                if (issues.size() >= MAX_ISSUES_PER_ATTEMPT) {
                    return issues;
                }
                issues.add(new ValidationIssueResponse(
                        row.rowNumber() == null ? 0 : row.rowNumber(),
                        asString(error.get("field")),
                        parseSeverity(error.get("severity")),
                        asString(error.get("ruleId")),
                        asString(error.get("ruleType")),
                        asString(error.get("actualValue")),
                        asString(error.get("expected")),
                        asString(error.get("message"))));
            }
        }
        return issues;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private ValidationSeverity parseSeverity(Object value) {
        if (value == null) {
            return ValidationSeverity.ERROR;
        }
        try {
            return ValidationSeverity.valueOf(value.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ValidationSeverity.ERROR;
        }
    }

    /**
     * Re-stages the raw upload to the {@code validated} interim-storage stage (upload-api-contract.md
     * §6) via an S3 CopyObject — best-effort: a failure here logs and leaves {@code
     * validatedObjectKey} null rather than rolling back the already-recorded validation results.
     */
    private String promoteValidatedCopy(UploadAttempt attempt) {
        if (attempt.getRawObjectKey() == null) {
            return null;
        }
        Optional<StorageConfig> config = storageConfigRepository.findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active);
        if (config.isEmpty()) {
            log.warn("No active AWS_S3 storage connection — cannot promote validated copy for attempt {}", attempt.getUploadAttemptId());
            return null;
        }
        String validatedKey = UploadObjectKeys.validated(attempt.getTemplateId(), attempt.getProcessId(), attempt.getOriginalFilename());
        try (S3Client client = S3ClientFactory.build(config.get())) {
            client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(config.get().getBucketName())
                    .sourceKey(attempt.getRawObjectKey())
                    .destinationBucket(config.get().getBucketName())
                    .destinationKey(validatedKey)
                    .build());
            return validatedKey;
        } catch (S3Exception e) {
            log.error("Failed to promote validated copy for attempt {}: {}", attempt.getUploadAttemptId(), e.getMessage(), e);
            return null;
        }
    }

    private record BatchOwner(String actorId, String processId, String templateId) {
    }
}
