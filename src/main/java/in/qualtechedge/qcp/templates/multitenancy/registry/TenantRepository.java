package in.qualtechedge.qcp.templates.multitenancy.registry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tenant registry repository. Routes to the system DB because callers run without a tenant
 * context (resolution filter, startup initializer, admin endpoints).
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByShortCodeIgnoreCase(String shortCode);

    boolean existsByShortCodeIgnoreCase(String shortCode);

    boolean existsByShortCodeIgnoreCaseAndStatus(String shortCode, Integer status);

    List<Tenant> findAllByStatus(Integer status);
}
