package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.BatchChunkMessage;
import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.dto.request.RowPayload;
import in.qualtechedge.qcp.templates.dto.request.ValidationRuleMessage;
import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.UploadFile;
import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import in.qualtechedge.qcp.templates.mapper.TemplateMapper;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.properties.KafkaBatchProperties;
import in.qualtechedge.qcp.templates.repository.TemplateRepository;
import in.qualtechedge.qcp.templates.repository.TemplateValidationRuleRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.BatchChunkPublisher;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Reads the just-uploaded file back off local disk (the temp file {@link UploadS3Worker} staged
 * for the S3 PUT, still present when this runs) and republishes its rows to validation-service in
 * {@link KafkaBatchProperties#getBatchChunkSize()}-row chunks, all keyed by the upload's job id so
 * they land on one Kafka partition and are consumed in order.
 * <p>
 * Every chunk of one job shares {@code chunkSequence} starting at 0; the last chunk sent —
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
    private final TemplateValidationRuleRepository templateValidationRuleRepository;
    private final TemplateMapper templateMapper;
    private final AuditEventService auditEventService;
    private final ConfigLockService configLockService;

    @Override
    public void publish(UploadFile record, Path file) {
        try {
            UploadFormatKey format = UploadFileRowReader.detectFormat(record.getOriginalFilename());
            UUID batchId = UUID.fromString(record.getJobId());
            String templateCode = templateRepository.findById(record.getTemplateId())
                    .map(Template::getTemplateCode)
                    .orElse(record.getTemplateId());
            // Snapshotted once per batch — attached only to chunk 0 (sendChunk), not repeated on
            // every chunk message.
            List<ValidationRuleMessage> rules = templateMapper.toValidationRuleMessages(
                    templateValidationRuleRepository.findByTemplateIdOrderBySortOrder(record.getTemplateId()));

            ChunkBuffer buffer = new ChunkBuffer();
            UploadFileRowReader.readRows(file, format, (rowNumber, data) -> {
                buffer.rows.add(new RowPayload(rowNumber, data));
                buffer.totalRows++;
                if (buffer.rows.size() >= kafkaBatchProperties.getBatchChunkSize()) {
                    sendChunk(batchId, record, templateCode, rules, buffer, false);
                }
            });
            sendChunk(batchId, record, templateCode, rules, buffer, true);

            log.info("Published batch chunks to Kafka: jobId={}, uploadId={}, chunks={}, rows={}, ruleCount={}",
                    batchId, record.getUploadId(), buffer.chunkSequence, buffer.totalRows, rules.size());
            recordEnqueuePushed(record, templateCode, buffer);
        } catch (RuntimeException | IOException e) {
            log.error("Failed to publish batch chunks: uploadId={}, jobId={}", record.getUploadId(), record.getJobId(), e);
            recordEnqueueFailed(record, e);
            configLockService.release(record.getJobId());
        }
    }

    private void sendChunk(UUID batchId, UploadFile record, String templateCode, List<ValidationRuleMessage> rules,
            ChunkBuffer buffer, boolean lastChunk) throws IOException {
        BatchChunkMessage message = new BatchChunkMessage(batchId, HostContext.getCurrentTenant(),
                record.getProcessId(), templateCode, record.getUploadedBy(), record.getOriginalFilename(),
                buffer.chunkSequence, lastChunk, List.copyOf(buffer.rows), buffer.chunkSequence == 0 ? rules : null);
        try {
            kafkaTemplate.send(kafkaBatchProperties.getTopics().getBatchChunk(), batchId.toString(), message)
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while publishing chunk " + buffer.chunkSequence + " for job " + batchId, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Failed to publish chunk " + buffer.chunkSequence + " for job " + batchId, e);
        }
        buffer.chunkSequence++;
        buffer.rows.clear();
    }

    private void recordEnqueuePushed(UploadFile record, String templateCode, ChunkBuffer buffer) {
        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.ENQUEUE_PUSHED,
                record.getUploadedBy(),
                null,
                record.getProcessId(),
                templateCode,
                null,
                null,
                null,
                null,
                record.getJobId(),
                AuditOutcome.SUCCESS,
                "Published " + buffer.chunkSequence + " chunk(s), " + buffer.totalRows + " row(s) for job " + record.getJobId(),
                Map.of("chunkCount", buffer.chunkSequence, "rowCount", buffer.totalRows)));
    }

    private void recordEnqueueFailed(UploadFile record, Exception cause) {
        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.ENQUEUE_FAILED,
                record.getUploadedBy(),
                null,
                record.getProcessId(),
                record.getTemplateId(),
                null,
                null,
                null,
                null,
                record.getJobId(),
                AuditOutcome.FAILURE,
                "Failed to publish job " + record.getJobId() + ": " + cause.getMessage(),
                null));
    }

    /** Mutable per-job accumulator — buffers rows until a chunk is full, then is cleared and reused. */
    private static final class ChunkBuffer {
        private final List<RowPayload> rows = new ArrayList<>();
        private int chunkSequence = 0;
        private int totalRows = 0;
    }
}
