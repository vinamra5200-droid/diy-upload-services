package in.qualtechedge.qcp.templates.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import in.qualtechedge.qcp.templates.dto.response.UploadAttemptResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationIssueResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationSummaryResponse;
import in.qualtechedge.qcp.templates.entity.UploadAttempt;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UploadAttemptMapper {

    public UploadAttemptResponse toResponse(UploadAttempt entity) {
        return new UploadAttemptResponse(
                entity.getUploadAttemptId(),
                entity.getProcessId(),
                entity.getProcessName(),
                entity.getTemplateId(),
                entity.getTemplateCode(),
                entity.getTemplateVersion(),
                entity.getMakerUserId(),
                entity.getOriginalFilename(),
                entity.getUploadFormat(),
                entity.getFileSizeBytes(),
                entity.getOriginalFileChecksumSha256(),
                entity.getRawObjectKey(),
                entity.getValidatedObjectKey(),
                entity.getStatus(),
                JsonColumnMapper.read(entity.getSummary(), ValidationSummaryResponse.class),
                readIssues(entity.getIssues()),
                entity.getDecision(),
                entity.getDecidedAt(),
                entity.isMakerCheckerEnabled(),
                entity.isValidationsEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private List<ValidationIssueResponse> readIssues(String json) {
        List<ValidationIssueResponse> issues = JsonColumnMapper.read(json, new TypeReference<List<ValidationIssueResponse>>() {
        });
        return issues == null ? List.of() : issues;
    }
}
