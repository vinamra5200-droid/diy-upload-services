package in.qualtechedge.qcp.templates.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO (record by default per QCP DTO rules).
 */
public record ExampleResponse(
        UUID id,
        String name,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
