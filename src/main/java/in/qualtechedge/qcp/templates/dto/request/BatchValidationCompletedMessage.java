package in.qualtechedge.qcp.templates.dto.request;

import java.util.UUID;

/**
 * One Kafka message published by validation-service once it finishes the last chunk of a batch
 * (the mirror image of {@link BatchChunkMessage} — this repo consumes it instead of producing
 * it). Consumed by {@link in.qualtechedge.qcp.templates.consumer.BatchValidationCompletedListener}
 * to record the {@code VALIDATION_COMPLETED} audit event, pull row-wise results, and release the
 * process's config lock.
 */
public record BatchValidationCompletedMessage(
        UUID batchId,
        String tenantCode,
        String status,
        Integer totalRowsReceived,
        Integer passedCount,
        Integer failedCount,
        Integer warningCount
) {
}
