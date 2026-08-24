package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import in.qualtechedge.qcp.templates.enums.UploadDecision;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import java.time.OffsetDateTime;
import java.util.List;

/** {@code UploadAttempt} shape (upload-api-contract.md §2.1). */
public record UploadAttemptResponse(
        String uploadAttemptId,
        String processId,
        String processName,
        String templateId,
        String templateCode,
        String templateVersion,
        String makerUserId,
        String originalFilename,
        UploadFormatKey uploadFormat,
        long fileSizeBytes,
        String originalFileChecksumSha256,
        String rawObjectKey,
        String validatedObjectKey,
        UploadAttemptStatus status,
        ValidationSummaryResponse summary,
        List<ValidationIssueResponse> issues,
        UploadDecision decision,
        OffsetDateTime decidedAt,
        int timeoutMinutes,
        boolean makerCheckerEnabled,
        boolean validationsEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
