package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.UploadFileStatus;
import java.time.OffsetDateTime;

public record UploadFileResponse(
        String uploadId,
        String processId,
        String templateId,
        String originalFilename,
        String checksumSha256,
        long fileSizeBytes,
        String contentType,
        String bucket,
        String key,
        String etag,
        String jobId,
        UploadFileStatus status,
        String uploadedBy,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
