package in.qualtechedge.qcp.templates.dto.response;

import java.time.OffsetDateTime;

/**
 * One batch's delivery detail — the drill-down behind a job's aggregate
 * {@link UploadJobCallbackSummaryResponse}, pulled live from consumer-callback-service via
 * {@link in.qualtechedge.qcp.templates.service.ConsumerCallbackResultsClient}, never persisted here.
 */
public record CallbackBatchResponse(
        Integer chunkSequence,
        String apiConfigId,
        String outcome,
        Integer httpStatusCode,
        Integer attemptCount,
        String errorMessage,
        Integer rowCount,
        OffsetDateTime attemptedAt
) {
}
