package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.TenantSidebarMenuEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * A tenant's own navigation, from {@code sidebar_menus} in that tenant's database.
 *
 * <p>Which database that is comes from the tenant on the request, so this reads whichever tenant
 * the host resolved to and never the system one. Read-only by intention: a tenant's menu set is
 * seeded and edited through the tenant's own screens, not written by the console.
 */
@Repository
public interface TenantSidebarMenuRepository extends JpaRepository<TenantSidebarMenuEntity, UUID> {

    List<TenantSidebarMenuEntity> findAllByStatusOrderByOrderIndexAsc(Integer status);
}
