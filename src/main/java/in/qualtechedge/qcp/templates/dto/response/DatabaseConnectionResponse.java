package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.DatabaseProvider;
import java.time.OffsetDateTime;
import java.util.List;

public record DatabaseConnectionResponse(
        String connectionId,
        DatabaseProvider provider,
        String connectionLabel,
        String connectionRef,
        List<String> tableNames,
        ConfigStatus status,
        String submittedBy,
        String rejectionReason,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
