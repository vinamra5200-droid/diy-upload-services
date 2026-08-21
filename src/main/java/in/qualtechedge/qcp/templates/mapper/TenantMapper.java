package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.response.TenantResponse;
import in.qualtechedge.qcp.templates.multitenancy.registry.Tenant;
import org.springframework.stereotype.Component;

/**
 * Manual DTO ⇆ entity converter (QCP mapper rule: mapping only, no business logic).
 */
@Component
public class TenantMapper {

    public TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getDescription(),
                tenant.getShortCode(),
                tenant.getDbUrl(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt());
    }
}
