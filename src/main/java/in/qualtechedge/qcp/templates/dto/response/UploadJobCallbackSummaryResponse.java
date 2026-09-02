package in.qualtechedge.qcp.templates.dto.response;

import java.time.OffsetDateTime;

/**
 * Summary of one consumer-callback-service delivery run, pulled from the local
 * {@code upload_job_callback_results} copy populated by {@code controller.UploadJobCallbackController}.
 * Batch-level detail (which chunk, what HTTP status) is not included — it stays in
 * consumer-callback-service's own database. {@code resultS3Bucket}/{@code resultS3Key} are null
 * until {@code ProcessedResultS3Exporter} finishes writing the per-batch JSON export to S3 — the UI
 * can offer a download link once both are set.
 */
public record UploadJobCallbackSummaryResponse(
        String jobId,
        String status,
        Integer totalBatches,
        Integer successCount,
        Integer failedCount,
        OffsetDateTime receivedAt,
        String resultS3Bucket,
        String resultS3Key
) {
}
