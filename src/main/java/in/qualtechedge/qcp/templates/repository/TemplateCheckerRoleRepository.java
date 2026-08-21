package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.TemplateCheckerRole;
import in.qualtechedge.qcp.templates.entity.TemplateCheckerRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateCheckerRoleRepository extends JpaRepository<TemplateCheckerRole, TemplateCheckerRoleId> {

    List<TemplateCheckerRole> findByTemplateId(String templateId);

    void deleteByTemplateId(String templateId);
}
