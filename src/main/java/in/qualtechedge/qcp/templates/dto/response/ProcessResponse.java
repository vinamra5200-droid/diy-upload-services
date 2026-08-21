package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import java.time.OffsetDateTime;

public record ProcessResponse(
        String processId,
        String processName,
        String description,
        ConfigStatus status,
        boolean validationsEnabled,
        String validationsSkipReason,
        boolean configLocked,
        String configLockRef,
        String submittedBy,
        String rejectionReason,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
