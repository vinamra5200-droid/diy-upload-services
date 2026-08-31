package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import java.time.OffsetDateTime;

/** {@code UploadJob} shape (upload-api-contract.md §3.2). */
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
        OffsetDateTime updatedAt
) {
}
