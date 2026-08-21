package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends JpaRepository<Template, String>, JpaSpecificationExecutor<Template> {

    boolean existsByTemplateCode(String templateCode);
}
