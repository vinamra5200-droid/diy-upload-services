package in.qualtechedge.qcp.templates.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Summary of one validation-service batch run, pulled from the local
 * {@code batch_upload_results} copy populated by {@code BatchValidationCompletedListener}.
 * {@code resultS3Bucket}/{@code resultS3Key} are null until {@code ValidatedResultS3Exporter}
 * finishes writing the row-by-row CSV to S3 — the UI can offer a download link once both are set.
 */
public record BatchUploadResultSummaryResponse(
        UUID batchId,
        String status,
        Integer totalRowsReceived,
        Integer passedCount,
        Integer failedCount,
        Integer warningCount,
        OffsetDateTime receivedAt,
        String resultS3Bucket,
        String resultS3Key
) {
}
