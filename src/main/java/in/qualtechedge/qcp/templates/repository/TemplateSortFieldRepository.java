package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.TemplateFieldRefId;
import in.qualtechedge.qcp.templates.entity.TemplateSortField;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateSortFieldRepository extends JpaRepository<TemplateSortField, TemplateFieldRefId> {

    List<TemplateSortField> findByTemplateIdOrderBySortOrder(String templateId);

    void deleteByTemplateId(String templateId);
}
