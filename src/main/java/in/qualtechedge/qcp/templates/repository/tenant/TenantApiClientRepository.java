package in.qualtechedge.qcp.templates.repository.tenant;

import in.qualtechedge.qcp.templates.entity.tenant.auth.TenantApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantApiClientRepository extends JpaRepository<TenantApiClient, UUID> {

    @Query("SELECT c FROM TenantApiClient c LEFT JOIN FETCH c.roleAssignments r LEFT JOIN FETCH r.role "
            + "WHERE c.clientId = :clientId")
    Optional<TenantApiClient> findByClientIdWithRoles(@Param("clientId") String clientId);
}
