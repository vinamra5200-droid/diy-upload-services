package in.qualtechedge.qcp.templates.dto.request;

import java.util.UUID;

/**
 * The fields {@link in.qualtechedge.qcp.templates.service.BatchChunkPublisher} needs to chunk a
 * file to validation-service — shared between the raw-upload flow ({@code UploadFile}) and the
 * upload-attempt flow ({@code UploadAttempt}), which own the source file under two different
 * entities. {@code lockRef} is the {@link in.qualtechedge.qcp.templates.service.ConfigLockService}
 * key to release on a publish failure (the raw flow uses its {@code jobId}; the attempt flow uses
 * the same {@code batchId} it acquired the lock under).
 */
public record BatchPublishRequest(
        UUID batchId,
        String processId,
        String templateId,
        String actorId,
        String originalFilename,
        String lockRef
) {
}
