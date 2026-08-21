package in.qualtechedge.qcp.templates.utils;

import in.qualtechedge.qcp.templates.multitenancy.config.MultiTenancyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@code {env}} segment used both by host/tenant resolution
 * ({@link in.qualtechedge.qcp.templates.config.TenantAuthenticationManagerResolver}) and by the
 * S3 object key layout ({@link in.qualtechedge.qcp.templates.service.impl.S3UploadServiceImpl}):
 * {@code qcp.multitenancy.host.environment} when pinned, otherwise the active Spring profile.
 */
@Component
@RequiredArgsConstructor
public class DeploymentEnvironment {

    private final MultiTenancyProperties multiTenancy;
    private final Environment springEnvironment;

    public String current() {
        String configured = multiTenancy.host().environment();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String[] profiles = springEnvironment.getActiveProfiles();
        return profiles.length > 0 ? profiles[0] : "local";
    }
}
