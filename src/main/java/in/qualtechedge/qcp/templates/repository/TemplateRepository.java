package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends JpaRepository<Template, String>, JpaSpecificationExecutor<Template> {

    boolean existsByTemplateCode(String templateCode);

    /** §1.2 — the single active template for a process, if any. */
    Optional<Template> findFirstByProcessIdAndStatus(String processId, ConfigStatus status);
}
