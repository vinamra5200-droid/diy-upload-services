package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.dto.request.PostLoadActionChunkMessage;
import in.qualtechedge.qcp.templates.dto.request.RowPayload;
import in.qualtechedge.qcp.templates.entity.QueueConfig;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.TemplateField;
import in.qualtechedge.qcp.templates.entity.UploadJob;
import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import in.qualtechedge.qcp.templates.enums.KafkaMode;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.properties.KafkaBatchProperties;
import in.qualtechedge.qcp.templates.repository.QueueConfigRepository;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.repository.TemplateFieldRepository;
import in.qualtechedge.qcp.templates.repository.UploadJobRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.PostLoadActionDispatcher;
import in.qualtechedge.qcp.templates.utils.S3ClientFactory;
import in.qualtechedge.qcp.templates.utils.TemplateFieldRemapper;
import in.qualtechedge.qcp.templates.utils.UploadFileRowReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Reads a job's completed file back off S3 (the {@code pending_processing} copy at
 * {@link UploadJob#getCompletedFileKey()}) and streams its rows to the resolved topic (see
 * {@link #resolveTarget}) in {@link KafkaBatchProperties#getPostLoadActionChunkSize()}-row chunks,
 * keyed by the job id so they land on one partition and are consumed in order — same shape as
 * {@link BatchChunkPublisherImpl}, a different leg of the pipeline (job -> post-load-action, not
 * raw file -> validation-service). Publishes via {@link KafkaProducerRegistry} rather than a
 * single injected {@code KafkaTemplate}, so a queue config's ({@code useExisting}) full producer
 * settings — {@code clientId}, {@code acks}, {@code batchSizeKb}, {@code lingerMs}, {@code
 * compressionType}, {@code retries}, {@code maxInFlightRequests} — or a template's ({@code
 * custom}) own {@code kafkaBootstrapServers} is honored, not just the shared default cluster and
 * producer config. See {@link ProducerOverride}.
 * {@link in.qualtechedge.qcp.templates.service.UploadJobService#dispatch} is the only caller; it
 * has already flipped the job to {@code PROCESSING} and confirmed the template's post-load action
 * is {@code kafka} before this runs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostLoadActionDispatcherImpl implements PostLoadActionDispatcher {

    private final KafkaProducerRegistry kafkaProducerRegistry;
    private final KafkaBatchProperties kafkaBatchProperties;
    private final UploadJobRepository uploadJobRepository;
    private final StorageConfigRepository storageConfigRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final QueueConfigRepository queueConfigRepository;
    private final AuditEventService auditEventService;

    @Override
    public boolean dispatch(UploadJob job, Template template) {
        Path tempFile = null;
        ResolvedTarget target = null;
        try {
            target = resolveTarget(template);
            StorageConfig config = storageConfigRepository.findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                    .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));

            String filename = filenameOf(job.getCompletedFileKey());
            // Path.of, not Files.createTempFile: the latter creates the file on disk immediately,
            // and S3Client.getObject(request, Path) refuses to overwrite an existing destination
            // (ResponseTransformer's Files.copy has no REPLACE_EXISTING) — every dispatch failed
            // with FileAlreadyExistsException until this matched UploadAttemptServiceImpl's own
            // tempFilePath(), which never pre-creates the file for exactly this reason.
            tempFile = Path.of(System.getProperty("java.io.tmpdir"), "post-load-action-" + UUID.randomUUID() + "-" + filename);
            downloadFromS3(config, job.getCompletedFileKey(), tempFile);

            UploadFormatKey format = UploadFileRowReader.detectFormat(filename);
            Map<String, String> sourceToTargetField = templateFieldRepository.findByTemplateIdOrderBySortOrder(template.getTemplateId())
                    .stream()
                    .collect(Collectors.toMap(TemplateField::getSourceColumn, TemplateField::getTargetField));

            ChunkBuffer buffer = new ChunkBuffer();
            String tenantCode = HostContext.getCurrentTenant();
            ResolvedTarget finalTarget = target;
            UploadFileRowReader.readRows(tempFile, format, (rowNumber, data) -> {
                buffer.rows.add(new RowPayload(rowNumber, TemplateFieldRemapper.remap(data, sourceToTargetField)));
                buffer.totalRows++;
                if (buffer.rows.size() >= kafkaBatchProperties.getPostLoadActionChunkSize()) {
                    sendChunk(job, finalTarget, tenantCode, buffer, false);
                }
            });
            sendChunk(job, target, tenantCode, buffer, true);

            job.setQueueJobRef(job.getJobId());
            job.setStatus(JobStatus.PROCESSING);
            uploadJobRepository.save(job);

            log.info("Dispatched job to Kafka: jobId={}, topic={}, chunks={}, rows={}",
                    job.getJobId(), target.topic(), buffer.chunkSequence, buffer.totalRows);
            recordDispatchPushed(job, target.topic(), buffer);
            return true;
        } catch (RuntimeException | IOException e) {
            log.error("Failed to dispatch job to Kafka: jobId={}", job.getJobId(), e);
            job.setStatus(JobStatus.FAILED);
            uploadJobRepository.save(job);
            recordDispatchFailed(job, target == null ? null : target.topic(), e);
            return false;
        } finally {
            deleteQuietly(tempFile);
        }
    }

    /**
     * {@code kafkaMode = useExisting} binds a saved, checker-approved {@link QueueConfig} —
     * resolve its {@code topicName} instead of the template's own {@code kafkaTopic}, and refuse
     * to publish if that queue config isn't {@code active} (its topic may not even exist on the
     * broker yet). {@code custom}/{@code null} keeps the direct {@code kafkaTopic}/{@code
     * kafkaBootstrapServers} behavior — a blank {@code kafkaBootstrapServers} means the shared
     * cluster, see {@link KafkaProducerRegistry}.
     */
    private ResolvedTarget resolveTarget(Template template) {
        if (template.getKafkaMode() == KafkaMode.useExisting) {
            if (template.getKafkaQueueConfigId() == null) {
                throw new IllegalStateException("Template " + template.getTemplateId()
                        + " has kafkaMode=useExisting but no kafkaQueueConfigId");
            }
            QueueConfig queueConfig = queueConfigRepository.findById(template.getKafkaQueueConfigId())
                    .orElseThrow(() -> new IllegalStateException("Template " + template.getTemplateId()
                            + "'s bound queue config " + template.getKafkaQueueConfigId() + " no longer exists"));
            if (queueConfig.getStatus() != ConfigStatus.active) {
                throw new IllegalStateException("Template " + template.getTemplateId() + "'s bound queue config "
                        + queueConfig.getQueueConfigId() + " is " + queueConfig.getStatus() + ", not active");
            }
            return new ResolvedTarget(queueConfig.getTopicName(), ProducerOverride.of(queueConfig));
        }
        if (template.getKafkaTopic() == null || template.getKafkaTopic().isBlank()) {
            throw new IllegalStateException("Template " + template.getTemplateId() + " has no kafkaTopic configured");
        }
        return new ResolvedTarget(template.getKafkaTopic(), ProducerOverride.bootstrapServersOnly(template.getKafkaBootstrapServers()));
    }

    private void sendChunk(UploadJob job, ResolvedTarget target, String tenantCode, ChunkBuffer buffer, boolean lastChunk) {
        PostLoadActionChunkMessage message = new PostLoadActionChunkMessage(job.getJobId(), tenantCode,
                job.getProcessCode(), job.getTemplateCode(), job.getTemplateVersion(),
                buffer.chunkSequence, lastChunk, job.getTotalRecords(), List.copyOf(buffer.rows));
        try {
            kafkaProducerRegistry.get(target.producerOverride())
                    .send(target.topic(), job.getJobId(), message).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while dispatching chunk " + buffer.chunkSequence + " for job " + job.getJobId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to dispatch chunk " + buffer.chunkSequence + " for job " + job.getJobId(), e);
        }
        buffer.chunkSequence++;
        buffer.rows.clear();
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

    private String filenameOf(String objectKey) {
        int lastSlash = objectKey.lastIndexOf('/');
        return lastSlash < 0 ? objectKey : objectKey.substring(lastSlash + 1);
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete temp file {}", file, e);
        }
    }

    private void recordDispatchPushed(UploadJob job, String topic, ChunkBuffer buffer) {
        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.JOB_DISPATCH_PUSHED, job.getMakerUserId(), null, job.getProcessCode(),
                job.getTemplateCode(), job.getTemplateVersion(), null, job.getUploadAttemptId(), job.getSubmissionId(),
                job.getJobId(), AuditOutcome.SUCCESS,
                "Dispatched " + buffer.chunkSequence + " chunk(s), " + buffer.totalRows + " row(s) for job " + job.getJobId()
                        + " to topic " + topic,
                Map.of("chunkCount", buffer.chunkSequence, "rowCount", buffer.totalRows, "topic", topic)));
    }

    private void recordDispatchFailed(UploadJob job, String topic, Exception cause) {
        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.JOB_DISPATCH_FAILED, job.getMakerUserId(), null, job.getProcessCode(),
                job.getTemplateCode(), job.getTemplateVersion(), null, job.getUploadAttemptId(), job.getSubmissionId(),
                job.getJobId(), AuditOutcome.FAILURE,
                "Failed to dispatch job " + job.getJobId() + " to topic " + topic + ": " + cause.getMessage(),
                null));
    }

    /** Mutable per-job accumulator — buffers rows until a chunk is full, then is cleared and reused. */
    private static final class ChunkBuffer {
        private final List<RowPayload> rows = new ArrayList<>();
        private int chunkSequence = 0;
        private int totalRows = 0;
    }

    /** {@code producerOverride} is {@link ProducerOverride#NONE} for the shared cluster/producer — see {@link KafkaProducerRegistry#get}. */
    private record ResolvedTarget(String topic, ProducerOverride producerOverride) {
    }
}
