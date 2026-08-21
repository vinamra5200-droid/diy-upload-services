package in.qualtechedge.qcp.templates.entity.common;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Common contract for {@code ApiClient} (admin scope) and {@code TenantApiClient} (tenant scope),
 * consumed by {@code ApiClientService} and {@code ApiKeyAuthenticationProvider}.
 */
public interface ApiClientPrincipal {

    UUID getId();

    String getClientId();

    String getClientSecret();

    String getName();

    String getEmailId();

    String getDescription();

    Integer getStatus();

    ZonedDateTime getCreatedAt();

    ZonedDateTime getUpdatedAt();

    /** Returns the set of active role principals assigned to this API client. */
    Set<RolePrincipal> getApiClientUserRoles();
}
