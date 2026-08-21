package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.UploadRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadRoleRepository extends JpaRepository<UploadRole, String> {

    boolean existsByRoleNameIgnoreCase(String roleName);

    boolean existsByRoleNameIgnoreCaseAndRoleIdNot(String roleName, String roleId);
}
