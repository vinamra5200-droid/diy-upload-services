package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record UploadRoleResponse(
        String roleId,
        String roleName,
        String description,
        List<String> processAccess,
        boolean isActive,
        ConfigStatus status,
        String submittedBy,
        String rejectionReason,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
