package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.TenantRequest;
import in.qualtechedge.qcp.templates.dto.response.TenantResponse;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.mapper.TenantMapper;
import in.qualtechedge.qcp.templates.multitenancy.config.MultiTenancyProperties;
import in.qualtechedge.qcp.templates.multitenancy.credentials.TenantCredentialProvider;
import in.qualtechedge.qcp.templates.multitenancy.provisioning.JdbcUrl;
import in.qualtechedge.qcp.templates.multitenancy.provisioning.TenantProvisioningException;
import in.qualtechedge.qcp.templates.multitenancy.provisioning.TenantProvisioningService;
import in.qualtechedge.qcp.templates.multitenancy.registry.Tenant;
import in.qualtechedge.qcp.templates.multitenancy.registry.TenantRepository;
import in.qualtechedge.qcp.templates.service.TenantAdminService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Tenant administration on the system DB. Onboarding builds the tenant's db_url from the
 * system datasource host/port and the configured db-name format, persists the registry row,
 * then provisions + migrates + pools the isolated database in one call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantAdminServiceImpl implements TenantAdminService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final TenantProvisioningService tenantProvisioningService;
    private final TenantCredentialProvider credentialProvider;
    private final MultiTenancyProperties properties;

    /** System datasource URL — tenant databases are created on the same server. */
    @Value("${spring.datasource.url}")
    private String systemDbUrl;

    @Override
    public List<TenantResponse> getAll() {
        return tenantRepository.findAll().stream()
                .map(tenantMapper::toResponse)
                .toList();
    }

    @Override
    public TenantResponse create(TenantRequest request) {
        String shortCode = request.shortCode().toLowerCase(Locale.ROOT);

        if (tenantRepository.existsByShortCodeIgnoreCase(shortCode)) {
            throw new ConflictException("Tenant with short code '" + shortCode + "' already exists");
        }
        // Fail before touching the registry or the DB server when no credentials are configured
        if (credentialProvider.getCredentials(shortCode).isEmpty()) {
            throw new TenantProvisioningException(
                    "No DB credentials configured for tenant '" + shortCode
                            + "' — add qcp.multitenancy.tenants." + shortCode + ".* before onboarding");
        }

        String databaseName = String.format(properties.dbNameFormat(), shortCode);
        String dbUrl = JdbcUrl.parse(systemDbUrl).withDatabase(databaseName);

        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setDescription(request.description());
        tenant.setShortCode(shortCode);
        tenant.setDbUrl(dbUrl);
        tenant.setStatus(Tenant.STATUS_ACTIVE);
        tenant = tenantRepository.save(tenant);

        tenantProvisioningService.onboard(tenant);
        log.info("Tenant '{}' onboarded: database={} (no restart required)", shortCode, databaseName);

        return tenantMapper.toResponse(tenant);
    }
}
