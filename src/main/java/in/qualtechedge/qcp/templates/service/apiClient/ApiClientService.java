package in.qualtechedge.qcp.templates.service.apiClient;

import in.qualtechedge.qcp.templates.entity.common.ApiClientPrincipal;

/**
 * Verifies API client credentials against the admin or tenant database depending on the current
 * tenant context ({@link in.qualtechedge.qcp.templates.multitenancy.context.HostContext}).
 * <p>
 * System context (no tenant set) → {@code auth.api_clients} in the system DB.
 * Tenant context → {@code api_clients} in the tenant DB.
 */
public interface ApiClientService {

    /**
     * Authenticates the API client by {@code clientId} / {@code clientSecret} and returns the
     * verified principal. Throws {@code BadCredentialsException} on any failure.
     */
    ApiClientPrincipal authenticate(String clientId, String clientSecret);
}
