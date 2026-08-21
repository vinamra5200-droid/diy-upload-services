package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.response.UploadFileResponse;
import in.qualtechedge.qcp.templates.entity.UploadFile;
import org.springframework.stereotype.Component;

@Component
public class UploadFileMapper {

    public UploadFileResponse toResponse(UploadFile entity) {
        return new UploadFileResponse(
                entity.getUploadId(),
                entity.getProcessId(),
                entity.getTemplateId(),
                entity.getOriginalFilename(),
                entity.getChecksumSha256(),
                entity.getFileSizeBytes(),
                entity.getContentType(),
                entity.getS3Bucket(),
                entity.getS3Key(),
                entity.getEtag(),
                entity.getJobId(),
                entity.getStatus(),
                entity.getUploadedBy(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
