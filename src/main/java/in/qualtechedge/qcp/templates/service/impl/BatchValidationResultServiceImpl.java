package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.BatchValidationCompletedRequest;
import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.dto.request.ValidationServiceRowsResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationIssueResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationSummaryResponse;
import in.qualtechedge.qcp.templates.entity.UploadAttempt;
import in.qualtechedge.qcp.templates.entity.UploadFile;
import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import in.qualtechedge.qcp.templates.enums.ValidationSeverity;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.UploadAttemptMapper;
import in.qualtechedge.qcp.templates.repository.BatchUploadResultRepository;
import in.qualtechedge.qcp.templates.repository.UploadAttemptRepository;
import in.qualtechedge.qcp.templates.repository.UploadFileRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.BatchValidationResultService;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import in.qualtechedge.qcp.templates.service.UploadAttemptEventPublisher;
import in.qualtechedge.qcp.templates.service.ValidationServiceResultsClient;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchValidationResultServiceImpl implements BatchValidationResultService {

    /** Caps how many per-row issues an attempt's {@code issues} JSONB column carries. Bounding
     * the {@link #fetchFailedRowIssues} pull to this many failed rows (not the batch's full row
     * count) is what keeps {@link #recordCompletion} fast regardless of batch size — the maker's
     * full, unbounded, filterable/searchable row browsing goes through the on-demand rows
     * endpoints instead (see {@code UploadAttemptController#getRows}), not this list. */
    private static final int MAX_ISSUES_PER_ATTEMPT = 1000;
    private static final int ISSUES_FETCH_PAGE_SIZE = 200;

    private final UploadFileRepository uploadFileRepository;
    private final UploadAttemptRepository uploadAttemptRepository;
    private final BatchUploadResultRepository batchUploadResultRepository;
    private final AuditEventService auditEventService;
    private final ConfigLockService configLockService;
    private final UploadAttemptMapper uploadAttemptMapper;
    private final UploadAttemptEventPublisher uploadAttemptEventPublisher;
    private final ValidationServiceResultsClient validationServiceResultsClient;

    /**
     * Atomically claims {@code message.batchId()} via {@link BatchUploadResultRepository#claim} —
     * the row this inserts is the same {@code batch_upload_results} row {@link #recordCompletion}
     * used to build later, just moved ahead of the expensive row pull so a concurrent or retried
     * duplicate of this callback is turned away here, before it ever calls validation-service.
     */
    @Override
    @Transactional
    public boolean claim(BatchValidationCompletedRequest message) {
        BatchOwner owner = resolveOwner(message.batchId());
        int inserted = batchUploadResultRepository.claim(message.batchId(), owner.processId(), owner.templateId(),
                message.status(), message.totalRowsReceived(), message.passedCount(), message.failedCount());
        if (inserted == 0) {
            log.info("Batch validation-completed callback ignored — already claimed: batchId={}", message.batchId());
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public void unclaim(UUID batchId) {
        batchUploadResultRepository.deleteById(batchId);
        log.warn("Batch validation-completed claim released after a failed recordCompletion: batchId={}", batchId);
    }

    @Override
    @Transactional
    public void recordCompletion(BatchValidationCompletedRequest message) {
        UUID batchId = message.batchId();
        Optional<UploadAttempt> attempt = uploadAttemptRepository.findByBatchId(batchId);
        BatchOwner owner = attempt.<BatchOwner>map(a -> new BatchOwner(a.getMakerUserId(), a.getProcessId(), a.getTemplateId()))
                .orElseGet(() -> resolveUploadFileOwner(batchId));

        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.VALIDATION_COMPLETED, owner.actorId(), null, owner.processId(),
                owner.templateId(), null, null, null, null, batchId.toString(),
                AuditOutcome.SUCCESS,
                "Validation completed: " + message.passedCount() + " passed, " + message.failedCount() + " failed",
                Map.of("totalRowsReceived", message.totalRowsReceived(), "passedCount", message.passedCount(),
                        "failedCount", message.failedCount())));

        List<ValidationIssueResponse> issues = new ArrayList<>();
        fetchFailedRowIssues(batchId, message.tenantCode(), issues);
        appendFailedChunkIssues(issues, message.failedChunks());
        attempt.ifPresent(a -> completeAttempt(a, message, issues));

        configLockService.release(batchId.toString());
        log.debug("Batch validation completion recorded: batchId={}, issueCount={}", batchId, issues.size());
    }

    /**
     * Pulls only failed rows, one page at a time, stopping as soon as {@link #MAX_ISSUES_PER_ATTEMPT}
     * is reached or failed rows run out — never the batch's full row count, unlike the
     * pull-everything this replaced. That's what keeps this fast regardless of batch size: a batch
     * with a million rows but 50 failures pulls at most one page, not a million rows.
     */
    private void fetchFailedRowIssues(UUID batchId, String tenantCode, List<ValidationIssueResponse> issues) {
        int page = 0;
        while (issues.size() < MAX_ISSUES_PER_ATTEMPT) {
            ValidationServiceRowsResponse.Data data = validationServiceResultsClient.fetchRowsPage(
                    batchId, tenantCode, "FAILED", null, null, page, ISSUES_FETCH_PAGE_SIZE);
            if (data.content().isEmpty()) {
                return;
            }
            appendIssues(issues, data.content());
            Integer totalPages = data.page() == null ? null : data.page().totalPages();
            page++;
            if (totalPages != null && page >= totalPages) {
                return;
            }
        }
    }

    private BatchOwner resolveOwner(UUID batchId) {
        return uploadAttemptRepository.findByBatchId(batchId)
                .<BatchOwner>map(a -> new BatchOwner(a.getMakerUserId(), a.getProcessId(), a.getTemplateId()))
                .orElseGet(() -> resolveUploadFileOwner(batchId));
    }

    private BatchOwner resolveUploadFileOwner(UUID batchId) {
        UploadFile uploadFile = uploadFileRepository.findFirstByJobId(batchId.toString())
                .orElseThrow(() -> new ResourceNotFoundException("No upload_attempts or upload_files row for batchId " + batchId));
        return new BatchOwner(uploadFile.getUploadedBy(), uploadFile.getProcessId(), uploadFile.getTemplateId());
    }

    /** Transitions the owning {@link UploadAttempt} to {@code READY_FOR_DECISION} with its
     * validation outcome frozen on — the upload-attempt-flow half of what this listener does.
     * Publishes the SSE "done" event in the same method, right after the save, rather than
     * leaving a poller to notice the new status later — this callback is the one place that
     * knows the final result the moment it's committed.
     * <p>
     * {@code validatedObjectKey} is left null here — it used to be set synchronously to a plain
     * S3 CopyObject of the raw upload (bytes identical to the original, transformations never
     * applied). {@link in.qualtechedge.qcp.templates.service.impl.ValidatedResultS3Exporter},
     * kicked off by {@code BatchUploadController} right after this transaction commits, now fills
     * it in once its transformed-rows CSV export finishes — same "null until ready" contract
     * {@code stage=validated} download already documents (404 if the key isn't set yet). */
    private void completeAttempt(UploadAttempt attempt, BatchValidationCompletedRequest message,
            List<ValidationIssueResponse> issues) {
        ValidationSummaryResponse summary = new ValidationSummaryResponse(
                message.totalRowsReceived(), message.passedCount(), message.failedCount());
        attempt.setSummary(JsonColumnMapper.write(summary));
        attempt.setIssues(JsonColumnMapper.write(issues));
        attempt.setStatus(UploadAttemptStatus.READY_FOR_DECISION);
        UploadAttempt saved = uploadAttemptRepository.save(attempt);
        uploadAttemptEventPublisher.publish(uploadAttemptMapper.toResponse(saved));
    }

    /**
     * Appends one synthetic issue per chunk validation-service gave up on (retries exhausted,
     * routed to its dead-letter topic) — those rows were never actually rule-checked, so there is
     * no per-row {@code ValidationServiceRowsResponse.Row} for them the way {@link #appendIssues}
     * has; this is the only record the maker gets that these rows were skipped rather than passed.
     */
    private void appendFailedChunkIssues(List<ValidationIssueResponse> issues,
            List<BatchValidationCompletedRequest.FailedChunkSummary> failedChunks) {
        if (failedChunks == null) {
            return;
        }
        for (BatchValidationCompletedRequest.FailedChunkSummary chunk : failedChunks) {
            if (issues.size() >= MAX_ISSUES_PER_ATTEMPT) {
                return;
            }
            String rowRange = Objects.equals(chunk.firstRowNumber(), chunk.lastRowNumber())
                    ? "row " + chunk.firstRowNumber()
                    : "rows " + chunk.firstRowNumber() + "-" + chunk.lastRowNumber();
            issues.add(new ValidationIssueResponse(
                    chunk.firstRowNumber() == null ? 0 : chunk.firstRowNumber(),
                    null,
                    ValidationSeverity.ERROR,
                    null,
                    "SYSTEM_ERROR",
                    null,
                    null,
                    "Not validated due to a system processing error (" + rowRange + ", " + chunk.rowCount()
                            + " row(s)) — please re-upload the file if this persists."));
        }
    }

    /**
     * Appends one page's issues onto the running, capped list — called once per page as rows
     * stream in from validation-service, instead of extracting from a fully-materialized row list,
     * so this stays bounded ({@link #MAX_ISSUES_PER_ATTEMPT}) no matter how large the batch is.
     */
    private void appendIssues(List<ValidationIssueResponse> issues, List<ValidationServiceRowsResponse.Row> page) {
        for (ValidationServiceRowsResponse.Row row : page) {
            if (row.errors() == null) {
                continue;
            }
            Map<String, Object> rowData = row.rowData();
            for (Map<String, Object> error : row.errors()) {
                if (issues.size() >= MAX_ISSUES_PER_ATTEMPT) {
                    return;
                }
                String field = asString(error.get("field"));
                issues.add(new ValidationIssueResponse(
                        row.rowNumber() == null ? 0 : row.rowNumber(),
                        field,
                        parseSeverity(error.get("severity")),
                        asString(error.get("ruleId")),
                        asString(error.get("ruleType")),
                        // validation-service's RowError carries no actualValue of its own — the row's
                        // own rowData (also returned alongside errors) is the source of truth for it.
                        field == null || rowData == null ? null : asString(rowData.get(field)),
                        asString(error.get("expected")),
                        // RowError's message field is called `errorMessage` on the wire (see
                        // validation-service's RowError record), not `message`.
                        asString(error.get("errorMessage"))));
            }
        }
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

    private record BatchOwner(String actorId, String processId, String templateId) {
    }
}
