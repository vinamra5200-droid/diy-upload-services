package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.AuthSidebarMenuEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Console navigation entries in the system (admin) database — {@code auth.sidebar_menus}. */
@Repository
public interface AuthSidebarMenuRepository extends JpaRepository<AuthSidebarMenuEntity, UUID> {

    List<AuthSidebarMenuEntity> findAllByStatusOrderByOrderIndexAsc(Integer status);

    List<AuthSidebarMenuEntity> findAllByOrderByOrderIndexAsc();

    Optional<AuthSidebarMenuEntity> findByMenuCodeIgnoreCase(String menuCode);

    boolean existsByMenuCodeIgnoreCase(String menuCode);
}
