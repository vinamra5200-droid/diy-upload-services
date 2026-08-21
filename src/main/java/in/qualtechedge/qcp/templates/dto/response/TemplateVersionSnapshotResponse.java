package in.qualtechedge.qcp.templates.dto.response;

import java.time.OffsetDateTime;

public record TemplateVersionSnapshotResponse(
        String snapshotId,
        String templateId,
        String version,
        TemplateResponse snapshot,
        String capturedBy,
        OffsetDateTime capturedAt
) {
}
