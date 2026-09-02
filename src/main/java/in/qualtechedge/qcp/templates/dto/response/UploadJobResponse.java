package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import java.time.OffsetDateTime;

/** {@code UploadJob} shape (upload-api-contract.md §3.2). {@code rawObjectKey}/
 * {@code validatedObjectKey} are not columns on {@code upload_jobs} itself — they're the owning
 * attempt's own raw/validated stage keys, resolved and populated only where a caller actually
 * needs them (today: ViewerServiceImpl#listJobs, for the viewer dashboard's per-row downloads);
 * every other caller gets {@code null} for both, same as before these fields existed. */
public record UploadJobResponse(
        String jobId,
        String processCode,
        String processName,
        String templateCode,
        String templateVersion,
        String makerUserId,
        String checkerUserId,
        String submissionId,
        String uploadAttemptId,
        UploadFormatKey uploadFormat,
        int totalRecords,
        int passedRecords,
        int failedRecords,
        String completedFileKey,
        String originalObjectKey,
        InterimStoreProvider storageProvider,
        boolean makerCheckerEnabled,
        String originalFileChecksumSha256,
        JobStatus status,
        String queueJobRef,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String rawObjectKey,
        String validatedObjectKey
) {
}
