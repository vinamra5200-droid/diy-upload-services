package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.ConsumerCallbackBatchesResponse;
import in.qualtechedge.qcp.templates.dto.response.CallbackBatchResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobCallbackSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationRowResponse;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.UploadAttempt;
import in.qualtechedge.qcp.templates.entity.UploadJob;
import in.qualtechedge.qcp.templates.entity.UploadJobCallbackResult;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import in.qualtechedge.qcp.templates.enums.PostLoadActionType;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.exception.UnprocessableEntityException;
import in.qualtechedge.qcp.templates.mapper.UploadJobMapper;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.repository.TemplateRepository;
import in.qualtechedge.qcp.templates.repository.UploadAttemptRepository;
import in.qualtechedge.qcp.templates.repository.UploadJobCallbackResultRepository;
import in.qualtechedge.qcp.templates.repository.UploadJobRepository;
import in.qualtechedge.qcp.templates.service.ConsumerCallbackResultsClient;
import in.qualtechedge.qcp.templates.service.UploadJobService;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import in.qualtechedge.qcp.templates.utils.S3ClientFactory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadJobServiceImpl implements UploadJobService {

    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final UploadJobRepository uploadJobRepository;
    private final UploadAttemptRepository uploadAttemptRepository;
    private final TemplateRepository templateRepository;
    private final StorageConfigRepository storageConfigRepository;
    private final UploadJobCallbackResultRepository uploadJobCallbackResultRepository;
    private final UploadJobMapper uploadJobMapper;
    private final PostLoadActionDispatchWorker postLoadActionDispatchWorker;
    private final ConsumerCallbackResultsClient consumerCallbackResultsClient;

    @Override
    @Transactional(readOnly = true)
    public List<UploadJobResponse> listByMaker(String makerUserId) {
        return uploadJobRepository.findByMakerUserIdOrderByCreatedAtDesc(makerUserId).stream()
                .map(uploadJobMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UploadJobResponse getById(String jobId, String actorId) {
        UploadJob job = uploadJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
        assertOwnership(job, actorId);
        return uploadJobMapper.toResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedDownloadResponse download(String jobId, String actorId) {
        UploadJob job = uploadJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
        assertOwnership(job, actorId);
        StorageConfig config = storageConfigRepository.findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));
        // Once processing has actually finished, ProcessedResultS3Exporter's per-row CSV (original
        // columns plus status/api_response) is the file "download processed" means — completedFileKey
        // is only the passed-only rows that went INTO dispatch, with no outcome attached yet. Before
        // that export lands (or if it failed), fall back to completedFileKey rather than 404 — a
        // maker clicking download mid-processing should still get something.
        String key = uploadJobCallbackResultRepository.findById(jobId)
                .map(UploadJobCallbackResult::getResultS3Key)
                .filter(resultKey -> resultKey != null && !resultKey.isBlank())
                .orElse(job.getCompletedFileKey());
        PresignedGetObjectRequest presigned = S3ClientFactory.presign(config, key, DOWNLOAD_URL_EXPIRY);
        return new PresignedDownloadResponse(presigned.url().toString(), OffsetDateTime.now().plus(DOWNLOAD_URL_EXPIRY));
    }

    @Override
    public UploadJobResponse dispatch(String jobId, String actorId) {
        // Deliberately not @Transactional: uploadJobRepository.save() below must fully commit
        // (its own, Spring-Data-managed transaction) before the @Async worker below is handed the
        // job — otherwise the worker's own save() at the end of a fast dispatch could race an
        // enclosing transaction's commit here and have its PROCESSING/FAILED result overwritten.
        UploadJob job = uploadJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
        if (!job.getMakerUserId().equals(actorId)) {
            throw new AccessDeniedException("Job " + jobId + " does not belong to actor " + actorId);
        }
        if (job.getStatus() != JobStatus.QUEUED) {
            throw new ConflictException("Job " + jobId + " is already " + job.getStatus() + " — it can only be dispatched once");
        }
        UploadAttempt attempt = uploadAttemptRepository.findById(job.getUploadAttemptId())
                .orElseThrow(() -> new ResourceNotFoundException("Upload attempt not found with id: " + job.getUploadAttemptId()));
        Template template = templateRepository.findById(attempt.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + attempt.getTemplateId()));
        if (template.getPostLoadActionType() != PostLoadActionType.kafka) {
            throw new UnprocessableEntityException("Template " + template.getTemplateId()
                    + "'s post-load action is " + template.getPostLoadActionType() + ", not kafka");
        }

        job.setStatus(JobStatus.PROCESSING);
        UploadJob saved = uploadJobRepository.save(job);
        postLoadActionDispatchWorker.run(HostContext.getCurrentTenant(), saved, template);
        return uploadJobMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UploadJobCallbackSummaryResponse getCallbackSummary(String jobId, String actorId) {
        UploadJob job = uploadJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
        if (!job.getMakerUserId().equals(actorId)) {
            throw new AccessDeniedException("Job " + jobId + " does not belong to actor " + actorId);
        }
        UploadJobCallbackResult result = uploadJobCallbackResultRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Callback results are not available yet for job " + jobId));
        return new UploadJobCallbackSummaryResponse(result.getJobId(), result.getStatus(), result.getTotalBatches(),
                result.getSuccessCount(), result.getFailedCount(), result.getReceivedAt(),
                result.getResultS3Bucket(), result.getResultS3Key());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CallbackBatchResponse> getCallbackBatches(String jobId, String actorId, String outcome, Pageable pageable) {
        UploadJob job = uploadJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
        assertOwnership(job, actorId);
        ConsumerCallbackBatchesResponse.Data data = consumerCallbackResultsClient.fetchBatchesPage(
                jobId, HostContext.getCurrentTenant(), outcome, pageable.getPageNumber(), pageable.getPageSize());
        List<CallbackBatchResponse> content = data.content().stream()
                .map(batch -> new CallbackBatchResponse(batch.chunkSequence(), batch.apiConfigId(), batch.outcome(),
                        batch.httpStatusCode(), batch.attemptCount(), batch.errorMessage(), batch.rowCount(), batch.attemptedAt()))
                .toList();
        PageResponse.PageMeta meta = new PageResponse.PageMeta(data.page().number(), data.page().size(),
                data.page().totalElements(), data.page().totalPages());
        return new PageResponse<>(content, meta);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ValidationRowResponse> getJobRows(String jobId, String actorId, String rowStatus, String search,
            Pageable pageable) {
        UploadJob job = uploadJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
        assertOwnership(job, actorId);

        List<ConsumerCallbackBatchesResponse.Batch> batches = new ArrayList<>();
        consumerCallbackResultsClient.streamBatches(jobId, HostContext.getCurrentTenant(), batches::addAll);
        List<CallbackRowRanges.RowRange> ranges = CallbackRowRanges.build(batches);

        String normalizedStatus = rowStatus == null || rowStatus.isBlank() ? null : rowStatus.toUpperCase(Locale.ROOT);
        String normalizedSearch = search == null || search.isBlank() ? null : search.toLowerCase(Locale.ROOT);
        List<CallbackRowRanges.RowRange> filtered = ranges.stream()
                .filter(r -> normalizedStatus == null || normalizedStatus.equals(r.status()))
                .filter(r -> normalizedSearch == null
                        || (r.responseText() != null && r.responseText().toLowerCase(Locale.ROOT).contains(normalizedSearch)))
                .toList();

        long totalElements = filtered.stream().mapToLong(CallbackRowRanges.RowRange::size).sum();
        int totalPages = pageable.getPageSize() == 0 ? 0 : (int) Math.ceil((double) totalElements / pageable.getPageSize());

        List<ValidationRowResponse> content = new ArrayList<>();
        long skip = pageable.getOffset();
        long consumed = 0;
        for (CallbackRowRanges.RowRange range : filtered) {
            long rangeSize = range.size();
            if (consumed + rangeSize <= skip) {
                consumed += rangeSize;
                continue;
            }
            long firstRow = range.startRow() + Math.max(0, skip - consumed);
            for (long rowNumber = firstRow; rowNumber <= range.endRow() && content.size() < pageable.getPageSize(); rowNumber++) {
                content.add(toRowResponse((int) rowNumber, range));
            }
            consumed += rangeSize;
            if (content.size() >= pageable.getPageSize()) {
                break;
            }
        }

        PageResponse.PageMeta meta = new PageResponse.PageMeta(pageable.getPageNumber(), pageable.getPageSize(), totalElements, totalPages);
        return new PageResponse<>(content, meta);
    }

    private ValidationRowResponse toRowResponse(int rowNumber, CallbackRowRanges.RowRange range) {
        List<Map<String, Object>> errors;
        if (range.responseText() == null) {
            errors = List.of();
        } else {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("field", range.httpStatusCode() != null ? "HTTP " + range.httpStatusCode() : null);
            entry.put("ruleType", null);
            entry.put("errorMessage", range.responseText());
            errors = List.of(entry);
        }
        return new ValidationRowResponse(rowNumber, Map.of(), errors, range.status());
    }

    private void assertOwnership(UploadJob job, String actorId) {
        if (!job.getMakerUserId().equals(actorId) && !CurrentActor.hasCrossActorReadAccess()) {
            throw new AccessDeniedException("Job " + job.getJobId() + " does not belong to actor " + actorId);
        }
    }
}
