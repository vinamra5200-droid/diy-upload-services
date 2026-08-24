package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.SubmissionStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** {@code UploadSubmission} shape (upload-api-contract.md §4.2). */
public record UploadSubmissionResponse(
        String submissionId,
        String uploadAttemptId,
        String processId,
        String templateId,
        String processName,
        String templateCode,
        String templateVersion,
        String makerUserId,
        String makerDisplayName,
        String pendingObjectKey,
        InterimStoreProvider storageProvider,
        ValidationSummaryResponse summary,
        List<ValidationIssueResponse> issues,
        String originalFileChecksumSha256,
        SubmissionStatus status,
        String checkerUserId,
        String reviewReason,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
