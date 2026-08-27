package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.BatchChunkMessage;
import in.qualtechedge.qcp.templates.dto.request.BatchPublishRequest;
import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.dto.request.RowPayload;
import in.qualtechedge.qcp.templates.dto.request.ValidationRuleMessage;
import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.TemplateField;
import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import in.qualtechedge.qcp.templates.mapper.TemplateMapper;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.properties.KafkaBatchProperties;
import in.qualtechedge.qcp.templates.repository.TemplateFieldRepository;
import in.qualtechedge.qcp.templates.repository.TemplateRepository;
import in.qualtechedge.qcp.templates.repository.TemplateValidationRuleRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.BatchChunkPublisher;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import in.qualtechedge.qcp.templates.utils.TemplateFieldRemapper;
import in.qualtechedge.qcp.templates.utils.UploadFileRowReader;
import java.io.IOException;
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
import org.apache.kafka.common.PartitionInfo;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Reads the just-uploaded file back off local disk (staged by the caller — {@link UploadS3Worker}
 * for the raw-upload flow, or the upload-attempt flow's own S3 download-to-temp step) and
 * republishes its rows to validation-service in {@link KafkaBatchProperties#getBatchChunkSize()}-row
 * chunks, spread round-robin across every partition of the topic (see {@link #partition}) instead
 * of pinning one batch's chunks to a single partition — a lakh-row batch's many chunks would
 * otherwise serialize through one consumer thread while the topic's other partitions sit idle.
 * <p>
 * Every chunk of one batch shares {@code chunkSequence} starting at 0; the last chunk sent —
 * whatever it contains, including empty — carries {@code lastChunk = true}. Because chunks can now
 * be consumed out of order across partitions, {@code lastChunk} only means "no more chunks follow
 * this one" — it is NOT a reliable "the batch is fully processed" signal on its own; validation-service
 * must track completion by counting processed chunks against a known total, not by trusting
 * whichever chunk happens to carry the flag.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchChunkPublisherImpl implements BatchChunkPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final KafkaBatchProperties kafkaBatchProperties;
    private final TemplateRepository templateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final TemplateValidationRuleRepository templateValidationRuleRepository;
    private final TemplateMapper templateMapper;
    private final AuditEventService auditEventService;
    private final ConfigLockService configLockService;

    @Override
    public boolean publish(BatchPublishRequest request, Path file) {
        try {
            UploadFormatKey format = UploadFileRowReader.detectFormat(request.originalFilename());
            UUID batchId = request.batchId();
            String templateCode = templateRepository.findById(request.templateId())
                    .map(Template::getTemplateCode)
                    .orElse(request.templateId());
            // Snapshotted once per batch — attached only to chunk 0 (sendChunk), not repeated on
            // every chunk message.
            List<ValidationRuleMessage> rules = templateMapper.toValidationRuleMessages(
                    templateValidationRuleRepository.findByTemplateIdOrderBySortOrder(request.templateId()));
            // Rows come off UploadFileRowReader keyed by the file's literal header text
            // (source_column) — remap to target_field here so keys match what rules/downstream
            // consumers (validation-service) expect. Columns with no field mapping are dropped.
            Map<String, String> sourceToTargetField = templateFieldRepository.findByTemplateIdOrderBySortOrder(request.templateId())
                    .stream()
                    .collect(Collectors.toMap(TemplateField::getSourceColumn, TemplateField::getTargetField));

            String topic = kafkaBatchProperties.getTopics().getBatchChunk();
            int numPartitions = numPartitions(topic);

            ChunkBuffer buffer = new ChunkBuffer();
            UploadFileRowReader.readRows(file, format, (rowNumber, data) -> {
                buffer.rows.add(new RowPayload(rowNumber, TemplateFieldRemapper.remap(data, sourceToTargetField)));
                buffer.totalRows++;
                if (buffer.rows.size() >= kafkaBatchProperties.getBatchChunkSize()) {
                    sendChunk(topic, numPartitions, batchId, request, templateCode, rules, buffer, false);
                }
            });
            sendChunk(topic, numPartitions, batchId, request, templateCode, rules, buffer, true);

            log.info("Published batch chunks to Kafka: batchId={}, processId={}, chunks={}, rows={}, ruleCount={}",
                    batchId, request.processId(), buffer.chunkSequence, buffer.totalRows, rules.size());
            recordEnqueuePushed(request, buffer);
            return true;
        } catch (RuntimeException | IOException e) {
            log.error("Failed to publish batch chunks: batchId={}, processId={}", request.batchId(), request.processId(), e);
            recordEnqueueFailed(request, e);
            configLockService.release(request.lockRef());
            return false;
        }
    }

    private void sendChunk(String topic, int numPartitions, UUID batchId, BatchPublishRequest request, String templateCode,
            List<ValidationRuleMessage> rules, ChunkBuffer buffer, boolean lastChunk) throws IOException {
        // rules is sent on every chunk, not just chunkSequence == 0 — see BatchChunkMessage's javadoc.
        BatchChunkMessage message = new BatchChunkMessage(batchId, HostContext.getCurrentTenant(),
                request.processId(), templateCode, request.actorId(), request.originalFilename(),
                buffer.chunkSequence, lastChunk, List.copyOf(buffer.rows), rules);
        int partition = partition(batchId, buffer.chunkSequence, numPartitions);
        try {
            kafkaTemplate.send(topic, partition, batchId.toString(), message)
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while publishing chunk " + buffer.chunkSequence + " for batch " + batchId, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Failed to publish chunk " + buffer.chunkSequence + " for batch " + batchId, e);
        }
        buffer.chunkSequence++;
        buffer.rows.clear();
    }

    /**
     * Rotates one batch's own chunks round-robin across every partition of the topic — keying
     * every chunk by {@code batchId} alone (the pre-fix behavior) pins the whole batch to a single
     * partition via key-hashing, which is exactly the imbalance this method exists to avoid. Folding
     * {@code batchId.hashCode()} into the modulo also spreads different batches' first chunks across
     * different starting partitions, so many small concurrent uploads don't all land on partition 0.
     */
    private int partition(UUID batchId, int chunkSequence, int numPartitions) {
        return Math.floorMod(batchId.hashCode() + chunkSequence, numPartitions);
    }

    /** Falls back to a single partition if the topic's metadata isn't available yet (e.g. an
     * auto-created topic that hasn't materialized) — matches the pre-fix behavior in that case. */
    private int numPartitions(String topic) {
        List<PartitionInfo> partitions = kafkaTemplate.partitionsFor(topic);
        return partitions == null || partitions.isEmpty() ? 1 : partitions.size();
    }

    private void recordEnqueuePushed(BatchPublishRequest request, ChunkBuffer buffer) {
        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.ENQUEUE_PUSHED,
                request.actorId(),
                null,
                request.processId(),
                request.templateId(),
                null,
                null,
                null,
                null,
                request.batchId().toString(),
                AuditOutcome.SUCCESS,
                "Published " + buffer.chunkSequence + " chunk(s), " + buffer.totalRows + " row(s) for batch " + request.batchId(),
                Map.of("chunkCount", buffer.chunkSequence, "rowCount", buffer.totalRows)));
    }

    private void recordEnqueueFailed(BatchPublishRequest request, Exception cause) {
        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.ENQUEUE_FAILED,
                request.actorId(),
                null,
                request.processId(),
                request.templateId(),
                null,
                null,
                null,
                null,
                request.batchId().toString(),
                AuditOutcome.FAILURE,
                "Failed to publish batch " + request.batchId() + ": " + cause.getMessage(),
                null));
    }

    /** Mutable per-batch accumulator — buffers rows until a chunk is full, then is cleared and reused. */
    private static final class ChunkBuffer {
        private final List<RowPayload> rows = new ArrayList<>();
        private int chunkSequence = 0;
        private int totalRows = 0;
    }
}
