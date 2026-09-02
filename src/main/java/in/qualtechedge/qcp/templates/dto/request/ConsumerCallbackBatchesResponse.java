package in.qualtechedge.qcp.templates.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Shape of consumer-callback-service's {@code GET /api/v1/internal/callback-jobs/{jobId}/batches}
 * response (its locked {@code APIResponse<PageResponse<CallbackBatchAttemptResponse>>} envelope),
 * as consumed by {@link in.qualtechedge.qcp.templates.service.ConsumerCallbackResultsClient}. Only
 * the fields this repo actually reads are declared — {@code @JsonIgnoreProperties} covers the rest
 * of the envelope regardless of the caller's Jackson fail-on-unknown-properties setting.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsumerCallbackBatchesResponse(
        String status,
        Data data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            List<Batch> content,
            PageMeta page
    ) {
    }

    public record Batch(
            String jobId,
            Integer chunkSequence,
            String apiConfigId,
            String outcome,
            Integer httpStatusCode,
            Integer attemptCount,
            String errorMessage,
            String responseBody,
            Integer rowCount,
            OffsetDateTime attemptedAt
    ) {
    }

    public record PageMeta(
            Integer number,
            Integer size,
            Long totalElements,
            Integer totalPages
    ) {
    }
}
