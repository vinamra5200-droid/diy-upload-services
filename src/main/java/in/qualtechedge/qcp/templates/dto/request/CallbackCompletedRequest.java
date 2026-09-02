package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * Request body POSTed by consumer-callback-service once it has attempted the outbound API call for
 * every batch (Kafka chunk) of a job — the reverse leg of {@code PostLoadActionDispatcherImpl}'s
 * Kafka publish, mirroring {@link BatchValidationCompletedRequest} (diy-validation-service's own
 * completion callback) for the outbound-API-delivery leg instead of the validation leg. Handled by
 * {@code controller.UploadJobCallbackController} to record the {@code JOB_CALLBACK_COMPLETED} audit
 * event and move the job out of {@code PROCESSING}.
 */
public record CallbackCompletedRequest(
        @NotBlank String jobId,
        @NotBlank String tenantCode,
        @NotBlank String status,
        @NotNull @PositiveOrZero Integer totalBatches,
        @NotNull @PositiveOrZero Integer successCount,
        @NotNull @PositiveOrZero Integer failedCount,
        List<FailedBatchSummary> failedBatches
) {
    /**
     * One batch (Kafka chunk) whose outbound API call permanently failed (retries exhausted on the
     * consumer-callback-service side) — surfaced so a maker can see which chunks never made it, not
     * just a bare {@code failedCount}.
     */
    public record FailedBatchSummary(
            Integer chunkSequence,
            Integer rowCount,
            Integer httpStatusCode,
            String errorMessage
    ) {
    }
}
