package in.qualtechedge.qcp.templates.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tenant-vault")
@Data
public class VaultProperties {

    private boolean enabled;
    private String url;
    private String authentication;
    private TenantAppRole tenantAppRole;
    private String mountPath;
    private String applicationName;

    @Data
    public static class TenantAppRole {
        private String roleId;
        private String secretId;
    }
}
