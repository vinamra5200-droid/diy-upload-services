package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.TenantRequest;
import in.qualtechedge.qcp.templates.dto.response.TenantResponse;
import java.util.List;

/** Tenant administration (superadmin scope — operates exclusively on the system database). */
public interface TenantAdminService {

    /** Lists every tenant in the registry. */
    List<TenantResponse> getAll();

    /**
     * Onboards a new tenant end to end: registry row + isolated database + role + Flyway +
     * datasource pool — live immediately, no restart (QCC Multi-Tenancy §4).
     */
    TenantResponse create(TenantRequest request);
}
