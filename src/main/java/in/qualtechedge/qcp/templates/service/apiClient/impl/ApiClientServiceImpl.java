package in.qualtechedge.qcp.templates.service.apiClient.impl;

import in.qualtechedge.qcp.templates.entity.common.ApiClientPrincipal;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.repository.admin.ApiClientRepository;
import in.qualtechedge.qcp.templates.repository.tenant.TenantApiClientRepository;
import in.qualtechedge.qcp.templates.service.apiClient.ApiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiClientServiceImpl implements ApiClientService {

    private final ApiClientRepository adminRepository;
    private final TenantApiClientRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public ApiClientPrincipal authenticate(String clientId, String clientSecret) {
        log.debug("Authenticating API client: {}", clientId);

        String tenant = HostContext.getCurrentTenant();
        boolean isTenantContext = tenant != null && !HostContext.SYSTEM_TENANT.equals(tenant);

        ApiClientPrincipal client = isTenantContext
                ? tenantRepository.findByClientIdWithRoles(clientId)
                        .orElseThrow(() -> {
                            log.debug("Tenant API client not found: {}", clientId);
                            return new BadCredentialsException("Invalid API credentials");
                        })
                : adminRepository.findByClientIdWithRoles(clientId)
                        .orElseThrow(() -> {
                            log.debug("Admin API client not found: {}", clientId);
                            return new BadCredentialsException("Invalid API credentials");
                        });

        if (!Integer.valueOf(1).equals(client.getStatus())) {
            log.debug("API client is inactive: {}", clientId);
            throw new BadCredentialsException("API client is not active");
        }

        if (!passwordEncoder.matches(clientSecret, client.getClientSecret())) {
            log.debug("API client secret mismatch: {}", clientId);
            throw new BadCredentialsException("Invalid API credentials");
        }

        log.debug("API client authenticated: {}", clientId);
        return client;
    }
}
