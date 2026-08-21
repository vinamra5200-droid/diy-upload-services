package in.qualtechedge.qcp.templates.consumer;

import in.qualtechedge.qcp.templates.dto.request.BatchValidationCompletedMessage;
import in.qualtechedge.qcp.templates.dto.request.ValidationServiceFailedRowsResponse;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.service.BatchValidationResultService;
import in.qualtechedge.qcp.templates.service.ValidationServiceResultsClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Entry point for validation-service's per-batch completion event — the reverse leg of
 * {@link in.qualtechedge.qcp.templates.service.BatchChunkPublisher} (thin, mirrors the QCP
 * controller rule: business logic delegated to {@link BatchValidationResultService}). Manual ack
 * — only acknowledged once the completion is fully recorded.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchValidationCompletedListener {

    private final ValidationServiceResultsClient validationServiceResultsClient;
    private final BatchValidationResultService batchValidationResultService;

    @KafkaListener(topics = "${qcp.kafka.topics.batch-validation-completed}")
    public void onMessage(BatchValidationCompletedMessage message, Acknowledgment acknowledgment) {
        log.info("Batch validation completed request: batchId={}, status={}", message.batchId(), message.status());
        // HostContext is thread-local (QCC Multi-Tenancy §3) — this listener runs on the Kafka
        // consumer thread, not a request thread, so it must be set here explicitly (same reasoning
        // as UploadS3Worker's @Async hop) and cleared once this message is fully handled.
        HostContext.setCurrentTenant(message.tenantCode());
        try {
            List<ValidationServiceFailedRowsResponse.Row> failedRows =
                    validationServiceResultsClient.fetchAllFailedRows(message.batchId());
            batchValidationResultService.recordCompletion(message, failedRows);
            acknowledgment.acknowledge();
            log.info("Batch validation completed: batchId={}", message.batchId());
        } finally {
            HostContext.clear();
        }
    }
}
