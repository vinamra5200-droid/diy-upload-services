package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

/**
 * Request body POSTed by diy-validation-service once it finishes the last chunk of a batch (the
 * mirror image of {@link BatchChunkMessage} — this repo consumes it as an HTTP call instead of a
 * Kafka message). Handled by {@code controller.BatchUploadController} to record the {@code
 * VALIDATION_COMPLETED} audit event, pull row-wise results, and release the process's config lock.
 * Field-for-field mirror of diy-validation-service's own {@code BatchValidationCompletedNotification}.
 */
public record BatchValidationCompletedRequest(
        @NotNull UUID batchId,
        @NotBlank String tenantCode,
        @NotBlank String status,
        @NotNull @PositiveOrZero Integer totalRowsReceived,
        @NotNull @PositiveOrZero Integer passedCount,
        @NotNull @PositiveOrZero Integer failedCount,
        List<FailedChunkSummary> failedChunks
) {
    /**
     * One chunk that permanently failed processing on the validation-service side (retries
     * exhausted, routed to its Kafka dead-letter topic) and so was never actually validated —
     * completion no longer waits on every chunk succeeding, only on every chunk being accounted
     * for, so these rows need to be surfaced to the maker as unprocessed rather than silently
     * missing from the pass/fail counts.
     */
    public record FailedChunkSummary(
            Integer chunkSequence,
            Integer rowCount,
            Integer firstRowNumber,
            Integer lastRowNumber,
            String errorMessage
    ) {
    }
}
