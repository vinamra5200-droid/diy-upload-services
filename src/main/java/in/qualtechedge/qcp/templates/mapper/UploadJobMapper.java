package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.entity.UploadJob;
import org.springframework.stereotype.Component;

@Component
public class UploadJobMapper {

    public UploadJobResponse toResponse(UploadJob entity) {
        return toResponse(entity, null, null);
    }

    /** Same shape, plus the owning attempt's raw/validated stage keys — for callers (the viewer
     * dashboard's job list) that already resolved the attempt and want its downloads alongside
     * the job's own without a second round-trip. */
    public UploadJobResponse toResponse(UploadJob entity, String rawObjectKey, String validatedObjectKey) {
        return new UploadJobResponse(
                entity.getJobId(),
                entity.getProcessCode(),
                entity.getProcessName(),
                entity.getTemplateCode(),
                entity.getTemplateVersion(),
                entity.getMakerUserId(),
                entity.getCheckerUserId(),
                entity.getSubmissionId(),
                entity.getUploadAttemptId(),
                entity.getUploadFormat(),
                entity.getTotalRecords(),
                entity.getPassedRecords(),
                entity.getFailedRecords(),
                entity.getCompletedFileKey(),
                entity.getOriginalObjectKey(),
                entity.getStorageProvider(),
                entity.isMakerCheckerEnabled(),
                entity.getOriginalFileChecksumSha256(),
                entity.getStatus(),
                entity.getQueueJobRef(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                rawObjectKey,
                validatedObjectKey);
    }
}
