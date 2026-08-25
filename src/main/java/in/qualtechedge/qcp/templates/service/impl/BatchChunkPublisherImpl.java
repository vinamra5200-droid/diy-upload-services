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
import in.qualtechedge.qcp.templates.utils.UploadFileRowReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Reads the just-uploaded file back off local disk (staged by the caller — {@link UploadS3Worker}
 * for the raw-upload flow, or the upload-attempt flow's own S3 download-to-temp step) and
 * republishes its rows to validation-service in {@link KafkaBatchProperties#getBatchChunkSize()}-row
 * chunks, all keyed by the batch id so they land on one Kafka partition and are consumed in order.
 * <p>
 * Every chunk of one batch shares {@code chunkSequence} starting at 0; the last chunk sent —
 * whatever it contains, including empty — carries {@code lastChunk = true} so validation-service
 * knows the batch is complete even for a zero-row file.
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

            ChunkBuffer buffer = new ChunkBuffer();
            UploadFileRowReader.readRows(file, format, (rowNumber, data) -> {
                buffer.rows.add(new RowPayload(rowNumber, remapToTargetFields(data, sourceToTargetField)));
                buffer.totalRows++;
                if (buffer.rows.size() >= kafkaBatchProperties.getBatchChunkSize()) {
                    sendChunk(batchId, request, templateCode, rules, buffer, false);
                }
            });
            sendChunk(batchId, request, templateCode, rules, buffer, true);

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

    private Map<String, Object> remapToTargetFields(Map<String, Object> sourceKeyedData, Map<String, String> sourceToTargetField) {
        Map<String, Object> remapped = new LinkedHashMap<>();
        sourceKeyedData.forEach((sourceColumn, value) -> {
            String targetField = sourceToTargetField.get(sourceColumn);
            if (targetField != null) {
                remapped.put(targetField, value);
            }
        });
        return remapped;
    }

    private void sendChunk(UUID batchId, BatchPublishRequest request, String templateCode, List<ValidationRuleMessage> rules,
            ChunkBuffer buffer, boolean lastChunk) throws IOException {
        BatchChunkMessage message = new BatchChunkMessage(batchId, HostContext.getCurrentTenant(),
                request.processId(), templateCode, request.actorId(), request.originalFilename(),
                buffer.chunkSequence, lastChunk, List.copyOf(buffer.rows), buffer.chunkSequence == 0 ? rules : null);
        try {
            kafkaTemplate.send(kafkaBatchProperties.getTopics().getBatchChunk(), batchId.toString(), message)
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
