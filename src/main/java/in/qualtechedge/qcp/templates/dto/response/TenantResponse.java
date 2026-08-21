package in.qualtechedge.qcp.templates.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Tenant registry view returned by the admin API. Credentials are never exposed. */
public record TenantResponse(
        UUID id,
        String name,
        String description,
        String shortCode,
        String dbUrl,
        Integer status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
