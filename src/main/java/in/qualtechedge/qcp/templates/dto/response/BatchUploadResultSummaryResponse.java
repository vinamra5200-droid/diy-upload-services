package in.qualtechedge.qcp.templates.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Summary of one validation-service batch run, pulled from the local
 * {@code batch_upload_results} copy populated by {@code BatchValidationCompletedListener}.
 */
public record BatchUploadResultSummaryResponse(
        UUID batchId,
        String status,
        Integer totalRowsReceived,
        Integer passedCount,
        Integer failedCount,
        Integer warningCount,
        OffsetDateTime receivedAt
) {
}
