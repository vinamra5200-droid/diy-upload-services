package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.TemplateFieldRefId;
import in.qualtechedge.qcp.templates.entity.TemplatePkField;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplatePkFieldRepository extends JpaRepository<TemplatePkField, TemplateFieldRefId> {

    List<TemplatePkField> findByTemplateIdOrderBySortOrder(String templateId);

    void deleteByTemplateId(String templateId);
}
