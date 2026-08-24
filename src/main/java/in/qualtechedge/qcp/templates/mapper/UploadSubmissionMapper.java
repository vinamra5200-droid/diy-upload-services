package in.qualtechedge.qcp.templates.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationIssueResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationSummaryResponse;
import in.qualtechedge.qcp.templates.entity.UploadSubmission;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UploadSubmissionMapper {

    /**
     * {@code templateId} is passed in rather than read off the entity — {@code upload_submissions}
     * has no {@code template_id} column of its own (only {@code template_code}/{@code
     * template_version}), so the caller resolves it from the owning {@code UploadAttempt}.
     */
    public UploadSubmissionResponse toResponse(UploadSubmission entity, String templateId) {
        return new UploadSubmissionResponse(
                entity.getSubmissionId(),
                entity.getUploadAttemptId(),
                entity.getProcessId(),
                templateId,
                entity.getProcessName(),
                entity.getTemplateCode(),
                entity.getTemplateVersion(),
                entity.getMakerUserId(),
                entity.getMakerDisplayName(),
                entity.getPendingObjectKey(),
                entity.getStorageProvider(),
                JsonColumnMapper.read(entity.getSummary(), ValidationSummaryResponse.class),
                readIssues(entity.getIssues()),
                entity.getOriginalFileChecksumSha256(),
                entity.getStatus(),
                entity.getCheckerUserId(),
                entity.getReviewReason(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private List<ValidationIssueResponse> readIssues(String json) {
        List<ValidationIssueResponse> issues = JsonColumnMapper.read(json, new TypeReference<List<ValidationIssueResponse>>() {
        });
        return issues == null ? List.of() : issues;
    }
}
