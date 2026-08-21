package in.qualtechedge.qcp.templates.repository.admin;

import in.qualtechedge.qcp.templates.entity.admin.auth.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {

    @Query("SELECT c FROM ApiClient c LEFT JOIN FETCH c.roleAssignments r LEFT JOIN FETCH r.role "
            + "WHERE c.clientId = :clientId")
    Optional<ApiClient> findByClientIdWithRoles(@Param("clientId") String clientId);
}
