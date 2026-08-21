package in.qualtechedge.qcp.templates.entity.common;

import java.util.UUID;

/** Minimal role contract shared by {@code Role} (admin) and {@code TenantRole} (tenant). */
public interface RolePrincipal {

    UUID getId();

    String getName();

    String getDescription();

    Integer getStatus();
}
